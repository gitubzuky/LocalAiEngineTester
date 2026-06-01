package com.zure.localaienginetester.engine

import com.zure.localaiengine.core.engine.AIEngine
import com.zure.localaiengine.core.engine.EngineConfig
import com.zure.localaiengine.core.engine.EngineDescriptor
import com.zure.localaiengine.core.engine.EngineFactory
import com.zure.localaiengine.core.inference.InferenceRequest
import com.zure.localaiengine.core.inference.InferenceResult
import com.zure.localaiengine.core.inference.InferenceTask
import com.zure.localaiengine.core.model.ModelFormat

class SherpaOnnxTtsEngineFactory : EngineFactory {
    override val descriptor = EngineDescriptor(
        id = ENGINE_ID,
        name = "sherpa-onnx",
        supportedFormats = setOf(ModelFormat.ONNX),
        supportedTasks = setOf(InferenceTask.TEXT_TO_SPEECH),
        supportsStreaming = false
    )

    override fun create(): AIEngine = SherpaOnnxTtsEngine(descriptor)

    companion object {
        const val ENGINE_ID = "sherpa-onnx"
    }
}

private class SherpaOnnxTtsEngine(
    override val descriptor: EngineDescriptor
) : AIEngine {
    override suspend fun load(config: EngineConfig) {
        throw UnsupportedOperationException(
            "sherpa-onnx TTS is used through the TtsSynthesisPipeline and SherpaOnnxTtsManager."
        )
    }

    override suspend fun infer(request: InferenceRequest): InferenceResult {
        throw UnsupportedOperationException(
            "sherpa-onnx TTS does not support generic AIEngine inference."
        )
    }

    override suspend fun close() = Unit
}
