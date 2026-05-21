# LocalAIEngineTester 学习文档

本文面向第一次接触 Android NDK / JNI / 本地 AI 推理引擎开发的学习者，结合当前项目代码说明整体架构、框架逻辑，以及 `engines:llama` 模块中涉及的 NDK 技术要点。

## 1. 项目定位

`LocalAIEngineTester` 是一个 Android 本地 AI 引擎测试工具。它的核心目标不是只绑定某一个模型框架，而是抽象出统一的 AI 推理接口，然后按需接入不同推理后端。

当前项目包含三类引擎模块：

- `:engines:tflite`：基于 Google LiteRT / TFLite，面向 `.tflite` 模型。
- `:engines:llama`：基于 `llama.cpp`，通过 Android NDK + CMake + JNI 编译和调用 native 推理能力，面向 `.gguf` 模型。
- `:engines:onnxruntime`：ONNX Runtime 扩展模块骨架，目前还没有完整 Android 推理实现。

整体结构如下：

```text
LocalAIEngineTester/
├── app/                     # Android App，负责 UI、Hilt 注入、按需打包引擎
├── core/                    # 纯 Kotlin 推理抽象层，不依赖 Android
├── engines/
│   ├── tflite/              # LiteRT/TFLite 引擎实现
│   ├── llama/               # llama.cpp + CMake + JNI 引擎实现
│   └── onnxruntime/         # ONNX Runtime 模块骨架
└── gradle/libs.versions.toml # 依赖版本统一管理
```

## 2. 分层架构

### 2.1 core：统一推理抽象层

`core/src/main/kotlin/com/zure/localaiengine/core/inference/` 是整个 AI 引擎框架的基础。它不依赖 Android SDK，因此可以被多个平台或模块复用。

关键类型：

- `AIEngine`：所有推理引擎的统一接口。
- `EngineConfig`：模型路径、模型格式和引擎加载参数。
- `EngineDescriptor`：引擎元信息，例如 id、名称、支持的模型格式、支持的任务、是否支持流式输出。
- `EngineFactory`：创建具体引擎实例。
- `EngineRegistry`：保存所有已注册引擎工厂，并按 id 创建引擎。
- `AIEngineManager`：管理当前正在使用的引擎，负责切换、推理、关闭。
- `InferenceRequest`：一次推理请求，包含任务类型、输入和采样参数。
- `InferenceResult` / `InferenceChunk`：同步推理结果和流式推理片段。
- `InferenceInput` / `InferenceOutput`：文本、图片、音频、Tensor 等输入输出结构。

可以把 `core` 理解为“推理框架的接口合同”。TFLite、Llama、ONNX 后续都必须按这个合同实现。

### 2.2 app：应用层和引擎装配

`app` 模块负责 Android UI、Hilt 依赖注入，以及控制本次 APK 打包哪些 AI 引擎。

关键文件：

- `app/build.gradle.kts`
- `app/src/main/java/com/zure/localaienginetester/di/AIEngineModule.kt`
- `app/src/main/java/com/zure/localaienginetester/ui/screen/home/HomeViewModel.kt`

`app/build.gradle.kts` 顶部有两个集合：

```kotlin
val supportedAIEngines = setOf("tflite", "llama")
val packagedAIEngines = setOf(
    "tflite",
    "llama",
)
```

`supportedAIEngines` 表示项目认识哪些引擎；`packagedAIEngines` 表示本次 APK 实际打包哪些引擎。Gradle 会根据这个集合添加条件依赖：

```kotlin
if ("tflite" in packagedAIEngines) {
    implementation(project(":engines:tflite"))
}
if ("llama" in packagedAIEngines) {
    implementation(project(":engines:llama"))
}
```

同时，它会生成一个 `BuildConfig.PACKAGED_AI_ENGINES` 字段。App 运行时通过这个字段决定注册哪些引擎。

`AIEngineModule` 的流程是：

1. 读取 `BuildConfig.PACKAGED_AI_ENGINES`。
2. 把 `tflite`、`llama` 这样的字符串映射到对应的 Factory 类名。
3. 使用反射创建 `EngineFactory`。
4. 把 Factory 集合交给 `EngineRegistry`。
5. 创建全局 `AIEngineManager`。

这样做的好处是：未打包进 APK 的引擎不会被注册，也不会出现在首页列表里。

### 2.3 engines：具体推理后端

每个 `engines/<engine>` 模块都实现 `core` 中定义的接口。

当前两个主要实现：

- `TfLiteEngine`：纯 Android/Kotlin 调用 LiteRT 的 `Interpreter`。
- `LlamaEngine`：Kotlin 调用 JNI，再由 C++ 调用 `llama.cpp`。

这也是学习 NDK 时最关键的对比点：

- TFLite 模块依赖已有 Android AAR，开发者主要写 Kotlin。
- Llama 模块需要自己编译 C++ 源码，处理 CMake、ABI、`.so`、JNI 函数签名、native 内存释放等问题。

## 3. 运行时调用链路

从 App 启动到看到首页可用引擎，大致流程如下：

```text
app/build.gradle.kts
  └─ packagedAIEngines 生成 BuildConfig.PACKAGED_AI_ENGINES
      └─ AIEngineModule.provideEngineFactories()
          └─ 反射创建 TfLiteEngineFactory / LlamaEngineFactory
              └─ EngineRegistry 保存 Factory
                  └─ AIEngineManager 暴露 availableEngines
                      └─ HomeViewModel 读取 availableEngines
                          └─ HomeScreen 展示引擎列表
```

执行一次 Llama 文本生成时，链路会变成：

```text
业务层创建 EngineConfig(GGUF 模型路径)
  └─ AIEngineManager.switchEngine("llama", config)
      └─ EngineRegistry.create("llama")
          └─ LlamaEngineFactory.create()
              └─ LlamaEngine.load(config)
                  └─ LlamaLibraryLoader.ensureLoaded()
                      └─ System.loadLibrary("llama_jni")
                  └─ LlamaNativeBridge.loadModel(...)
                      └─ JNI: Java_..._loadModel(...)
                          └─ llama_model_load_from_file(...)
                          └─ llama_init_from_model(...)

业务层创建 InferenceRequest(TEXT_GENERATION, Text(prompt))
  └─ AIEngineManager.infer(request)
      └─ LlamaEngine.infer(request)
          └─ LlamaNativeBridge.generate(...)
              └─ JNI: Java_..._generate(...)
                  └─ tokenize prompt
                  └─ llama_decode prompt
                  └─ sampler sample token
                  └─ llama_decode generated token
                  └─ 返回完整文本
```

流式输出时，Kotlin 侧使用 `callbackFlow`，C++ 每生成一个 token piece 就通过 Java 回调交还给 Kotlin。

## 4. core 框架逻辑详解

### 4.1 AIEngine

`AIEngine` 是最小推理接口：

```kotlin
interface AIEngine {
    val descriptor: EngineDescriptor

    suspend fun load(config: EngineConfig)

    suspend fun infer(request: InferenceRequest): InferenceResult

    fun stream(request: InferenceRequest): Flow<InferenceChunk> = flow {
        throw UnsupportedOperationException(...)
    }

    suspend fun close()
}
```

它规定了一个引擎的生命周期：

1. `load`：加载模型和初始化运行时资源。
2. `infer`：执行同步推理。
3. `stream`：可选，执行流式推理。
4. `close`：释放资源。

### 4.2 EngineRegistry

`EngineRegistry` 负责保存所有引擎 Factory：

```kotlin
private val factoriesById = factories.associateBy { it.descriptor.id }
```

它只做两件事：

- 查询可用引擎。
- 根据 `engineId` 创建具体引擎。

真正的模型加载和推理不在 Registry 中做，这让 Registry 保持轻量。

### 4.3 AIEngineManager

`AIEngineManager` 管理当前活动引擎。它内部有一个 `Mutex`，用于避免引擎切换和关闭时出现并发问题。

切换引擎时的策略是：

1. 创建新引擎。
2. 先加载新模型。
3. 新模型加载成功后，再关闭旧引擎。
4. 把新引擎设置为 `activeEngine`。

这段设计很重要：如果新模型加载失败，旧引擎仍然可用，调用者不会因为一次失败的切换而丢掉原来的引擎。

## 5. TFLite / LiteRT 模块逻辑

`engines:tflite` 当前实现较直接：

- `TfLiteEngineFactory` 声明引擎支持的格式和任务。
- `TfLiteEngine.load` 检查模型格式必须是 `ModelFormat.TFLITE`。
- 使用 `Interpreter(modelFile, options)` 加载模型。
- `infer` 接收 `InferenceInput.Tensor`。
- 通过 `runForMultipleInputsOutputs` 执行推理。
- 根据输出 Tensor 的数据类型和 shape 创建输出数组。

当前 TFLite 模块不负责图片、音频、文本的预处理。例如图片 resize、normalize、转 `FloatArray` 等逻辑应该放在业务层或后续专用 adapter 中。

## 6. Llama 模块整体结构

`engines/llama` 是当前项目 NDK 学习的核心。

```text
engines/llama/
├── build.gradle.kts
└── src/main/
    ├── cpp/
    │   ├── CMakeLists.txt
    │   ├── README.md
    │   ├── llama_jni.cpp
    │   └── llama.cpp/       # Git submodule，引入上游 llama.cpp 源码
    └── java/com/zure/localaiengine/engines/llama/
        ├── LlamaEngine.kt
        ├── LlamaEngineFactory.kt
        ├── LlamaGenerationConfig.kt
        ├── LlamaLibraryLoader.kt
        ├── LlamaNativeBridge.kt
        └── LlamaTokenCallback.kt
```

它分成三层：

- Kotlin 引擎层：适配 `core.AIEngine`。
- JNI 桥接层：`LlamaNativeBridge` 和 `llama_jni.cpp`。
- native 推理层：上游 `llama.cpp`。

## 7. NDK 构建链路

### 7.1 Android Gradle Plugin 如何接入 CMake

`engines/llama/build.gradle.kts` 中的关键配置：

```kotlin
externalNativeBuild {
    cmake {
        path = file("src/main/cpp/CMakeLists.txt")
    }
}
```

这告诉 Android Gradle Plugin：构建 Android library 时，需要调用 CMake，并使用该 CMakeLists 生成 native 库。

`defaultConfig.externalNativeBuild.cmake` 中还有：

```kotlin
cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
arguments += listOf(
    "-DANDROID_STL=c++_shared",
    "-DGGML_NATIVE=OFF",
    "-DGGML_OPENMP=OFF",
    "-DGGML_VULKAN=OFF",
    "-DGGML_BLAS=OFF",
    "-DGGML_LLAMAFILE=OFF"
)
```

含义：

- `-std=c++17`：使用 C++17。
- `-fexceptions`：启用 C++ 异常，当前 JNI 代码会 `catch (const std::exception&)`。
- `-frtti`：启用运行时类型信息。
- `ANDROID_STL=c++_shared`：使用 Android NDK 提供的共享版 C++ 标准库。
- `GGML_* = OFF`：关闭当前 Android 构建不需要或不稳定的加速后端，先保证 CPU 基础路径可用。

### 7.2 ABI 过滤

当前只构建：

```kotlin
ndk {
    abiFilters += listOf("arm64-v8a")
}
```

ABI 可以理解为 native 库的 CPU 架构版本。Android 常见 ABI 包括：

- `arm64-v8a`：64 位 ARM，当前主流真机。
- `armeabi-v7a`：32 位 ARM，老设备。
- `x86_64`：常用于模拟器。

当前项目只产出：

```text
lib/arm64-v8a/libllama_jni.so
```

如果用 x86_64 模拟器安装只包含 `arm64-v8a` 的 APK，native 库会无法加载。学习和调试 Llama 引擎时，建议使用 ARM64 真机或支持 ARM64 的模拟环境。

### 7.3 CMakeLists.txt 做了什么

`engines/llama/src/main/cpp/CMakeLists.txt` 是 native 构建入口。

核心逻辑：

```cmake
set(LLAMA_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

add_subdirectory(${LLAMA_CPP_DIR} llama_cpp_build)

add_library(llama_jni SHARED
    llama_jni.cpp
)

target_include_directories(llama_jni PRIVATE
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/ggml/include
)

target_link_libraries(llama_jni
    llama
    ${log-lib}
)
```

逐句理解：

- `LLAMA_CPP_DIR` 指向 submodule 中的 `llama.cpp` 源码。
- `add_subdirectory` 把上游 `llama.cpp` 的 CMake 项目纳入当前构建。
- `add_library(llama_jni SHARED ...)` 生成 Android 可加载的动态库 `libllama_jni.so`。
- `target_include_directories` 让 `llama_jni.cpp` 能 include `llama.h` 等头文件。
- `target_link_libraries` 把本项目 JNI 库链接到 `llama` 静态/目标库和 Android `log` 库。

最终 Kotlin 里调用：

```kotlin
System.loadLibrary("llama_jni")
```

这里传的是库名 `llama_jni`，实际 APK 里文件名是 `libllama_jni.so`。

## 8. JNI 桥接层

### 8.1 Kotlin 侧 external fun

`LlamaNativeBridge` 声明 native 方法：

```kotlin
external fun loadModel(...): Long
external fun generate(...): String
external fun generateStream(...)
external fun release(handle: Long)
```

`external` 表示这个函数没有 Kotlin 实现，真正实现来自已经加载的 native `.so`。

### 8.2 JNI 函数命名规则

C++ 侧函数名形如：

```cpp
Java_com_zure_localaiengine_engines_llama_LlamaNativeBridge_loadModel
```

它对应 Kotlin/Java 方法：

```text
com.zure.localaiengine.engines.llama.LlamaNativeBridge.loadModel
```

基本命名规则：

```text
Java_包名_类名_方法名
```

包名中的 `.` 会替换为 `_`。

当前项目使用的是“静态注册 JNI”方式，也就是靠函数名匹配。另一种方式是 `RegisterNatives` 动态注册，本项目暂未使用。

### 8.3 JNIEnv

JNI 函数第一个参数通常是：

```cpp
JNIEnv * env
```

它是 native 代码访问 JVM 的入口。当前项目用它做了几件事：

- 把 `jstring` 转成 `std::string`。
- 创建返回给 Kotlin 的 `jstring`。
- 访问 Java/Kotlin 回调对象。
- 抛出 Java 异常。
- 删除局部引用，避免 local reference 累积。

例如字符串转换：

```cpp
const char * chars = env->GetStringUTFChars(value, nullptr);
std::string result(chars == nullptr ? "" : chars);
env->ReleaseStringUTFChars(value, chars);
```

注意：`GetStringUTFChars` 获取到的指针必须释放，否则可能造成内存或 VM 资源泄漏。

### 8.4 native handle

Kotlin 侧保存：

```kotlin
private var handle: Long = 0L
```

C++ 侧实际创建：

```cpp
struct LlamaHandle {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    uint32_t seed = LLAMA_DEFAULT_SEED;
};
```

`loadModel` 返回：

```cpp
return reinterpret_cast<jlong>(handle.release());
```

这意味着 Kotlin 侧的 `Long` 本质上是一个 native 指针地址。之后 `generate`、`generateStream`、`release` 都把这个 `Long` 转回：

```cpp
auto * handle = reinterpret_cast<LlamaHandle *>(native_handle);
```

这是 JNI 开发中常见模式，但也很容易出错：

- `handle = 0L` 表示未加载。
- 不能重复释放同一个 handle。
- `release` 后必须把 Kotlin 侧 handle 置为 `0L`。
- 不能在 native 对象释放后继续调用 `generate`。

当前 `LlamaEngine.close()` 已经处理了释放后置零：

```kotlin
if (handle != 0L) {
    nativeBridge.release(handle)
    handle = 0L
}
```

## 9. llama.cpp 推理逻辑

### 9.1 加载模型

JNI 的 `loadModel` 做了以下事情：

1. `llama_backend_init()` 初始化 llama.cpp 后端。
2. 创建 `llama_model_params`。
3. 设置 `n_gpu_layers`。
4. 创建 `llama_context_params`。
5. 设置 `n_ctx`、`n_threads`、`n_threads_batch`。
6. 调用 `llama_model_load_from_file` 加载 GGUF 模型。
7. 调用 `llama_init_from_model` 创建上下文。
8. 把 `LlamaHandle*` 作为 `jlong` 返回给 Kotlin。

其中：

- `model` 表示模型权重。
- `context` 表示一次推理上下文，包含 KV cache、线程配置等运行状态。
- `n_ctx` 是上下文窗口大小。
- `n_threads` 影响 CPU 推理线程数。

### 9.2 prompt tokenization

生成前会先调用：

```cpp
auto prompt_tokens = tokenize(handle->model, prompt, true);
```

`tokenize` 内部先用一次 `llama_tokenize` 计算 token 数，再分配 vector，最后再真正写入 token。

这是一种常见 C API 使用方式：先查询需要多大缓冲区，再分配足够空间。

### 9.3 decode prompt

```cpp
llama_batch batch = llama_batch_get_one(prompt_tokens.data(), prompt_tokens.size());
llama_decode(handle->context, batch);
```

可以把 `llama_decode` 理解为“把 token 输入模型，让模型更新内部状态”。先 decode prompt，模型才能基于 prompt 预测下一个 token。

### 9.4 sampler

当前采样链：

```cpp
llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
llama_sampler_chain_add(sampler, llama_sampler_init_dist(handle->seed));
```

含义：

- `top_k`：只在概率最高的 K 个候选 token 中采样。
- `top_p`：只在累计概率达到 P 的候选集合中采样。
- `temperature`：控制随机性，越低越稳定，越高越发散。
- `seed`：控制随机采样的可复现性。

### 9.5 token 生成循环

生成循环每次做：

1. 从当前上下文采样一个 token。
2. 判断是否为 EOG / EOS。
3. 把 token 转为文本片段。
4. 追加到输出。
5. 如果是流式模式，回调 Kotlin。
6. 检查 stop sequence。
7. 把刚生成的 token 再 `llama_decode`，更新上下文。

这一步最能体现自回归语言模型的工作方式：模型每次只预测下一个 token，生成的 token 又会成为下一步输入。

## 10. 流式输出如何跨过 JNI

Kotlin 侧：

```kotlin
callbackFlow {
    nativeBridge.generateStream(..., callback = LlamaTokenCallback { token ->
        trySend(InferenceChunk(output = InferenceOutput.Text(token)))
        true
    })
}
```

C++ 侧：

```cpp
jclass callback_class = env->GetObjectClass(callback);
on_token = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)Z");
...
jstring java_piece = env->NewStringUTF(piece.c_str());
const jboolean should_continue = env->CallBooleanMethod(callback, on_token, java_piece);
env->DeleteLocalRef(java_piece);
```

这里有几个学习重点：

- Kotlin 的 `fun interface LlamaTokenCallback` 编译后对 JNI 来说就是一个 Java 对象。
- C++ 通过 `GetMethodID` 找到 `onToken(String): Boolean`。
- 每生成一个 token piece，就创建 `jstring` 并调用 Java 方法。
- Kotlin 回调返回 `true` 表示继续，返回 `false` 表示中断生成。
- `DeleteLocalRef` 用于释放本轮创建的局部引用。

当前实现中，`generateStream` 是同步阻塞式 native 调用；`callbackFlow` 负责把回调转成 Kotlin Flow，但 native 生成过程本身仍在调用线程里运行。后续如果要提升 UI 体验，需要确保调用发生在合适的后台协程调度器上。

## 11. native 异常和 Java 异常

C++ 侧大部分 JNI 函数使用：

```cpp
try {
    ...
} catch (const std::exception & e) {
    ALOGE("%s", e.what());
    throw_java(env, e.what());
}
```

`throw_java` 内部：

```cpp
jclass clazz = env->FindClass("java/lang/IllegalStateException");
env->ThrowNew(clazz, message);
```

这会在 Java/Kotlin 侧表现为一个 `IllegalStateException`。

需要注意：

- C++ 异常不能直接穿过 JNI 边界抛到 Kotlin。
- 必须 catch 后转换为 Java 异常。
- `ThrowNew` 之后 native 函数通常应尽快返回。

## 12. native 资源释放

`release` 释放顺序：

```cpp
if (handle->context != nullptr) {
    llama_free(handle->context);
}
if (handle->model != nullptr) {
    llama_model_free(handle->model);
}
delete handle;
llama_backend_free();
```

对应 Kotlin 的 `LlamaEngine.close()`。

学习 NDK 时要特别注意：Kotlin/Java 有 GC，但 native 分配的资源不会自动被 GC 管理。只要 C++ 里 `new`、打开文件、创建 native 上下文，就必须设计明确释放路径。

## 13. GGUF、llama.cpp、NDK 的关系

在当前项目中：

- `.gguf` 是模型文件格式。
- `llama.cpp` 是读取 GGUF 并执行推理的 C/C++ 引擎。
- Android NDK 负责把 C/C++ 代码编译成 Android 能加载的 `.so`。
- JNI 负责让 Kotlin 调用 C++。
- `LlamaEngine` 负责把这些能力包装成统一的 `AIEngine`。

可以按下面的层次记忆：

```text
Kotlin App
  └─ AIEngineManager / LlamaEngine
      └─ LlamaNativeBridge external fun
          └─ libllama_jni.so
              └─ llama_jni.cpp
                  └─ llama.cpp
                      └─ GGUF model
```

## 14. 当前项目中的 NDK 技术要点清单

### 14.1 externalNativeBuild

Android Gradle Plugin 通过 `externalNativeBuild.cmake.path` 找到 CMake 构建脚本，并把 native 构建结果打包进 AAR/APK。

### 14.2 CMake

CMake 负责：

- 引入 `llama.cpp` 子项目。
- 编译本项目的 `llama_jni.cpp`。
- 指定 include 目录。
- 链接 `llama` 和 Android `log`。
- 生成 `libllama_jni.so`。

### 14.3 ABI

`abiFilters` 控制构建哪些 CPU 架构的 `.so`。当前只有 `arm64-v8a`。

### 14.4 Android log

C++ 中 include：

```cpp
#include <android/log.h>
```

并使用：

```cpp
__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, ...)
```

这样 native 错误可以出现在 logcat 中。

### 14.5 JNI 字符串转换

`jstring` 和 `std::string` 不能直接互用，需要通过 `GetStringUTFChars` / `ReleaseStringUTFChars` 转换。

### 14.6 JNI 数组转换

`stopSequences: Array<String>` 到 C++ 是 `jobjectArray`。当前代码逐个 `GetObjectArrayElement`，转为 `std::vector<std::string>`，然后 `DeleteLocalRef`。

### 14.7 native 指针传回 Kotlin

`LlamaHandle*` 被转换为 `jlong`，Kotlin 保存为 `Long`。这是常见但需要谨慎管理生命周期的 JNI 技巧。

### 14.8 Java 回调

C++ 使用 `GetMethodID` 和 `CallBooleanMethod` 调用 Kotlin 的 `LlamaTokenCallback.onToken`，实现 token 流式回传。

### 14.9 native 库加载

`System.loadLibrary("llama_jni")` 会从 APK 的 `lib/<abi>/` 中查找 `libllama_jni.so`。

如果库不存在、ABI 不匹配或依赖库缺失，会抛出 `UnsatisfiedLinkError`。

## 15. 常见问题排查

### 15.1 `llama.cpp submodule is missing`

CMake 中有检查：

```cmake
if (NOT EXISTS ${LLAMA_CPP_DIR}/CMakeLists.txt)
    message(FATAL_ERROR ...)
endif()
```

如果报这个错，说明 `engines/llama/src/main/cpp/llama.cpp` 没有初始化。执行：

```powershell
git submodule update --init --recursive
```

### 15.2 `UnsatisfiedLinkError: no llama_jni in java.library.path`

可能原因：

- `packagedAIEngines` 没包含 `"llama"`。
- native 构建失败，没有生成 `libllama_jni.so`。
- 设备 ABI 与 APK 中的 `.so` ABI 不匹配。
- APK 残留旧构建结果，需要 clean。

可检查 APK 内 native 库：

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apk = "app/build/outputs/apk/debug/app-debug.apk"
[System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $apk)).Entries |
    Where-Object { $_.FullName -like "lib/*/*.so" } |
    Sort-Object FullName |
    Select-Object FullName, Length
```

### 15.3 GGUF 加载失败

`LlamaEngine.load` 会先检查：

- `modelFormat == ModelFormat.GGUF`
- 模型路径存在且是文件
- native runtime 可用

如果 C++ 的 `llama_model_load_from_file` 返回空指针，通常要检查：

- 模型文件路径是否是 App 可访问的真实路径。
- 模型文件是否损坏。
- 模型是否确实是 GGUF。
- 设备内存是否足够。

### 15.4 生成很慢

当前 Android Llama 构建关闭了 Vulkan、BLAS、OpenMP 等后端，主要走 CPU 基础路径。影响速度的因素包括：

- 模型大小和量化等级。
- `threads` 参数。
- `contextSize`。
- 设备 CPU 性能。
- prompt 长度和 `maxTokens`。

当前默认线程数：

```kotlin
Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
```

也就是最多 4 线程，避免移动设备过度占用资源。

## 16. 推荐学习顺序

如果你是第一次做 NDK，可以按这个顺序读代码：

1. `core/.../AIEngine.kt`：理解统一接口。
2. `core/.../AIEngineManager.kt`：理解引擎生命周期。
3. `app/build.gradle.kts`：理解按需打包引擎。
4. `app/.../AIEngineModule.kt`：理解运行时如何注册引擎。
5. `engines/llama/build.gradle.kts`：理解 Android Gradle 如何接入 CMake。
6. `engines/llama/src/main/cpp/CMakeLists.txt`：理解 native 库如何生成。
7. `engines/llama/.../LlamaLibraryLoader.kt`：理解 `.so` 如何加载。
8. `engines/llama/.../LlamaNativeBridge.kt`：理解 Kotlin native 方法声明。
9. `engines/llama/src/main/cpp/llama_jni.cpp`：理解 JNI 和 llama.cpp 调用。
10. `engines/llama/.../LlamaEngine.kt`：回到 Kotlin，看 native 能力如何包装成 `AIEngine`。

建议每读完一层，都回答一个问题：

- 这一层接收什么输入？
- 这一层输出什么结果？
- 这一层持有哪些资源？
- 资源什么时候创建，什么时候释放？
- 出错时异常如何传递到上一层？

## 17. 常用验证命令

构建 App：

```powershell
.\gradlew.bat :app:assembleDebug
```

只验证 Llama native 构建：

```powershell
.\gradlew.bat :engines:llama:externalNativeBuildDebug
```

运行测试：

```powershell
.\gradlew.bat test
```

检查 submodule 版本：

```powershell
git -C engines/llama/src/main/cpp/llama.cpp describe --tags --always
git -C engines/llama/src/main/cpp/llama.cpp rev-parse --short HEAD
```

当前 README 记录的期望版本：

```text
tag: b9204
commit: 726704a16
```

## 18. 后续扩展方向

当你理解当前 Llama NDK 链路后，可以继续尝试这些扩展：

- 增加 `x86_64` ABI，方便模拟器调试。
- 为 `generateStream` 增加更明确的后台调度策略，避免阻塞 UI 线程。
- 增加取消推理的 native 侧控制能力，而不只是 Kotlin 回调返回 false。
- 把 stop sequence 命中后的输出裁剪策略做得更精细。
- 增加模型元信息读取，例如上下文长度、词表信息、量化类型。
- 增加 benchmark 页面，对比不同线程数、上下文长度、模型大小的速度。
- 在 ONNX Runtime 模块中复用同一套 `AIEngine` 抽象，实现第三种后端。

## 19. 一句话总结

当前项目的核心思想是：用 `core` 定义稳定的推理抽象，用 `app` 决定打包和注册哪些引擎，用每个 `engines/*` 模块适配具体推理后端；其中 `engines:llama` 展示了 Android NDK 开发的完整链路：Gradle 接入 CMake，CMake 编译 `llama.cpp` 和 JNI 层，Kotlin 通过 `System.loadLibrary` 与 `external fun` 调用 native `.so`，最后把 native 推理能力包装回统一的 `AIEngine` 接口。
