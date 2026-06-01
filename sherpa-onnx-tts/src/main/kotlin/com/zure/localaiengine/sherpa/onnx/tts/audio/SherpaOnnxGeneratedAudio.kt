package com.zure.localaiengine.sherpa.onnx.tts.audio

data class SherpaOnnxGeneratedAudio(
    val samples: FloatArray,
    val sampleRate: Int,
    val channels: Int = 1,
    val elapsedMillis: Long,
    val metadata: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        return other is SherpaOnnxGeneratedAudio &&
            samples.contentEquals(other.samples) &&
            sampleRate == other.sampleRate &&
            channels == other.channels &&
            elapsedMillis == other.elapsedMillis &&
            metadata == other.metadata
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        result = 31 * result + elapsedMillis.hashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
}
