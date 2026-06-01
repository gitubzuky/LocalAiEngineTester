# TTS Module

`:core:tts` defines reusable Text-to-Speech contracts. It does not depend on a
specific runtime such as ONNX Runtime or sherpa-onnx. App or feature modules are
responsible for binding these contracts to a concrete model pipeline.

## B Chain: sherpa-onnx + Kokoro

The current B chain is the production-oriented Kokoro test path. It uses
sherpa-onnx as an end-to-end TTS pipeline runtime and the Kokoro resource bundle
as model input.

```text
Home engine list
  -> ONNX Runtime model list
  -> Kokoro zip/bundle discovery
  -> app internal bundle extraction
  -> TTS test page
  -> KokoroSherpaOnnxTtsPipeline
  -> sherpa-onnx OfflineTts
  -> TtsResult
  -> AudioTrack playback
```

## Module Roles

`:core`

- Owns the generic inference abstractions:
  - `InferenceTask.TEXT_TO_SPEECH`
  - `InferenceOutput.Audio`
  - `EngineConfig.artifacts`
- These primitives are shared by all engines and feature wrappers.

`:core:tts`

- Owns the reusable TTS-facing API:
  - `TtsRequest`
  - `TtsResult`
  - `TtsSynthesisPipeline`
  - `TtsPipelineId`
  - `TtsParameterKeys`
- It does not know about Kokoro, sherpa-onnx, Android assets, or UI.

`:engines:onnxruntime`

- Owns generic ONNX tensor inference.
- B chain does not call this module directly because sherpa-onnx already wraps
  ONNX Runtime and Kokoro-specific preprocessing.

`:app`

- Owns Android-specific model discovery, bundle extraction, UI, playback, and
  the concrete Kokoro adapters.
- The sherpa-onnx AAR is an app dependency:
  `app/libs/sherpa-onnx-1.13.2.aar`.

## Runtime Files

Kokoro is provided as a zip asset. Supported asset locations are:

```text
app/src/main/assets/res/tts/kokoro-multi-lang-v1_1.zip
app/src/main/assets/tts/kokoro-multi-lang-v1_1.zip
```

The zip must contain, either at the top level or under one top-level directory:

```text
model.onnx
voices.bin
tokens.txt
espeak-ng-data/
dict/
lexicon-zh.txt
lexicon-us-en.txt
lexicon-gb-en.txt
```

The current validation requires:

```text
model.onnx
voices.bin
tokens.txt
```

The sherpa B chain additionally requires:

```text
espeak-ng-data/
dict/
at least one lexicon file
```

## Discovery and Preparation

Code path:

```text
app/src/main/java/.../data/model/LocalModelDiscovery.kt
```

Discovery starts when `ModelListViewModel` asks `LocalModelDiscovery.discover()`
for the selected engine.

For `onnxruntime`, discovery combines:

- regular model assets under `assets/models/onnxruntime`
- external files under `getExternalFilesDir("models/onnxruntime")`
- Kokoro zip resources under `assets/res/tts` and `assets/tts`

Kokoro zip detection is intentionally simple:

```text
*.zip && filename contains "kokoro"
```

When a Kokoro model is selected, `ModelListViewModel` calls:

```kotlin
modelDiscovery.prepareModelBundle(model)
```

That method:

- returns an external bundle directory as-is if it already contains Kokoro files
- extracts zip bundles into:

```text
context.filesDir/models_cache/{engineId}/{bundleName}/
```

- validates the extracted bundle
- writes `.bundle_source` so unchanged zip assets are not extracted again
- checks canonical paths for every zip entry to prevent zip path traversal

The TTS page receives the prepared internal bundle directory via:

```text
Route.TtsTest(modelName, modelPath)
```

## UI Flow

Code path:

```text
app/src/main/java/.../ui/screen/tts/TtsTestScreen.kt
app/src/main/java/.../ui/screen/tts/TtsTestViewModel.kt
```

`TtsTestViewModel` owns:

- current input text
- speaker id
- speed
- selected pipeline
- last audio result
- last error

When the user taps synthesize:

1. UI state is converted into `TtsRequest`.
2. The selected `TtsSynthesisPipeline` is created.
3. `pipeline.synthesize(request)` runs on `Dispatchers.IO`.
4. The returned `TtsResult` is cached as `lastAudio`.
5. A compact result summary is shown in the page.

When the user taps play, `TtsAudioPlayer` sends `FloatArray` PCM to Android
`AudioTrack`.

## B Chain Data Flow

Concrete pipeline:

```text
app/src/main/java/.../ui/screen/tts/KokoroSherpaOnnxTtsPipeline.kt
```

Input:

```kotlin
TtsRequest(
    text = "...",
    speakerId = 0,
    speed = 1.0f,
    language = "zh"
)
```

Bundle resolution:

```kotlin
KokoroBundle.fromPath(route.modelPath)
```

`KokoroBundle` maps the prepared directory into concrete files:

```text
rootDir
modelFile        -> model.onnx
voicesFile       -> voices.bin
tokensFile       -> tokens.txt
espeakDataDir    -> espeak-ng-data/
dictDir          -> dict/
lexiconZh        -> lexicon-zh.txt
lexiconUsEn      -> lexicon-us-en.txt
lexiconGbEn      -> lexicon-gb-en.txt
```

sherpa configuration:

```kotlin
OfflineTtsConfig(
    model = OfflineTtsModelConfig(
        kokoro = OfflineTtsKokoroModelConfig(
            model = bundle.modelFile.absolutePath,
            voices = bundle.voicesFile.absolutePath,
            tokens = bundle.tokensFile.absolutePath,
            dataDir = bundle.espeakDataDir.absolutePath,
            dictDir = bundle.dictDir.absolutePath,
            lexicon = bundle.lexiconPaths(),
            lengthScale = request.speed
        ),
        numThreads = 2,
        debug = false
    )
)
```

Generation:

```kotlin
OfflineTts(config).generate(
    text = request.text,
    sid = request.speakerId ?: 0,
    speed = request.speed
)
```

Output mapping:

```text
sherpa GeneratedAudio.samples    -> TtsResult.samples
sherpa GeneratedAudio.sampleRate -> TtsResult.sampleRate
channels                         -> 1
elapsedMillis                    -> measured around generate()
metadata.pipelineId              -> "kokoro-sherpa-onnx"
```

The `OfflineTts` instance is released after each synthesis call.

## Why B Chain Bypasses engines:onnxruntime

sherpa-onnx is not just an ONNX session wrapper for Kokoro. It owns the full TTS
pipeline:

```text
text normalization
  -> phoneme/token conversion
  -> speaker/voice resource loading
  -> ONNX inference
  -> waveform output
```

Therefore B chain depends on sherpa-onnx directly in the app adapter. The generic
`:engines:onnxruntime` module remains available for A chain experiments where
the app implements Kokoro frontend and tensor assembly itself.

## Native Library Packaging

Both sherpa-onnx and `onnxruntime-android` package `libonnxruntime.so`. The app
uses Gradle packaging rules to keep one copy:

```kotlin
packaging {
    jniLibs {
        pickFirsts += "lib/**/libonnxruntime.so"
    }
}
```

This is required for `:app:assembleDebug` to pass while both B chain and generic
ONNX Runtime are present in the same APK.

## Extension Notes

Future TTS models should follow the same layering:

```text
core:tts contract
  -> app or feature pipeline adapter
  -> runtime library or generic engine
  -> TtsResult
```

Future STT/VAD wrappers can mirror this shape in separate modules, for example:

```text
:core:stt
:core:vad
```

Those modules should define domain-level request/result/pipeline contracts while
leaving concrete Android runtime binding to app or feature modules.
