package com.zure.localaiengine.camera.analysis.api

import com.zure.localaiengine.camera.analysis.pipeline.CameraFrame
import com.zure.localaiengine.camera.analysis.pipeline.FrameProcessContext
import com.zure.localaiengine.camera.analysis.pipeline.FrameProcessorPipeline

object CameraFramePreprocessor {
    suspend fun process(
        frame: CameraFrame,
        profile: VisionInputProfile,
        config: CameraAnalysisConfig
    ): CameraAnalysisInput {
        val context = FrameProcessContext(frame.info, profile, config)
        val pipeline = FrameProcessorPipeline(profile.createPipeline(config))
        return pipeline.process(frame, context)
    }
}
