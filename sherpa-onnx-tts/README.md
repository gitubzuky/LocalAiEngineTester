# sherpa-onnx-tts

独立的 Android Gradle module，用于封装 sherpa-onnx Offline TTS 链路的通用外围逻辑：

- 发现 assets / external 中的 TTS bundle 或 zip
- 解压 zip bundle 到 app 私有缓存目录
- 复用已解压且来源未变化的 bundle
- 根据 bundle 创建 sherpa 原生 `OfflineTtsConfig`
- 调用 sherpa 原生 `OfflineTts.generate`
- 使用 `AudioTrack` 播放生成音频
- 将 Float PCM 保存为可持久化播放的 WAV 文件

当前内置支持 Kokoro bundle layout，但 module 名称和主 API 不绑定 Kokoro。后续替换其他 sherpa-onnx TTS 模型时，可以新增 `SherpaOnnxTtsBundleLayout` 和 `SherpaOnnxTtsModelConfig` 类型。

## 准备工作
### 通过submodule添加进自己的项目中

适合希望长期跟踪本模块更新的宿主项目。在宿主项目根目录执行：

```bash
git submodule add <sherpa-onnx-tts 仓库地址> sherpa-onnx-tts
git submodule update --init --recursive
```

添加后，宿主项目目录结构示例：

```text
your-project/
  app/
  settings.gradle.kts
  sherpa-onnx-tts/
    build.gradle.kts
    sherpa-onnx-aar/
    bundle-kokoro/
    libs/
```

如果其他成员或 CI 拉取宿主项目，需要初始化 submodule：

```bash
git submodule update --init --recursive
```

后续同步更新本模块时，可以在宿主项目根目录执行：

```bash
cd sherpa-onnx-tts
git fetch
git checkout <目标分支或目标 tag>
git pull
cd ..
git status
git add sherpa-onnx-tts
git commit -m "Update sherpa-onnx-tts submodule"
```

如果只想更新到 submodule 远端默认分支的最新提交，也可以直接在宿主项目根目录执行：

```bash
git submodule update --remote sherpa-onnx-tts
git add sherpa-onnx-tts
git commit -m "Update sherpa-onnx-tts submodule"
```

注意：宿主项目记录的是 submodule 的具体提交指针。其他成员拉取宿主项目更新后，需要执行：

```bash
git submodule update --init --recursive
```

这样本地 `sherpa-onnx-tts/` 才会同步到宿主项目当前记录的版本。

### 或者直接clone模块后，连同根目录复制到自己的项目中

适合不需要跟踪上游提交、只想把当前版本作为项目内源码模块维护的宿主项目。先在任意临时目录 clone：

```bash
git clone <sherpa-onnx-tts 仓库地址>
```

然后将 clone 下来的整个 `sherpa-onnx-tts/` 根目录复制到宿主项目根目录，保留内部目录结构，不要只复制 `src/`：

```text
your-project/
  app/
  settings.gradle.kts
  sherpa-onnx-tts/
    build.gradle.kts
    src/
    sherpa-onnx-aar/
    bundle-kokoro/
    libs/
```

如果宿主项目不希望保留该模块自己的 Git 历史，可以删除复制后 `sherpa-onnx-tts/.git/` 目录，再由宿主项目统一管理这些文件。

### 下载所需核心库和bundle资源

- 下载sherpa-onnx运行时核心库，[点击下载](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.2/sherpa-onnx-1.13.2.aar) , 下载后放到`:sherpa-onnx-tts\libs\`目录下

- 如果希望直接使用默认tts模型kokoro，需先下载相关bundle资源[(点击下载)](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_1.tar.bz2), 下载后转为zip压缩包放到 `:sherpa-onnx-tts\bundle-kokoro\src\main\assets\tts` 目录下

## 依赖接入

宿主项目添加 module 依赖：

```kotlin
// settings.gradle.kts
include(":sherpa-onnx-tts")
include(":sherpa-onnx-tts:sherpa-onnx-aar")
include(":sherpa-onnx-tts:bundle-kokoro") // 可选：内置默认 Kokoro bundle
```

```kotlin
// app/build.gradle.kts or feature module build.gradle.kts
dependencies {
    implementation(project(":sherpa-onnx-tts"))

    // 可选：需要把默认 Kokoro bundle 打进 APK 时再依赖
    implementation(project(":sherpa-onnx-tts:bundle-kokoro"))
}
```

本 module 内部通过 `:sherpa-onnx-tts:sherpa-onnx-aar` 承载本地 sherpa-onnx AAR，并由主 module 以 `api(project(...))` 暴露，因此宿主业务代码可以直接使用：

```kotlin
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
```

如果宿主项目同时依赖 `onnxruntime-android`，可能会和 sherpa-onnx AAR 都携带 `libonnxruntime.so`。宿主 app 可按需配置：

```kotlin
android {
    packaging {
        jniLibs {
            pickFirsts += "lib/**/libonnxruntime.so"
        }
    }
}
```

## Bundle 放置方式

默认扫描 assets 路径：

```text
app/src/main/assets/res/tts/
app/src/main/assets/tts/
```

也可以通过 `SherpaOnnxTtsInitConfig.assetDirs` 自定义。

当前 Kokoro bundle zip 解压后应包含：

```text
model.onnx
voices.bin
tokens.txt
espeak-ng-data/
dict/
lexicon-us-en.txt
lexicon-gb-en.txt
lexicon-zh.txt
```

其中 `model.onnx`、`voices.bin`、`tokens.txt` 是 layout 识别所需文件；创建 Kokoro `OfflineTtsConfig` 时还需要 `espeak-ng-data/`、`dict/` 和至少一个 lexicon 文件。

external 目录可以放：

- 已解压 bundle 目录
- zip bundle 文件

## 可选默认 Kokoro Bundle

默认 Kokoro zip 被放在可选资源子模块中：

```text
:sherpa-onnx-tts:bundle-kokoro
```

该子模块只包含 assets 资源，不包含运行时代码。宿主 app 依赖它后，Android Gradle Plugin 会把资源合并到宿主 APK：

```text
assets/tts/kokoro-multi-lang-v1_1.zip
```

由于 `SherpaOnnxTtsInitConfig.assetDirs` 默认包含 `tts`，依赖该子模块后可直接调用：

```kotlin
val manager = SherpaOnnxTtsManager(context)
val bundle = manager.init()
```

不依赖该子模块时，主 `:sherpa-onnx-tts` 仍可用于外部目录、下载 zip、或宿主项目自己的 assets bundle。

## 推荐调用方式

```kotlin
import com.k2fsa.sherpa.onnx.OfflineTts
import com.zure.localaiengine.sherpa.onnx.tts.SherpaOnnxTtsInitConfig
import com.zure.localaiengine.sherpa.onnx.tts.SherpaOnnxTtsManager
import com.zure.localaiengine.sherpa.onnx.tts.SherpaOnnxTtsModelConfig
import java.io.File

val manager = SherpaOnnxTtsManager(context)

val bundle = manager.init(
    SherpaOnnxTtsInitConfig(
        assetDirs = listOf("res/tts", "tts"),
        externalDirs = listOfNotNull(context.getExternalFilesDir("tts")),
        preferredBundleName = "kokoro"
    )
)

val config = manager.createOfflineTtsConfig(
    bundle = bundle,
    modelConfig = SherpaOnnxTtsModelConfig.Kokoro(
        numThreads = 2,
        debug = false,
        lengthScale = 1.0f
    )
)

val tts = OfflineTts(config = config)
try {
    val audio = manager.generate(
        tts = tts,
        text = "你好，欢迎测试 sherpa-onnx 文本转语音。",
        sid = 0,
        speed = 1.0f
    )

    manager.play(audio)

    val wavFile = manager.save(
        audio = audio,
        outputFile = File(context.filesDir, "tts/output.wav")
    )

    val publicWavUri = manager.saveToExternalDownloads(
        audio = audio,
        relativeDir = "Download/StartupApp/TTS",
        fileName = "output.wav"
    )
} finally {
    tts.release()
    manager.release()
}
```

## 保存 WAV 文件

本 module 提供两种 WAV 保存方式。

### 保存到指定 `File`

`save(audio, outputFile)` 会直接写入调用方传入的文件路径，适合保存到 app 私有目录或调用方已经管理好的文件位置：

```kotlin
val wavFile = manager.save(
    audio = audio,
    outputFile = File(context.filesDir, "tts/output.wav")
)
```

如果保存到 `context.filesDir`，文件位于 app 内部私有目录，普通文件管理器通常不可见。

### 保存到系统 Downloads

`saveToExternalDownloads(audio, relativeDir, fileName)` 使用 `MediaStore.Downloads` 写入系统下载目录，适合需要让用户在文件管理器中直接找到 WAV 文件的场景：

```kotlin
val uri = manager.saveToExternalDownloads(
    audio = audio,
    relativeDir = "Download/StartupApp/TTS",
    fileName = "output.wav"
)
```

上例保存后的可见路径通常是：

```text
/storage/emulated/0/Download/StartupApp/TTS/output.wav
```

`relativeDir` 和 `fileName` 由调用方传入，因此宿主 app 可以按自己的产品目录调整保存位置和文件名。保存前会删除同一 `relativeDir` 下同名文件，因此该方法表现为覆盖保存。

该方法依赖 Android 10 / API 29+ 的 scoped storage 能力。由于 `:sherpa-onnx-tts` module 自身 `minSdk` 为 26，API 内部带有运行时检查；低于 API 29 的设备应继续使用 `save(audio, outputFile)`，或由宿主 app 自行处理旧版外部存储权限和路径。

## 使用 Manager 托管 OfflineTts

如果希望 module 帮忙持有并释放当前 `OfflineTts`：

```kotlin
val manager = SherpaOnnxTtsManager(context)

manager.init()

val config = manager.createOfflineTtsConfig(
    modelConfig = SherpaOnnxTtsModelConfig.Kokoro(numThreads = 2)
)

manager.createOfflineTts(config)

val audio = manager.generate(
    text = "这次由 manager 内部持有 OfflineTts。",
    sid = 0,
    speed = 1.0f
)

manager.play(audio)
manager.save(audio, File(context.filesDir, "tts/managed.wav"))
manager.saveToExternalDownloads(audio, "Download/StartupApp/TTS", "managed.wav")
manager.release()
```

## 替换模型或 bundle

后续需要切换到另一个 bundle 时，再调用一次 `init()` 即可：

```kotlin
val nextBundle = manager.init(
    SherpaOnnxTtsInitConfig(
        explicitBundlePath = File(context.filesDir, "downloaded_tts_bundle.zip"),
        preferredBundleName = null
    )
)

val nextConfig = manager.createOfflineTtsConfig(
    bundle = nextBundle,
    modelConfig = SherpaOnnxTtsModelConfig.Kokoro(lengthScale = 1.0f)
)

val nextTts = manager.createOfflineTts(nextConfig)
```

`init()` 会根据来源签名决定是否复用已解压缓存。assets zip 的缓存目录默认是：

```text
context.filesDir/models_cache/sherpa-onnx-tts/{bundleName}/
```

可以通过 `SherpaOnnxTtsInitConfig.cacheDir` 和 `cacheNamespace` 调整。

## 扩展其他 sherpa TTS 模型

当前 module 的公开抽象刻意没有绑定 Kokoro：

- `SherpaOnnxTtsBundle`
- `SherpaOnnxTtsBundleLayout`
- `SherpaOnnxTtsModelConfig`
- `SherpaOnnxTtsManager`

新增模型时建议：

1. 增加新的 `SherpaOnnxTtsBundleLayout`，识别和解析新模型 bundle。
2. 在 `SherpaOnnxTtsModelConfig` 增加新模型配置类型。
3. 在 `SherpaOnnxTtsManager.createOfflineTtsConfig()` 中组装对应 sherpa `OfflineTtsConfig`。

下面以新增 VITS 模型为例。实际文件名需要按所使用的 sherpa-onnx 模型包调整，核心思路是：先让 layout 能识别 bundle，再让 model config 表达该模型需要的参数，最后在 manager 中把通用 `SherpaOnnxTtsBundle` 转成 sherpa 原生配置。

### 1. 新增 bundle layout

例如新增 `VitsBundleLayout.kt`：

```kotlin
package com.zure.localaiengine.sherpa.onnx.tts.bundle.layout

import com.zure.localaiengine.sherpa.onnx.tts.bundle.SherpaOnnxTtsBundle
import java.io.File

object VitsBundleLayout : SherpaOnnxTtsBundleLayout {
    override val id: String = "vits"

    override fun matches(directory: File): Boolean {
        return directory.isDirectory &&
            File(directory, "model.onnx").isFile &&
            File(directory, "tokens.txt").isFile
    }

    override fun resolve(directory: File): SherpaOnnxTtsBundle {
        require(matches(directory)) {
            "VITS bundle is incomplete: ${directory.absolutePath}. Required files: model.onnx, tokens.txt."
        }
        return SherpaOnnxTtsBundle(
            rootDir = directory,
            layoutId = id,
            modelFile = File(directory, "model.onnx"),
            tokensFile = File(directory, "tokens.txt"),
            dataDir = File(directory, "espeak-ng-data").takeIf { it.isDirectory },
            dictDir = File(directory, "dict").takeIf { it.isDirectory },
            lexiconFiles = listOf(
                "lexicon.txt",
                "lexicon-us-en.txt",
                "lexicon-gb-en.txt",
                "lexicon-zh.txt"
            ).mapNotNull { name -> File(directory, name).takeIf { it.isFile } }
        )
    }
}
```

### 2. 增加模型配置类型

在 `SherpaOnnxTtsModelConfig` 中增加一个配置类型：

```kotlin
sealed interface SherpaOnnxTtsModelConfig {
    val numThreads: Int
    val debug: Boolean

    data class Kokoro(
        override val numThreads: Int = 2,
        override val debug: Boolean = false,
        val lengthScale: Float = 1.0f
    ) : SherpaOnnxTtsModelConfig

    data class Vits(
        override val numThreads: Int = 2,
        override val debug: Boolean = false,
        val noiseScale: Float = 0.667f,
        val noiseScaleW: Float = 0.8f,
        val lengthScale: Float = 1.0f
    ) : SherpaOnnxTtsModelConfig
}
```

### 3. 在 manager 中组装原生配置

给 `SherpaOnnxTtsManager.createOfflineTtsConfig()` 增加分支：

```kotlin
fun createOfflineTtsConfig(
    bundle: SherpaOnnxTtsBundle = requireActiveBundle(),
    modelConfig: SherpaOnnxTtsModelConfig = SherpaOnnxTtsModelConfig.Kokoro()
): OfflineTtsConfig {
    return when (modelConfig) {
        is SherpaOnnxTtsModelConfig.Kokoro -> createKokoroConfig(bundle, modelConfig)
        is SherpaOnnxTtsModelConfig.Vits -> createVitsConfig(bundle, modelConfig)
    }
}
```

再新增 `createVitsConfig()`：

```kotlin
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig

private fun createVitsConfig(
    bundle: SherpaOnnxTtsBundle,
    modelConfig: SherpaOnnxTtsModelConfig.Vits
): OfflineTtsConfig {
    bundle.validateRequired("model.onnx", "tokens.txt")

    return OfflineTtsConfig(
        model = OfflineTtsModelConfig(
            vits = OfflineTtsVitsModelConfig(
                model = bundle.modelFile.absolutePath,
                lexicon = bundle.lexiconFiles.joinToString(",") { it.absolutePath },
                tokens = requireNotNull(bundle.tokensFile).absolutePath,
                dataDir = bundle.dataDir?.absolutePath.orEmpty(),
                dictDir = bundle.dictDir?.absolutePath.orEmpty(),
                noiseScale = modelConfig.noiseScale,
                noiseScaleW = modelConfig.noiseScaleW,
                lengthScale = modelConfig.lengthScale
            ),
            numThreads = modelConfig.numThreads,
            debug = modelConfig.debug
        )
    )
}
```

### 4. 初始化时启用新 layout

调用 `init()` 时把新 layout 传入：

```kotlin
val bundle = manager.init(
    SherpaOnnxTtsInitConfig(
        assetDirs = listOf("res/tts", "tts"),
        preferredBundleName = "vits",
        layouts = listOf(VitsBundleLayout, KokoroBundleLayout)
    )
)

val config = manager.createOfflineTtsConfig(
    bundle = bundle,
    modelConfig = SherpaOnnxTtsModelConfig.Vits(
        numThreads = 2,
        lengthScale = 1.0f
    )
)

val tts = manager.createOfflineTts(config)
```

如果希望新模型成为默认可识别 layout，也可以把 `VitsBundleLayout` 加到 `SherpaOnnxTtsInitConfig.layouts` 的默认列表中：

```kotlin
val layouts: List<SherpaOnnxTtsBundleLayout> = listOf(
    KokoroBundleLayout,
    VitsBundleLayout
)
```

## 移除此模块

如果宿主项目后续不再使用 `sherpa-onnx-tts`，需要同时移除 Gradle 配置、源码目录或 submodule 记录，以及业务代码中的调用。

先从宿主项目的 `settings.gradle.kts` 中删除：

```kotlin
include(":sherpa-onnx-tts")
include(":sherpa-onnx-tts:sherpa-onnx-aar")
include(":sherpa-onnx-tts:bundle-kokoro")
```

再从宿主 app 或 feature module 的 `build.gradle.kts` 中删除：

```kotlin
dependencies {
    implementation(project(":sherpa-onnx-tts"))
    implementation(project(":sherpa-onnx-tts:bundle-kokoro"))
}
```

如果当初是通过 submodule 接入，在宿主项目根目录执行：

```bash
git submodule deinit -f sherpa-onnx-tts
git rm -f sherpa-onnx-tts
git commit -m "Remove sherpa-onnx-tts submodule"
```

如果需要彻底清理本地残留缓存，可以再删除宿主项目 `.git/modules/sherpa-onnx-tts` 目录。该目录属于本地 Git 元数据，删除前确认没有未提交的 submodule 内部改动。

如果当初是直接 clone 后复制目录接入，删除宿主项目中的 `sherpa-onnx-tts/` 目录即可：

```bash
git rm -r sherpa-onnx-tts
git commit -m "Remove sherpa-onnx-tts module"
```

最后清理宿主业务代码中对以下 API 或包名的引用：

```kotlin
import com.zure.localaiengine.sherpa.onnx.tts.SherpaOnnxTtsManager
import com.zure.localaiengine.sherpa.onnx.tts.SherpaOnnxTtsInitConfig
import com.zure.localaiengine.sherpa.onnx.tts.SherpaOnnxTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTts
```

