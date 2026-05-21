package com.zure.localaiengine.core.inference

data class InferenceChunk(
    val output: InferenceOutput,
    val isFinal: Boolean = false
)
