package com.zure.localaienginetester.ui.screen.translation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zure.localaiengine.core.engine.AIEngineManager
import com.zure.localaiengine.core.inference.InferenceInput
import com.zure.localaiengine.core.inference.InferenceOutput
import com.zure.localaiengine.core.inference.InferenceRequest
import com.zure.localaiengine.core.inference.InferenceTask
import com.zure.localaienginetester.base.BaseViewModel
import com.zure.localaienginetester.base.ErrorEvent
import com.zure.localaienginetester.base.UiEvent
import com.zure.localaienginetester.base.UiState
import com.zure.localaienginetester.config.InferenceConfigPresets
import com.zure.localaienginetester.navigation.Route
import com.zure.localaienginetester.util.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TranslationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val aiEngineManager: AIEngineManager
) : BaseViewModel<TranslationEvent, UiState<TranslationUiData>>() {

    private val route = savedStateHandle.toRoute<Route.TranslationTest>()
    private val _uiState = MutableStateFlow(
        TranslationUiData(modelName = route.modelName)
    )
    val uiState: StateFlow<TranslationUiData> = _uiState.asStateFlow()

    private var translationJob: Job? = null

    fun updateSourceLanguage(language: String) {
        _uiState.update { it.copy(sourceLanguage = language) }
    }

    fun updateTargetLanguage(language: String) {
        _uiState.update { it.copy(targetLanguage = language) }
    }

    fun updateSourceText(text: String) {
        val limitedText = text.take(MAX_SOURCE_TEXT_CHARS)
        val limitMessage = if (text.length > MAX_SOURCE_TEXT_CHARS) {
            "输入文本已达到 ${MAX_SOURCE_TEXT_CHARS} 字上限。为保证整段翻译准确性，当前测试页不自动分段。"
        } else {
            null
        }
        _uiState.update {
            it.copy(
                sourceText = limitedText,
                errorMessage = limitMessage
            )
        }
    }

    fun translate() {
        val state = _uiState.value
        if (state.sourceText.isBlank() || state.isTranslating) {
            return
        }
        if (state.sourceText.length > state.sourceTextLimit) {
            _uiState.update {
                it.copy(errorMessage = "输入文本超过 ${state.sourceTextLimit} 字上限，请缩短后再翻译。")
            }
            return
        }

        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            _uiState.update { it.copy(outputText = "", isTranslating = true, errorMessage = null) }
            try {
                val prompt = buildPrompt(state)
                AppLog.i(
                    TAG,
                    "Translation started model=${state.modelName}, source=${state.sourceLanguage}, " +
                        "target=${state.targetLanguage}, inputLength=${state.sourceText.length}, prompt=$prompt"
                )
                val request = InferenceRequest(
                    task = InferenceTask.TEXT_GENERATION,
                    inputs = listOf(InferenceInput.Text(prompt)),
                    parameters = InferenceConfigPresets.translation
                )

                var accumulatedLength = 0
                aiEngineManager.stream(request)
                    .flowOn(Dispatchers.Default)
                    .collect { chunk ->
                        val piece = (chunk.output as? InferenceOutput.Text)?.text.orEmpty()
                        if (piece.isNotEmpty()) {
                            accumulatedLength += piece.length
                            AppLog.d(
                                TAG,
                                "Model stream chunk length=${piece.length}, accumulatedLength=$accumulatedLength, " +
                                    "isFinal=${chunk.isFinal}, content=$piece"
                            )
                            _uiState.update { it.copy(outputText = it.outputText + piece) }
                        }
                        if (chunk.isFinal) {
                            AppLog.i(
                                TAG,
                                "Translation finished outputLength=${_uiState.value.outputText.length}, " +
                                    "output=${_uiState.value.outputText}"
                            )
                            AppLog.flush()
                            _uiState.update { it.copy(isTranslating = false) }
                        }
                    }
            } catch (throwable: Throwable) {
                val message = throwable.message ?: "Translation failed."
                AppLog.e(TAG, "Translation failed: $message", throwable)
                AppLog.flush()
                _uiState.update { it.copy(isTranslating = false, errorMessage = message) }
                sendEvent(TranslationEvent.Error(message))
            }
        }
    }

    override fun onCleared() {
        translationJob?.cancel()
        super.onCleared()
    }

    private fun buildPrompt(state: TranslationUiData): String {
        val targetLanguage = state.targetLanguage.toInstructionName()
        val sourceLanguage = state.sourceLanguage.toInstructionName()
        val sourceInstruction = if (state.sourceLanguage == "Auto") {
            "Detect the source language automatically."
        } else {
            "The source language is $sourceLanguage."
        }
        return "$sourceInstruction Translate the following segment into $targetLanguage, " +
            "without additional explanation: ${state.sourceText}"
    }

    private fun String.toInstructionName(): String {
        return when (this) {
            "Auto" -> "Auto"
            "Chinese" -> "Simplified Chinese"
            else -> this
        }
    }

    companion object {
        private const val TAG = "Translation"
        private const val MAX_SOURCE_TEXT_CHARS = 1500
    }
}

data class TranslationUiData(
    val modelName: String,
    val sourceLanguage: String = "Auto",
    val targetLanguage: String = "Chinese",
    val sourceText: String = "",
    val outputText: String = "",
    val isTranslating: Boolean = false,
    val errorMessage: String? = null,
    val sourceTextLimit: Int = 1500
)

sealed class TranslationEvent : UiEvent {
    data class Error(override val message: String) : TranslationEvent(), ErrorEvent
}
