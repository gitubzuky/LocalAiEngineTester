package com.zure.localaienginetester.ui.screen.camera

import com.zure.localaiengine.camera.analysis.api.FrameTransform
import com.zure.localaiengine.camera.analysis.profiles.RtmDetPersonPreprocessMode
import com.zure.localaiengine.core.vision.Detection

data class PoseCameraUiData(
    val modelName: String,
    val detectorModelName: String? = null,
    val detectorType: PersonDetectorType = PersonDetectorType.Yolo,
    val detectorPreprocessMode: RtmDetPersonPreprocessMode = RtmDetPersonPreprocessMode.RgbNone,
    val hasCameraPermission: Boolean = false,
    val isAnalyzing: Boolean = false,
    val pose: PoseResult? = null,
    val frameTransform: FrameTransform? = null,
    val personDetection: Detection? = null,
    val detectedPersons: Int = 0,
    val detectedKeypoints: Int = 0,
    val maxPoseScore: Float? = null,
    val lastInferenceMillis: Long? = null,
    val lastDetectionMillis: Long? = null,
    val lastError: String? = null,
    val analysisIntervalMillis: Long = 300L
)

enum class PersonDetectorType(val label: String) {
    Yolo("YOLO person"),
    RtmDet("RTMDet person")
}
