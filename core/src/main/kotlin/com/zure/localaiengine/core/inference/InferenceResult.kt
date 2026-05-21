package com.zure.localaiengine.core.inference

data class InferenceResult(
    val outputs: List<InferenceOutput>,
    val elapsedMillis: Long? = null,
    val metadata: Map<String, String> = emptyMap()
)
