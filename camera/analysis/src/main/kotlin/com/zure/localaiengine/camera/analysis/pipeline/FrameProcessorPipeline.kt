package com.zure.localaiengine.camera.analysis.pipeline

import com.zure.localaiengine.camera.analysis.api.CameraAnalysisInput

interface FrameProcessorStep {
    suspend fun process(buffer: MutableFrameBuffer, context: FrameProcessContext)
}

class FrameProcessorPipeline(
    private val steps: List<FrameProcessorStep>
) {
    suspend fun process(frame: CameraFrame, context: FrameProcessContext): CameraAnalysisInput {
        val buffer = frame.toMutableBuffer()
        steps.forEach { step -> step.process(buffer, context) }
        @Suppress("UNCHECKED_CAST")
        val tensors = buffer.metadata[FrameMetadataKeys.Tensors]
            as? List<com.zure.localaiengine.core.inference.InferenceInput.Tensor>
            ?: error("Pipeline finished without producing tensor inputs.")

        return CameraAnalysisInput(
            tensors = tensors,
            frameInfo = frame.info,
            transform = context.requireTransform(),
            profileId = context.profile.id
        )
    }
}
