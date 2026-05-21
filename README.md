# LocalAIEngineTester

Android 本地 AI 引擎测试工具。

## 当前项目状态

项目已拆分为多 Gradle Module，用于验证和复用本地 AI 推理引擎框架。

```text
LocalAIEngineTester/
├── core/                    # 纯 Kotlin 模块，无 Android 依赖
│   └── engine/inference/    # 统一 AI 推理接口、配置、注册表、Manager
├── engines/
│   ├── tflite/              # LiteRT/TFLite 引擎实现
│   ├── llama/               # llama.cpp Android CMake/JNI 引擎实现
│   └── onnxruntime/         # ONNX Runtime 扩展模块骨架
└── app/                     # Android App，依赖 core 和被打包的引擎模块
```

### core 模块

`core` 提供统一推理抽象：

- `AIEngine`
- `EngineConfig`
- `EngineRuntimeConfig`
- `EngineDescriptor`
- `EngineFactory`
- `EngineRegistry`
- `AIEngineManager`
- `InferenceRequest`
- `InferenceResult`
- `InferenceInput`
- `InferenceOutput`
- `InferenceTask`
- `ModelFormat`

接口设计支持：

- 机器视觉模型：分类、检测、分割、OCR、Tensor 输入输出
- 大语言模型：文本生成、流式输出
- 多模态模型：通过多输入结构预留扩展能力

运行时由 `AIEngineManager` 管理当前引擎，支持按已注册引擎进行热切换。可切换范围由当前 APK 实际打包的引擎决定。

`EngineConfig` 是模型加载入口，包含：

- `modelPath`：模型文件路径
- `modelFormat`：模型格式，例如 `TFLITE`、`GGUF`
- `runtime`：通用运行时配置，供业务层类型化控制 `contextSize`、`threads`、`gpuLayers`、`seed`
- `options`：保留给引擎特定扩展的字符串配置，并兼容旧调用方式

示例：

```kotlin
EngineConfig(
    modelPath = modelFile.absolutePath,
    modelFormat = model.format,
    runtime = EngineRuntimeConfig(
        contextSize = 1024,
        threads = 4,
        gpuLayers = 0
    )
)
```

### TFLite/LiteRT 引擎

`:engines:tflite` 当前使用 Google LiteRT：

```text
com.google.ai.edge.litert:litert
```

项目已从旧的 `org.tensorflow:tensorflow-lite` 迁移到 LiteRT，以避免旧 `libtensorflowlite_jni.so` 在 16 KB page size 设备上的兼容问题。

当前实现支持：

- 加载 `.tflite` 模型
- 基于 `InferenceInput.Tensor` 执行推理
- 输出 `InferenceOutput.Tensor`

图片、音频、文本等输入的预处理应在业务层或后续专用适配器中完成。

### Llama 引擎

`:engines:llama` 使用方案 A：源码编译。

- `llama.cpp` 作为 Git submodule 放在：

```text
engines/llama/src/main/cpp/llama.cpp
```

- 当前固定版本：

```text
tag: b9204
commit: 726704a16
```

- Android native 构建通过 CMake 生成：

```text
libllama_jni.so
```

当前实现支持：

- 加载 GGUF 模型
- `TEXT_GENERATION`
- 同步文本生成
- token 流式输出封装为 `Flow<InferenceChunk>`
- 基于 GGUF metadata 的 Jinja chat template 渲染，适配官方 `--jinja` 推理路径
- 流式输出 UTF-8 边界缓冲，避免中文等多字节字符被 token piece 拆分后出现乱码
- 通过 `InferenceParameters.extras` 控制 `repetitionPenalty`、`useChatTemplate` 等生成选项

当前启用 `arm64-v8a` 和 `x86_64` ABI。普通 UI/TFLite 调试时不建议总是打包 Llama 引擎，因为 llama.cpp native 库会增加 APK 体积和安装/启动成本。

### 翻译测试

App 内的翻译测试页使用 `TEXT_GENERATION` + 流式输出，当前面向 Hy-MT1.5 GGUF 翻译模型做了适配：

- 需自行下载翻译模型：https://huggingface.co/AngelSlim/Hy-MT1.5-1.8B-1.25bit-GGUF , 下载后将模型放到/app/assets/models/llama/目录下 
- 生成参数参考官方示例：`temperature=0.7`、`topP=0.6`、`topK=20`、`repetitionPenalty=1.05`
- 默认启用 chat template：`useChatTemplate=true`
- 通过 `AppLog` 打印翻译开始、每个流式 chunk、完成输出和异常堆栈
- Logcat 可筛选 `LocalAIEngine-Translation` 和 `LlamaJni`

调试流式输出问题时，优先看 chunk 日志判断异常来自模型原始输出、JNI 解码边界，还是 UI 追加逻辑。

## 按需打包 AI 引擎

项目通过 `app/build.gradle.kts` 中的 `packagedAIEngines` 配置控制本次 APK 打包哪些引擎。这样可以直接使用 Android Studio Run 到设备或模拟器，不需要额外传 Gradle 命令行参数。

推荐默认配置：

```kotlin
val packagedAIEngines = setOf(
    "tflite",
    "llama",
)
```

预期行为：

- `tflite`：只打包 LiteRT/TFLite 引擎，不包含 `libllama_jni.so`
- `llama`：只打包 llama.cpp 引擎，不包含 LiteRT native 库
- `tflite` + `llama`：同时打包两个引擎，运行时可在两者之间切换

App 启动后通过 `EngineRegistry` 只注册当前 APK 内实际存在的引擎。因此：

- 未打包的引擎不会出现在可用引擎列表中
- 运行时不能切换到未打包的引擎
- 首页展示的引擎列表会跟随打包结果变化

这种方式避免固定 flavor 组合膨胀，也便于后续增加 ONNX Runtime 等新引擎。

### 修改打包引擎的方法

修改 `app/build.gradle.kts` 顶部的 `packagedAIEngines`。

只打包 TFLite/LiteRT：

```kotlin
val packagedAIEngines = setOf(
    "tflite",
)
```

只打包 Llama：

```kotlin
val packagedAIEngines = setOf(
    "llama",
)
```

同时打包 TFLite/LiteRT 和 Llama：

```kotlin
val packagedAIEngines = setOf(
    "tflite",
    "llama",
)
```

修改后可以直接点击 Android Studio 的 Run，或使用命令行构建：

```powershell
.\gradlew.bat :app:assembleDebug
```

支持值：

```text
tflite
llama
```

后续新增引擎时，需要同步更新：

- `settings.gradle.kts` include 新模块
- 对应 `engines/<engine>/build.gradle.kts`
- `app/build.gradle.kts` 中的 `supportedAIEngines`
- `app/build.gradle.kts` 中的条件依赖逻辑
- `AIEngineModule` 中的引擎 Factory 类名映射
- README 中的支持值说明

### 注意事项

- `packagedAIEngines` 至少应包含一个引擎。当前支持 `tflite` 和 `llama`。
- 运行时只能切换到当前 APK 已打包的引擎；未打包的引擎不会注册到 `EngineRegistry`，也不会出现在可用引擎列表中。
- 从一个引擎组合切换到另一个组合后，建议在 Android Studio 中执行 Clean Project / Rebuild Project。如果怀疑 APK 里残留了旧 native 库，可命令行执行：

```powershell
.\gradlew.bat clean :app:assembleDebug
```

- `llama` 会打包 `libllama_jni.so`，APK 会变大，native 构建和安装也会更慢；普通 UI 调试建议使用默认 `tflite` 包。
- `tflite` 当前使用 LiteRT，会打包 `libLiteRt.so` 等 native 库。
- `llama` 当前构建 `arm64-v8a` 和 `x86_64` 的 `libllama_jni.so`。如果需要支持其他 ABI，需要调整 `engines/llama/build.gradle.kts` 中的 `abiFilters` 和 CMake/NDK 配置。
- 可通过检查 APK 内 native 库确认打包结果：

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apk = "app/build/outputs/apk/debug/app-debug.apk"
[System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $apk)).Entries |
    Where-Object { $_.FullName -like "lib/*/*.so" } |
    Sort-Object FullName |
    Select-Object FullName, Length
```

## 常用验证命令

默认 TFLite 包：

```powershell
.\gradlew.bat :app:assembleDebug
```

显式 TFLite 包：

```kotlin
val packagedAIEngines = setOf(
    "tflite",
)
```

Llama 包：

```kotlin
val packagedAIEngines = setOf(
    "llama",
)
```

TFLite + Llama 包：

```kotlin
val packagedAIEngines = setOf(
    "tflite",
    "llama",
)
```

完整测试：

```powershell
.\gradlew.bat test
```

Llama native 构建验证：

```powershell
.\gradlew.bat :engines:llama:externalNativeBuildDebug
```
