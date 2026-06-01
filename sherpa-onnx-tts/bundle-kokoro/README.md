# sherpa-onnx-tts Kokoro Bundle

可选资源子模块，内置默认 Kokoro TTS bundle：

```text
src/main/assets/tts/kokoro-multi-lang-v1_1.zip
```

宿主 app 只有在显式依赖本模块时，才会把该 zip 合并进 APK assets。

```kotlin
dependencies {
    implementation(project(":sherpa-onnx-tts"))
    implementation(project(":sherpa-onnx-tts:bundle-kokoro"))
}
```

`SherpaOnnxTtsManager.init()` 默认会扫描 `tts/`，因此依赖本模块后可以直接使用默认初始化：

```kotlin
val manager = SherpaOnnxTtsManager(context)
val bundle = manager.init()
```
