package com.zure.localaienginetester.ui.screen.tts

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zure.localaiengine.core.engine.AIEngineManager
import com.zure.localaiengine.core.tts.TtsRequest
import com.zure.localaiengine.core.tts.TtsResult
import com.zure.localaiengine.core.tts.TtsSynthesisPipeline
import com.zure.localaienginetester.base.BaseViewModel
import com.zure.localaienginetester.base.ErrorEvent
import com.zure.localaienginetester.base.UiEvent
import com.zure.localaienginetester.base.UiState
import com.zure.localaienginetester.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class TtsTestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val aiEngineManager: AIEngineManager
) : BaseViewModel<TtsTestEvent, UiState<TtsTestUiState>>() {
    private val route = savedStateHandle.toRoute<Route.TtsTest>()
    private val bundle = KokoroBundle.fromPath(route.modelPath)
    private val audioPlayer = TtsAudioPlayer()
    private var lastAudio: TtsResult? = null

    private val _uiState = MutableStateFlow<UiState<TtsTestUiState>>(
        UiState.Success(
            TtsTestUiState(
                modelName = route.modelName,
                modelPath = route.modelPath
            )
        )
    )
    val uiState: StateFlow<UiState<TtsTestUiState>> = _uiState.asStateFlow()

    fun updateText(text: String) {
        update { it.copy(text = text, lastError = null) }
    }

    fun updateSpeakerId(speakerId: String) {
        update { it.copy(speakerId = speakerId.filter { char -> char.isDigit() }, lastError = null) }
    }

    fun updateSpeed(speed: String) {
        update { it.copy(speed = speed, lastError = null) }
    }

    fun selectPipeline(type: TtsPipelineType) {
        update { it.copy(selectedPipeline = type, lastError = null) }
    }

    fun synthesize() {
        val state = currentData() ?: return
        if (state.isSynthesizing) return
        viewModelScope.launch {
            update { it.copy(isSynthesizing = true, lastError = null, lastResult = null) }
            runCatching {
                val request = TtsRequest(
                    text = state.text.trim(),
                    speakerId = state.speakerId.toIntOrNull() ?: 0,
                    speed = state.speed.toFloatOrNull() ?: 1.0f,
                    language = "zh"
                )
                require(request.text.isNotBlank()) { "请输入要合成的文本。" }
                val pipeline = createPipeline(state.selectedPipeline)
                val result = withContext(Dispatchers.IO) {
                    pipeline.synthesize(request)
                }
                lastAudio = result
                update {
                    it.copy(
                        isSynthesizing = false,
                        lastResult = result.toSummary(state.selectedPipeline.label),
                        lastError = null
                    )
                }
            }.onFailure { throwable ->
                val message = throwable.message ?: "TTS 合成失败。"
                update { it.copy(isSynthesizing = false, lastError = message) }
                sendEvent(TtsTestEvent.Error(message))
            }
        }
    }

    fun playLastAudio() {
        val audio = lastAudio ?: return
        audioPlayer.play(audio.samples, audio.sampleRate, audio.channels)
    }

    override fun onCleared() {
        audioPlayer.stop()
        super.onCleared()
    }

    private fun createPipeline(type: TtsPipelineType): TtsSynthesisPipeline {
        return when (type) {
            TtsPipelineType.OnnxRuntime -> KokoroOnnxRuntimeTtsPipeline(bundle, aiEngineManager)
            TtsPipelineType.SherpaOnnx -> KokoroSherpaOnnxTtsPipeline(context, route.modelPath)
        }
    }

    private fun TtsResult.toSummary(label: String): TtsResultSummary {
        return TtsResultSummary(
            pipelineLabel = label,
            sampleRate = sampleRate,
            channels = channels,
            sampleCount = samples.size,
            elapsedMillis = elapsedMillis
        )
    }

    private fun update(reducer: (TtsTestUiState) -> TtsTestUiState) {
        val current = currentData() ?: return
        _uiState.value = UiState.Success(reducer(current))
    }

    private fun currentData(): TtsTestUiState? {
        return (_uiState.value as? UiState.Success)?.data
    }
}

sealed class TtsTestEvent : UiEvent {
    data class Error(override val message: String) : TtsTestEvent(), ErrorEvent
}
