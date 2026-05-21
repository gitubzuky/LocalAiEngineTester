package com.zure.localaiengine.engines.tflite

import com.zure.localaiengine.core.engine.AIEngine
import com.zure.localaiengine.core.engine.EngineDescriptor
import com.zure.localaiengine.core.engine.EngineFactory
import com.zure.localaiengine.core.inference.InferenceTask
import com.zure.localaiengine.core.model.ModelFormat

class TfLiteEngineFactory : EngineFactory {
    override val descriptor = EngineDescriptor(
        id = ENGINE_ID,
        name = "TensorFlow Lite",
        supportedFormats = setOf(ModelFormat.TFLITE),
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

    override fun create(): AIEngine = TfLiteEngine(descriptor)

    companion object {
        const val ENGINE_ID = "tflite"
    }
}
