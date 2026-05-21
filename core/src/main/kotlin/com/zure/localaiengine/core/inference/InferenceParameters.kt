package com.zure.localaiengine.core.inference

data class InferenceParameters(
    val maxTokens: Int? = null,
    val temperature: Float? = null,
    val topK: Int? = null,
    val topP: Float? = null,
    val repetitionPenalty: Float? = null,
    val seed: Int? = null,
    val useChatTemplate: Boolean? = null,
    val stopSequences: List<String> = emptyList(),
    val extras: Map<String, String> = emptyMap()
)
