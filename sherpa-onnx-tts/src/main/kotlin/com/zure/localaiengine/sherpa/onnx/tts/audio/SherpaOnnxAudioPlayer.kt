package com.zure.localaiengine.sherpa.onnx.tts.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

class SherpaOnnxAudioPlayer {
    private var audioTrack: AudioTrack? = null

    fun play(audio: SherpaOnnxGeneratedAudio) {
        play(
            samples = audio.samples,
            sampleRate = audio.sampleRate,
            channels = audio.channels
        )
    }

    fun play(samples: FloatArray, sampleRate: Int, channels: Int = 1) {
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

    fun release() {
        stop()
    }
}
