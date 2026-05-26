package com.zure.localaiengine.camera.analysis.api

import com.zure.localaiengine.camera.analysis.pipeline.CameraFrame
import com.zure.localaiengine.core.inference.InferenceInput

data class CameraAnalysisInput(
    val tensors: List<InferenceInput.Tensor>,
    val frameInfo: CameraFrameInfo,
    val transform: FrameTransform,
    val profileId: String,
    val sourceFrame: CameraFrame? = null
)

data class CameraFrameInfo(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val rotationDegrees: Int,
    val timestampNanos: Long,
    val lensFacing: AnalysisLensFacing
)

data class FrameTransform(
    val modelInputWidth: Int,
    val modelInputHeight: Int,
    val sourceWidth: Float = 0f,
    val sourceHeight: Float = 0f,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropWidth: Float,
    val cropHeight: Float,
    val scaleX: Float,
    val scaleY: Float,
    val padLeft: Float = 0f,
    val padTop: Float = 0f,
    val isMirrored: Boolean = false
)
