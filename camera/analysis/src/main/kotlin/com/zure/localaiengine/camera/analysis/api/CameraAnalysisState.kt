package com.zure.localaiengine.camera.analysis.api

sealed interface CameraAnalysisState {
    data object Idle : CameraAnalysisState
    data object Opening : CameraAnalysisState
    data class Running(
        val lensFacing: AnalysisLensFacing,
        val profileId: String,
        val backend: PreprocessBackend
    ) : CameraAnalysisState
    data class Error(val error: CameraAnalysisError) : CameraAnalysisState
}
