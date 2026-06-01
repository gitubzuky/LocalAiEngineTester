package com.zure.localaienginetester.ui.screen.tts

data class TtsTestUiState(
    val modelName: String,
    val modelPath: String,
    val text: String = "你好，欢迎测试 Kokoro 文本转语音。",
    val speakerId: String = "0",
    val speed: String = "1.0",
    val selectedPipeline: TtsPipelineType = TtsPipelineType.SherpaOnnx,
    val isSynthesizing: Boolean = false,
    val lastResult: TtsResultSummary? = null,
    val lastError: String? = null
)

data class TtsResultSummary(
    val pipelineLabel: String,
    val sampleRate: Int,
    val channels: Int,
    val sampleCount: Int,
    val elapsedMillis: Long?
)
