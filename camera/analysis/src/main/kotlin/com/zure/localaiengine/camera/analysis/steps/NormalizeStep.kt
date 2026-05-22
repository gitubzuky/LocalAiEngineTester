package com.zure.localaiengine.camera.analysis.steps

import com.zure.localaiengine.camera.analysis.api.NormalizationSpec
import com.zure.localaiengine.camera.analysis.pipeline.FrameProcessContext
import com.zure.localaiengine.camera.analysis.pipeline.FrameProcessorStep
import com.zure.localaiengine.camera.analysis.pipeline.MutableFrameBuffer

class NormalizeStep(
    private val spec: NormalizationSpec
) : FrameProcessorStep {
    override suspend fun process(buffer: MutableFrameBuffer, context: FrameProcessContext) {
        buffer.metadata[NormalizationKey] = spec
    }

    companion object {
        const val NormalizationKey = "normalization_spec"
    }
}
