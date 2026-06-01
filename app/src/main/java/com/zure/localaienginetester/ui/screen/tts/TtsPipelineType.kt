package com.zure.localaienginetester.ui.screen.tts

enum class TtsPipelineType(
    val label: String,
    val description: String
) {
    OnnxRuntime(
        label = "ONNX Runtime",
        description = "core:tts + app Kokoro adapter + engines:onnxruntime"
    ),
    SherpaOnnx(
        label = "sherpa-onnx",
        description = "core:tts + app Sherpa adapter + sherpa-onnx + Kokoro"
    )
}
