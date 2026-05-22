package com.zure.localaiengine.camera.analysis.api

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface CameraAnalysisController {
    val state: StateFlow<CameraAnalysisState>
    val outputs: Flow<CameraAnalysisInput>

    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView?,
        profile: VisionInputProfile,
        config: CameraAnalysisConfig = CameraAnalysisConfig()
    )

    suspend fun unbind()
}
