package com.zure.localaiengine.core.inference

import com.zure.localaiengine.core.vision.Detection

sealed interface InferenceOutput {
    data class Text(val text: String) : InferenceOutput

    data class Tokens(val tokenIds: List<Int>) : InferenceOutput

    data class Embedding(val vector: FloatArray) : InferenceOutput

    data class Audio(
        val samples: FloatArray,
        val sampleRate: Int,
        val channels: Int = 1
    ) : InferenceOutput

    data class Tensor(
        val name: String,
        val data: Any,
        val shape: IntArray = intArrayOf()
    ) : InferenceOutput

    data class Detections(val items: List<Detection>) : InferenceOutput
}
