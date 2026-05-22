package com.zure.localaienginetester.ui.screen.camera

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zure.localaiengine.camera.analysis.api.CameraAnalysisInput
import com.zure.localaiengine.core.engine.AIEngineManager
import com.zure.localaiengine.core.inference.InferenceRequest
import com.zure.localaiengine.core.inference.InferenceTask
import com.zure.localaienginetester.base.BaseViewModel
import com.zure.localaienginetester.base.ErrorEvent
import com.zure.localaienginetester.base.UiEvent
import com.zure.localaienginetester.base.UiState
import com.zure.localaienginetester.navigation.Route
import com.zure.localaienginetester.util.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class PoseCameraViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val aiEngineManager: AIEngineManager
) : BaseViewModel<PoseCameraEvent, UiState<PoseCameraUiData>>() {

    private val route = savedStateHandle.toRoute<Route.CameraPoseTest>()
    private val decoder = RtmposeOutputDecoder()
    private val inferenceMutex = Mutex()
    private var analyzeLogCount = 0
    private val _uiState = MutableStateFlow<UiState<PoseCameraUiData>>(
        UiState.Success(PoseCameraUiData(modelName = route.modelName))
    )
    val uiState: StateFlow<UiState<PoseCameraUiData>> = _uiState.asStateFlow()

    fun onPermissionChanged(granted: Boolean) {
        updateData { data ->
            data.copy(
                hasCameraPermission = granted,
                lastError = if (granted) null else "需要相机权限才能进行姿态分析。"
            )
        }
    }

    fun onCameraError(message: String) {
        updateData { data -> data.copy(lastError = message, isAnalyzing = false) }
        sendEvent(PoseCameraEvent.Error(message))
    }

    fun analyze(input: CameraAnalysisInput) {
        if (inferenceMutex.isLocked) return
        viewModelScope.launch {
            inferenceMutex.withLock {
                updateData { data -> data.copy(isAnalyzing = true, lastError = null) }
                runCatching {
                    analyzeLogCount += 1
                    if (shouldLog(analyzeLogCount)) {
                        AppLog.i(
                            TAG,
                            "analyze[$analyzeLogCount] profile=${input.profileId} " +
                                "frame=${input.frameInfo.sourceWidth}x${input.frameInfo.sourceHeight} " +
                                "rotation=${input.frameInfo.rotationDegrees} lens=${input.frameInfo.lensFacing} " +
                                "transform=${input.transform} " +
                                "inputs=${input.tensors.joinToString { "${it.name}:${it.shape.contentToString()}:${it.data.javaClass.simpleName}" }}"
                        )
                    }
                    val result = aiEngineManager.infer(
                        InferenceRequest(
                            task = InferenceTask.TENSOR,
                            inputs = input.tensors
                        )
                    )
                    val pose = decoder.decode(
                        outputs = result.outputs,
                        inputWidth = input.transform.modelInputWidth,
                        inputHeight = input.transform.modelInputHeight
                    )
                    if (shouldLog(analyzeLogCount)) {
                        AppLog.i(
                            TAG,
                            "analyze[$analyzeLogCount] result elapsed=${result.elapsedMillis}ms " +
                                "outputs=${result.outputs.filterIsInstance<com.zure.localaiengine.core.inference.InferenceOutput.Tensor>().joinToString { "${it.name}:${it.shape.contentToString()}" }} " +
                                "posePoints=${pose.keypoints.size} maxScore=${pose.keypoints.maxOfOrNull { it.score }}"
                        )
                    }
                    updateData { data ->
                        data.copy(
                            isAnalyzing = false,
                            pose = pose,
                            frameTransform = input.transform,
                            detectedKeypoints = pose.keypoints.size,
                            maxPoseScore = pose.keypoints.maxOfOrNull { it.score },
                            lastInferenceMillis = result.elapsedMillis,
                            lastError = null
                        )
                    }
                }.onFailure { throwable ->
                    val message = throwable.message ?: "姿态分析失败。"
                    updateData { data -> data.copy(isAnalyzing = false, lastError = message) }
                    sendEvent(PoseCameraEvent.Error(message))
                }
            }
        }
    }

    private fun updateData(reducer: (PoseCameraUiData) -> PoseCameraUiData) {
        val current = (_uiState.value as? UiState.Success)?.data
            ?: PoseCameraUiData(modelName = route.modelName)
        _uiState.value = UiState.Success(reducer(current))
    }

    private fun shouldLog(count: Int): Boolean = count <= 5 || count % 30 == 0

    private companion object {
        const val TAG = "RTMPoseDebug"
    }
}

sealed class PoseCameraEvent : UiEvent {
    data class Error(override val message: String) : PoseCameraEvent(), ErrorEvent
}
