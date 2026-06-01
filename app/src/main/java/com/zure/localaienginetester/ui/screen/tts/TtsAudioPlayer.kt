package com.zure.localaienginetester.ui.screen.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

class TtsAudioPlayer {
    private var audioTrack: AudioTrack? = null

    fun play(samples: FloatArray, sampleRate: Int, channels: Int) {
        stop()
        val channelMask = if (channels == 1) {
            AudioFormat.CHANNEL_OUT_MONO
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(samples.size * Float.SIZE_BYTES)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(minBufferSize)
            .build()
        audioTrack = track
        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        track.play()
    }

    fun stop() {
        audioTrack?.run {
            runCatching { stop() }
            release()
        }
        audioTrack = null
    }
}
