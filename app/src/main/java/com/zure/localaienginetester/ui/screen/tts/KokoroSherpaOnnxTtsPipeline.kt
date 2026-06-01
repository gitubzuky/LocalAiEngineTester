package com.zure.localaienginetester.ui.screen.tts

import android.content.Context
import com.zure.localaiengine.core.tts.TtsPipelineId
import com.zure.localaiengine.core.tts.TtsRequest
import com.zure.localaiengine.core.tts.TtsResult
import com.zure.localaiengine.core.tts.TtsSynthesisPipeline
import com.zure.localaiengine.sherpa.onnx.tts.SherpaOnnxTtsInitConfig
import com.zure.localaiengine.sherpa.onnx.tts.SherpaOnnxTtsManager
import com.zure.localaiengine.sherpa.onnx.tts.SherpaOnnxTtsModelConfig
import java.io.File

class KokoroSherpaOnnxTtsPipeline(
    private val context: Context,
    private val bundlePath: String
) : TtsSynthesisPipeline {
    override val id = TtsPipelineId("kokoro-sherpa-onnx")
    override val displayName = "sherpa-onnx"

    override suspend fun synthesize(request: TtsRequest): TtsResult {
        val manager = SherpaOnnxTtsManager(context)
        return try {
            val bundle = manager.init(
                SherpaOnnxTtsInitConfig(
                    assetDirs = emptyList(),
                    externalDirs = emptyList(),
                    explicitBundlePath = File(bundlePath)
                )
            )
            val config = manager.createOfflineTtsConfig(
                bundle = bundle,
                modelConfig = SherpaOnnxTtsModelConfig.Kokoro(
                    numThreads = 2,
                    debug = false,
                    lengthScale = request.speed
                )
            )
            val tts = manager.createOfflineTts(config)
            val audio = manager.generate(
                tts = tts,
                text = request.text,
                sid = request.speakerId ?: 0,
                speed = request.speed
            )
            TtsResult(
                samples = audio.samples,
                sampleRate = audio.sampleRate,
                channels = audio.channels,
                elapsedMillis = audio.elapsedMillis,
                metadata = audio.metadata + ("pipelineId" to id.value)
            )
        } finally {
            manager.release()
        }
    }
}
