package com.zure.localaiengine.core.inference

sealed interface InferenceInput {
    data class Text(val text: String) : InferenceInput

    data class Image(
        val bytes: ByteArray,
        val width: Int? = null,
        val height: Int? = null,
        val mimeType: String? = null
    ) : InferenceInput

    data class Audio(
        val bytes: ByteArray,
        val sampleRate: Int,
        val channels: Int = 1
    ) : InferenceInput

    data class Tensor(
        val name: String,
        val data: Any,
        val shape: IntArray = intArrayOf()
    ) : InferenceInput
}
