package com.zure.localaienginetester.ui.screen.tts

import com.zure.localaiengine.core.engine.AIEngineManager
import com.zure.localaiengine.core.engine.EngineConfig
import com.zure.localaiengine.core.model.ModelFormat
import com.zure.localaiengine.core.tts.TtsPipelineId
import com.zure.localaiengine.core.tts.TtsRequest
import com.zure.localaiengine.core.tts.TtsResult
import com.zure.localaiengine.core.tts.TtsSynthesisPipeline

class KokoroOnnxRuntimeTtsPipeline(
    private val bundle: KokoroBundle,
    private val engineManager: AIEngineManager
) : TtsSynthesisPipeline {
    override val id = TtsPipelineId("kokoro-onnxruntime")
    override val displayName = "ONNX Runtime"

    override suspend fun synthesize(request: TtsRequest): TtsResult {
        bundle.validate()
        engineManager.loadStandaloneEngine(
            engineId = "onnxruntime",
            config = EngineConfig(
                modelPath = bundle.modelFile.absolutePath,
                modelFormat = ModelFormat.ONNX,
                artifacts = bundle.artifacts
            )
        ).close()
        error(
            "ONNX Runtime chain loaded model resources, but Kokoro text frontend is not implemented yet. " +
                "This chain still needs text normalization, phoneme/token mapping, voice embedding parsing, " +
                "and ONNX tensor assembly before it can synthesize audio."
        )
    }
}
