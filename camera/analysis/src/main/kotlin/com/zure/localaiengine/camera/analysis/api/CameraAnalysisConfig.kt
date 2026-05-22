package com.zure.localaiengine.camera.analysis.api

enum class AnalysisLensFacing {
    Back,
    Front
}

enum class PreprocessBackend {
    Kotlin,
    Native,
    Auto
}

data class CameraAnalysisConfig(
    val lensFacing: AnalysisLensFacing = AnalysisLensFacing.Back,
    val targetWidth: Int = 640,
    val targetHeight: Int = 480,
    val maxAnalysisFps: Int = 5,
    val backend: PreprocessBackend = PreprocessBackend.Auto,
    val mirrorFrontCamera: Boolean = true
)
