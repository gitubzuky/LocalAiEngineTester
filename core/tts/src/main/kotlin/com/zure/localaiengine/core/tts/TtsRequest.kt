package com.zure.localaiengine.core.tts

data class TtsRequest(
    val text: String,
    val voiceId: String? = null,
    val speakerId: Int? = null,
    val language: String? = null,
    val speed: Float = 1.0f,
    val extras: Map<String, String> = emptyMap()
) {
    fun toExtras(): Map<String, String> {
        return buildMap {
            putAll(extras)
            voiceId?.let { put(TtsParameterKeys.VOICE_ID, it) }
            speakerId?.let { put(TtsParameterKeys.SPEAKER_ID, it.toString()) }
            language?.let { put(TtsParameterKeys.LANGUAGE, it) }
            put(TtsParameterKeys.SPEED, speed.toString())
        }
    }
}
