package com.zure.localaiengine.core.tts

data class TtsResult(
    val samples: FloatArray,
    val sampleRate: Int,
    val channels: Int = 1,
    val elapsedMillis: Long? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TtsResult

        if (!samples.contentEquals(other.samples)) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
        if (elapsedMillis != other.elapsedMillis) return false
        return metadata == other.metadata
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        result = 31 * result + (elapsedMillis?.hashCode() ?: 0)
        result = 31 * result + metadata.hashCode()
        return result
    }
}
