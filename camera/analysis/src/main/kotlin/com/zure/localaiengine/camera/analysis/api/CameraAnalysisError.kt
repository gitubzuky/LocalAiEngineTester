package com.zure.localaiengine.camera.analysis.api

sealed interface CameraAnalysisError {
    data object CameraPermissionMissing : CameraAnalysisError
    data object CameraUnavailable : CameraAnalysisError
    data object LifecycleNotReady : CameraAnalysisError
    data object InvalidAnalysisConfig : CameraAnalysisError
    data class BindFailed(val cause: Throwable) : CameraAnalysisError
    data class FrameProcessingFailed(val cause: Throwable) : CameraAnalysisError
    data class NativeBackendUnavailable(val cause: Throwable?) : CameraAnalysisError
}
