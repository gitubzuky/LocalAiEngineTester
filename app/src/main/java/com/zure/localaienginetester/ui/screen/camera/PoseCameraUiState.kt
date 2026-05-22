package com.zure.localaienginetester.ui.screen.camera

import com.zure.localaiengine.camera.analysis.api.FrameTransform

data class PoseCameraUiData(
    val modelName: String,
    val hasCameraPermission: Boolean = false,
    val isAnalyzing: Boolean = false,
    val pose: PoseResult? = null,
    val frameTransform: FrameTransform? = null,
    val detectedKeypoints: Int = 0,
    val maxPoseScore: Float? = null,
    val lastInferenceMillis: Long? = null,
    val lastError: String? = null,
    val analysisIntervalMillis: Long = 300L
)
