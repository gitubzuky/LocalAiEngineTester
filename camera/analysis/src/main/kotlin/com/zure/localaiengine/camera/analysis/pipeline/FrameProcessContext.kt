package com.zure.localaiengine.camera.analysis.pipeline

import com.zure.localaiengine.camera.analysis.api.CameraAnalysisConfig
import com.zure.localaiengine.camera.analysis.api.CameraFrameInfo
import com.zure.localaiengine.camera.analysis.api.FrameTransform
import com.zure.localaiengine.camera.analysis.api.VisionInputProfile

class FrameProcessContext(
    val sourceInfo: CameraFrameInfo,
    val profile: VisionInputProfile,
    val config: CameraAnalysisConfig
) {
    private var transform: FrameTransform? = null

    fun setTransform(value: FrameTransform) {
        transform = value
    }

    fun requireTransform(): FrameTransform {
        return requireNotNull(transform) {
            "FrameTransform was not produced by the preprocessing pipeline."
        }
    }
}
