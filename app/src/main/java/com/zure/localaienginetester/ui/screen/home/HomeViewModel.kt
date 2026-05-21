package com.zure.localaienginetester.ui.screen.home

import androidx.lifecycle.viewModelScope
import com.zure.localaiengine.core.engine.AIEngineManager
import com.zure.localaienginetester.base.BaseViewModel
import com.zure.localaienginetester.base.ErrorEvent
import com.zure.localaienginetester.base.UiEvent
import com.zure.localaienginetester.base.UiState
import com.zure.localaienginetester.domain.entity.AiModel
import com.zure.localaienginetester.util.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val aiEngineManager: AIEngineManager
) : BaseViewModel<HomeEvent, UiState<List<AiModel>>>() {

    private final val TAG = "HomeViewModel"

    private val _uiState = MutableStateFlow<UiState<List<AiModel>>>(UiState.Idle)
    val uiState: StateFlow<UiState<List<AiModel>>> = _uiState.asStateFlow()

    init {
        AppLog.d(TAG, "init")
        loadModels()
    }

    private fun loadModels() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val engines = aiEngineManager.availableEngines.mapIndexed { index, descriptor ->
                AiModel(
                    id = index,
                    engineId = descriptor.id,
                    type = descriptor.supportedFormats.joinToString(separator = "/"),
                    name = descriptor.name
                )
            }
            _uiState.value = UiState.Success(engines)
        }
    }
}

sealed class HomeEvent : UiEvent {
    data class Error(override val message: String) : HomeEvent(), ErrorEvent
}
