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

## Git Submodule 初始化

项目通过 Git submodule 固定 `llama.cpp` 源码版本。

submodule 配置：

```text
path: engines/llama/src/main/cpp/llama.cpp
url:  https://github.com/ggml-org/llama.cpp.git
```

首次 clone 项目后，在项目根目录执行：

```powershell
git submodule update --init --recursive
```

如果 clone 时希望一并拉取 submodule：

```powershell
git clone --recursive <repo-url>
```

如果已经普通 clone，也可以后续再执行：

```powershell
git submodule update --init --recursive
```

如果需要重新同步 `.gitmodules` 中的 URL 或路径配置：

```powershell
git submodule sync --recursive
git submodule update --init --recursive
```

当前 `llama.cpp` 固定版本：

```text
describe: b9222-16-g45c4c1c61
commit:   45c4c1c618b74b9911e5cbe5910ad0caba085d2d
remote:   https://github.com/ggml-org/llama.cpp.git
```

验证当前版本：

```powershell
git -C engines/llama/src/main/cpp/llama.cpp describe --tags --always
git -C engines/llama/src/main/cpp/llama.cpp rev-parse HEAD
```

期望输出：

```text
b9222-16-g45c4c1c61
45c4c1c618b74b9911e5cbe5910ad0caba085d2d
```

如果需要更新固定版本，应在 submodule 目录中 checkout 到目标 tag/commit，再回到项目根目录提交 submodule 指针：

```powershell
git -C engines/llama/src/main/cpp/llama.cpp fetch --tags
git -C engines/llama/src/main/cpp/llama.cpp checkout <tag-or-commit>
git add .gitmodules engines/llama/src/main/cpp/llama.cpp
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
- `runtime`：通用运行时配置，供业务层类型化控制 `contextSize`、`batchSize`、`microBatchSize`、`threads`、`gpuLayers`、`seed`
- `options`：保留给引擎特定扩展的字符串配置，并兼容旧调用方式

示例：

```kotlin
EngineConfig(
    modelPath = modelFile.absolutePath,
    modelFormat = model.format,
    runtime = EngineRuntimeConfig(
        contextSize = 1024,
        batchSize = 128,
        microBatchSize = 128,
        threads = 4,
        gpuLayers = 0
    )
)
```

`InferenceRequest.parameters` 是推理期参数入口，常用文本生成参数已经字段化：

- `maxTokens`：本次最多生成 token 数
- `temperature`：采样温度
- `topK`：Top-K 采样
- `topP`：Top-P 采样
- `repetitionPenalty`：重复惩罚
- `seed`：本次采样随机种子；不设置时使用模型加载期 seed 或 llama.cpp 默认值
- `useChatTemplate`：是否使用模型 GGUF metadata 中的 chat template
- `stopSequences`：停止序列
- `extras`：保留给引擎特定扩展；llama 引擎仍兼容旧的 `extras["repetitionPenalty"]`、`extras["seed"]`、`extras["useChatTemplate"]`

示例：

```kotlin
InferenceRequest(
    task = InferenceTask.TEXT_GENERATION,
    inputs = listOf(InferenceInput.Text(prompt)),
    parameters = InferenceParameters(
        maxTokens = 128,
        temperature = 0.3f,
        topP = 0.8f,
        topK = 20,
        repetitionPenalty = 1.05f,
        useChatTemplate = true
    )
)
```

App 侧默认参数集中在：

```text
app/src/main/java/com/zure/localaienginetester/config/InferenceConfigPresets.kt
```

当前包含：

- `llamaRuntime`：Llama/GGUF 模型加载期默认参数
- `textGeneration`：通用文本生成默认参数
- `conciseTextGeneration`：短文本、翻译、摘要等需要更短输出时可复用的默认参数

调整默认值时优先修改 `InferenceConfigPresets`。如果某个页面只需要临时覆盖少数字段，可以使用 `copy(...)`：

```kotlin
val parameters = InferenceConfigPresets.textGeneration.copy(
    maxTokens = 64,
    temperature = 0.2f
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
describe: b9222-16-g45c4c1c61
commit: 45c4c1c618b74b9911e5cbe5910ad0caba085d2d
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
- 通过 `InferenceParameters` 字段控制 `repetitionPenalty`、`seed`、`useChatTemplate` 等生成选项，并兼容旧 `extras` 键
- 输出 `Generation perf` 日志，包含 `promptTokens`、`generatedTokens`、`promptMs`、`firstTokenMs`、`decodeMs`、`totalMs`、`tokPerSec`

Llama 运行时参数读取优先级：

1. `EngineConfig.runtime` 中的类型化字段
2. `EngineConfig.options` 中的同名字符串字段
3. llama 引擎默认值

支持的 `options` 兼容键：

```text
contextSize
batchSize
microBatchSize
threads
gpuLayers
seed
```

当前启用 `arm64-v8a` 和 `x86_64` ABI。普通 UI/TFLite 调试时不建议总是打包 Llama 引擎，因为 llama.cpp native 库会增加 APK 体积和安装/启动成本。

### 翻译测试

App 内的翻译测试页使用 `TEXT_GENERATION` + 流式输出，当前面向 Hy-MT1.5 GGUF 翻译模型做了适配：

- 需自行下载翻译模型：https://huggingface.co/AngelSlim/Hy-MT1.5-1.8B-1.25bit-GGUF , 下载后将模型放到/app/assets/models/llama/目录下 
- 生成参数来自 `InferenceConfigPresets.conciseTextGeneration`，可在统一配置入口调整
- 默认启用 chat template：`useChatTemplate=true`
- 通过 `AppLog` 打印翻译开始、每个流式 chunk、完成输出和异常堆栈
- Logcat 可筛选 `LocalAIEngine-Translation` 和 `LlamaJni`；性能调优时重点查看 `Generation perf`

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
