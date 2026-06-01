package com.zure.localaiengine.core.tts

interface TtsSynthesisPipeline {
    val id: TtsPipelineId
    val displayName: String

    suspend fun synthesize(request: TtsRequest): TtsResult
}
