package com.zure.localaiengine.engines.onnxruntime

import com.zure.localaiengine.core.engine.AIEngine
import com.zure.localaiengine.core.engine.EngineConfig
import com.zure.localaiengine.core.engine.EngineDescriptor
import com.zure.localaiengine.core.engine.EngineFactory
import com.zure.localaiengine.core.inference.InferenceRequest
import com.zure.localaiengine.core.inference.InferenceResult
import com.zure.localaiengine.core.inference.InferenceTask
import com.zure.localaiengine.core.model.ModelFormat

class OnnxRuntimeEngineFactory : EngineFactory {
    override val descriptor = EngineDescriptor(
        id = "onnxruntime",
        name = "ONNX Runtime",
        supportedFormats = setOf(ModelFormat.ONNX),
        supportedTasks = setOf(
            InferenceTask.IMAGE_CLASSIFICATION,
            InferenceTask.OBJECT_DETECTION,
            InferenceTask.IMAGE_SEGMENTATION,
            InferenceTask.OCR,
            InferenceTask.TEXT_EMBEDDING,
            InferenceTask.TENSOR
        ),
        supportsStreaming = false
    )

    override fun create(): AIEngine = OnnxRuntimeEngine(descriptor)
}

private class OnnxRuntimeEngine(
    override val descriptor: EngineDescriptor
) : AIEngine {
    override suspend fun load(config: EngineConfig) {
        throw UnsupportedOperationException("ONNX Runtime dependency is not configured yet.")
    }

    override suspend fun infer(request: InferenceRequest): InferenceResult {
        throw UnsupportedOperationException("ONNX Runtime dependency is not configured yet.")
    }

    override suspend fun close() = Unit
}
