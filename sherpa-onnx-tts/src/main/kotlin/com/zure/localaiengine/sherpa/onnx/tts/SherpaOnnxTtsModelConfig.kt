package com.zure.localaiengine.sherpa.onnx.tts

sealed interface SherpaOnnxTtsModelConfig {
    val numThreads: Int
    val debug: Boolean

    data class Kokoro(
        override val numThreads: Int = 2,
        override val debug: Boolean = false,
        val lengthScale: Float = 1.0f
    ) : SherpaOnnxTtsModelConfig
}
