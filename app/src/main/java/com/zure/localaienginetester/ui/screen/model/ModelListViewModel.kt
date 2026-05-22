package com.zure.localaienginetester.ui.screen.model

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zure.localaiengine.core.engine.AIEngineManager
import com.zure.localaiengine.core.engine.EngineConfig
import com.zure.localaiengine.core.engine.EngineDescriptor
import com.zure.localaiengine.core.model.ModelFormat
import com.zure.localaienginetester.base.BaseViewModel
import com.zure.localaienginetester.base.ErrorEvent
import com.zure.localaienginetester.base.UiEvent
import com.zure.localaienginetester.base.UiState
import com.zure.localaienginetester.config.InferenceConfigPresets
import com.zure.localaienginetester.data.model.LocalModelDiscovery
import com.zure.localaienginetester.domain.entity.LocalModel
import com.zure.localaienginetester.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ModelListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val aiEngineManager: AIEngineManager,
    private val modelDiscovery: LocalModelDiscovery
) : BaseViewModel<ModelListEvent, UiState<ModelListUiData>>() {

    private val route = savedStateHandle.toRoute<Route.ModelList>()
    private val _uiState = MutableStateFlow<UiState<ModelListUiData>>(UiState.Idle)
    val uiState: StateFlow<UiState<ModelListUiData>> = _uiState.asStateFlow()

    init {
        loadModels()
    }

    fun loadSelectedModel(model: LocalModel) {
        viewModelScope.launch {
            val currentData = (_uiState.value as? UiState.Success)?.data ?: return@launch
            if (currentData.loadingModelId != null) {
                return@launch
            }
            _uiState.value = UiState.Success(currentData.copy(loadingModelId = model.id))

            try {
                require(model.format in currentData.engine.supportedFormats) {
                    "${currentData.engine.name} does not support ${model.format}."
                }
                val modelFile = withContext(Dispatchers.IO) {
                    modelDiscovery.prepareModelFile(model)
                }
                aiEngineManager.switchEngine(
                    model.engineId,
                    EngineConfig(
                        modelPath = modelFile.absolutePath,
                        modelFormat = model.format,
                        runtime = InferenceConfigPresets.llamaRuntime
                    )
                )
                _uiState.value = UiState.Success(currentData.copy(loadingModelId = null))
                if (model.isRtmposeBody2dModel()) {
                    sendEvent(ModelListEvent.CameraPoseReady(model.name))
                } else {
                    sendEvent(ModelListEvent.ModelReady(model.name))
                }
            } catch (throwable: Throwable) {
                val message = throwable.message ?: "Failed to load model."
                sendEvent(ModelListEvent.Error(message))
                _uiState.value = UiState.Success(
                    currentData.copy(
                        loadingModelId = null,
                        errorMessage = message
                    )
                )
            }
        }
    }

    private fun loadModels() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val engine = aiEngineManager.availableEngines.firstOrNull { it.id == route.engineId }
            if (engine == null) {
                _uiState.value = UiState.Error("Engine is not packaged: ${route.engineId}")
                return@launch
            }

            val discoveryResult = withContext(Dispatchers.IO) {
                modelDiscovery.discover(engine)
            }
            _uiState.value = UiState.Success(
                ModelListUiData(
                    engine = engine,
                    models = discoveryResult.models,
                    externalDirectoryPath = discoveryResult.externalDirectoryPath,
                    errorMessage = discoveryResult.externalDirectoryError
                )
            )
        }
    }

    private fun LocalModel.isRtmposeBody2dModel(): Boolean {
        return engineId == "tflite" &&
            format == ModelFormat.TFLITE &&
            name.contains("rtmpose", ignoreCase = true) &&
            name.contains("body2d", ignoreCase = true)
    }
}

data class ModelListUiData(
    val engine: EngineDescriptor,
    val models: List<LocalModel>,
    val externalDirectoryPath: String? = null,
    val loadingModelId: String? = null,
    val errorMessage: String? = null
)

sealed class ModelListEvent : UiEvent {
    data class Error(override val message: String) : ModelListEvent(), ErrorEvent
    data class ModelReady(val modelName: String) : ModelListEvent()
    data class CameraPoseReady(val modelName: String) : ModelListEvent()
}
