# :camera:analysis

`:camera:analysis` 是面向本地 AI 视觉输入的 CameraX 分析模块。它只处理“相机帧如何变成模型输入”，不承担拍照、录像、手电筒、手动对焦等通用相机功能；这些能力后续应拆到独立 `camera/*` 子模块中按需引入。

## 设计目标

- 在运行功能前统一校验相机权限、摄像头可用性和分析配置。
- 使用 CameraX `Preview + ImageAnalysis` 获取最新帧。
- 将 `ImageProxy` 转换为可复用的预处理 Pipeline 输入。
- 通过 `VisionInputProfile` 描述不同模型需要的输入格式。
- 通过可组合 `FrameProcessorStep` 实现方向修正、RGB 转换、裁剪、缩放、归一化和 Tensor 打包。
- 输出 `InferenceInput.Tensor`，直接适配当前 `:engines:tflite` 的输入要求。
- 预留 CMake/NDK fused backend，用于后续将重 CPU 像素处理迁移到 native。

## 模块边界

模块负责：

- CameraX 分析流绑定。
- CAMERA 权限运行前校验。
- 摄像头可用性校验。
- 分析帧节流。
- 旧帧丢弃，避免模型推理或预处理积压。
- 图像预处理 Pipeline。
- 输出 `CameraAnalysisInput`，包含 Tensor、原始帧信息和坐标映射变换。

模块不负责：

- CAMERA 权限申请弹窗。
- 权限拒绝解释 UI。
- 拍照保存。
- 视频录制。
- 手电筒/对焦/曝光等完整相机控制。
- 模型加载、推理调度和后处理渲染。

调用方应在 UI/app 层申请权限；本模块在 `bind()` 前再次校验权限，未授权时进入 `CameraAnalysisState.Error(CameraPermissionMissing)`，不会启动 CameraX。

## 核心流程

```text
app 申请 CAMERA 权限
-> CameraXAnalysisController.bind(...)
-> 权限/配置/摄像头可用性校验
-> CameraX Preview + ImageAnalysis
-> ImageProxyFrameReader 复制 planes 并立即释放 ImageProxy
-> FrameProcessorPipeline
   -> YuvToRgbStep
   -> OrientationStep
   -> CropStep
   -> ResizeStep
   -> NormalizeStep
   -> TensorPackStep
-> CameraAnalysisInput
-> AIEngine.infer(InferenceRequest(inputs = input.tensors))
```

## 公开入口

```kotlin
val controller = CameraXAnalysisController(context)

controller.bind(
    lifecycleOwner = lifecycleOwner,
    previewView = previewView,
    profile = RtmposeBody2dProfile.create(),
    config = CameraAnalysisConfig(
        lensFacing = AnalysisLensFacing.Back,
        targetWidth = 640,
        targetHeight = 480,
        maxAnalysisFps = 5,
        backend = PreprocessBackend.Auto
    )
)
```

收集输出：

```kotlin
controller.outputs.collectLatest { input ->
    val result = engine.infer(
        InferenceRequest(
            task = InferenceTask.TENSOR,
            inputs = input.tensors
        )
    )
}
```

## Profile 抽象

不同模型通过 `VisionInputProfile` 声明输入要求：

```kotlin
interface VisionInputProfile {
    val id: String
    val inputName: String?
    val inputShape: IntArray
    val tensorLayout: TensorLayout
    val tensorDataType: TensorDataType
    val pixelOrder: PixelOrder
    val cropPolicy: CropPolicy
    val resizePolicy: ResizePolicy
    val normalization: NormalizationSpec

    fun createPipeline(config: CameraAnalysisConfig): List<FrameProcessorStep>
}
```

内置 `GenericVisionInputProfile` 可覆盖常见视觉模型：

- NHWC / NCHW。
- Float32 / UInt8。
- RGB / BGR。
- 无裁剪、中心裁剪、按目标比例中心裁剪、letterbox。
- 不归一化、0-1、-1 到 1、mean/std。

新增模型时优先新增 Profile，而不是修改 CameraX 控制器。

## RTMPose-Body2d 示例

当前内置 `RtmposeBody2dProfile` 用作第一阶段示例：

```text
shape: [1, 256, 192, 3]
layout: NHWC
dataType: Float32
pixelOrder: RGB
crop: CenterAspectFit(192, 256)
resize: Bilinear(192, 256)
normalization: ZeroToOne
```

这能匹配当前 `:engines:tflite` 只接受 `InferenceInput.Tensor` 的设计。正式接入前仍建议用 TFLite `Interpreter.getInputTensor(0)` 读取真实 shape/name/dataType，并将 `inputName` 传入 `RtmposeBody2dProfile.create(inputName)`。

## RTMDet 人体检测前置

姿态相机测试页使用 `RTMDet person detector -> RTMPose` 两阶段流程。RTMDet 检测模型通过 app 层 `LocalModelDiscovery` 统一发现，可放在：

```text
app/src/main/assets/models/tflite/rtmdet_*person*.tflite
```

也可以放在运行时外部 TFLite 模型目录。文件名需要同时包含 `rtmdet` 和 `person`，用于和普通 TFLite 模型区分。发现到多个候选时，assets 内置模型优先，外部目录作为兜底。

检测阶段使用 `RtmDetPersonProfile` 生成 640x640 letterbox 输入，并将 letterbox 的 scale/pad 记录到 `FrameTransform`；RTMDet 输出的人体 bbox 会映射回相机帧坐标，再扩展为 RTMPose 需要的 192:256 ROI 重新预处理同一帧。

## Pipeline 与可扩展步骤

每个预处理步骤实现：

```kotlin
interface FrameProcessorStep {
    suspend fun process(buffer: MutableFrameBuffer, context: FrameProcessContext)
}
```

当前 Kotlin fallback 步骤：

- `YuvToRgbStep`
- `OrientationStep`
- `CropStep`
- `ResizeStep`
- `NormalizeStep`
- `TensorPackStep`

如果一个模型需要特殊前处理，可以新增 Step，然后在 Profile 中组合：

```kotlin
override fun createPipeline(config: CameraAnalysisConfig): List<FrameProcessorStep> {
    return listOf(
        YuvToRgbStep(),
        OrientationStep(),
        CustomRoiStep(),
        ResizeStep(resizePolicy),
        NormalizeStep(normalization),
        TensorPackStep()
    )
}
```

## Native backend

模块已包含 CMake/NDK 工程：

```text
src/main/cpp/CMakeLists.txt
src/main/cpp/native_preprocessor.cpp
```

当前 native backend 已实现 `YUV_420_888 -> Float32 Tensor` fused kernel。`NativeFusedTensorStep` 会在配置受支持且 native library 加载成功时直接走 C++，否则自动回退 Kotlin Pipeline。

当前 native fused kernel 覆盖：

```text
YUV_420_888 planes
-> rotation / optional front-camera mirror
-> center crop / full frame crop
-> resize
-> normalize
-> NHWC/NCHW pack
-> direct ByteBuffer Float32 tensor
```

支持的 Profile 条件：

- `TensorDataType.Float32`
- `ResizePolicy.Bilinear`
- `CropPolicy.None`、`CropPolicy.CenterCrop`、`CropPolicy.CenterAspectFit`
- `TensorLayout.NHWC` 或 `TensorLayout.NCHW`
- `PixelOrder.RGB` 或 `PixelOrder.BGR`
- `NormalizationSpec.None`、`ZeroToOne`、`MinusOneToOne`、`MeanStd`

暂未覆盖的配置会自动回退 Kotlin Pipeline，例如 `UInt8` 和 `Letterbox`。

迁移原则：

- Kotlin 层保留 CameraX、权限、Flow、Profile 和状态管理。
- Native 层只处理像素搬运和数值计算。
- 避免“一步一个 JNI 调用”，优先实现 fused kernel。
- 尽量直接读取 ImageProxy plane buffer，直接写入 direct `ByteBuffer`。
- 保留 Kotlin fallback，便于兼容不同设备的 YUV stride/pixelStride 差异。

## 权限与状态

所有运行入口都必须先校验权限和环境。当前 `bind()` 校验：

- `Manifest.permission.CAMERA`
- `targetWidth/targetHeight/maxAnalysisFps` 是否合法
- 指定前/后摄是否可用

状态通过 `CameraAnalysisState` 暴露：

```text
Idle
Opening
Running
Error
```

错误通过 `CameraAnalysisError` 结构化返回：

```text
CameraPermissionMissing
CameraUnavailable
LifecycleNotReady
InvalidAnalysisConfig
BindFailed
FrameProcessingFailed
NativeBackendUnavailable
```

## 性能注意

- 默认使用 `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`。
- `maxAnalysisFps` 控制预处理频率。
- 当前实现会复制 `ImageProxy` planes，然后立即 `close()`，避免阻塞 CameraX。
- 若上一帧仍在处理，新帧会被丢弃，避免积压过期画面。
- `PreprocessBackend.Auto` 会优先使用 native fused kernel；不支持时回退 Kotlin。
- Kotlin fallback 中 `YuvToRgbStep` 经过 JPEG 中转，适合兼容兜底；实时性能场景应优先使用 native fused kernel。

## 未来演进

- 增加 direct `ByteBuffer` 复用池。
- 增加 TFLite tensor inspector，根据模型真实 input tensor 自动生成 Profile。
- 继续完善 ROI 输入，覆盖更多检测模型输出格式和 native fused ROI 路径。
- 增加坐标后处理工具，将关键点从模型输入坐标映射回预览坐标。
