package com.zure.localaienginetester.ui.screen.tts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.zure.localaienginetester.base.UiState
import com.zure.localaienginetester.ui.component.AppScaffold
import com.zure.localaienginetester.ui.theme.LocalAIEngineTesterTheme

@Composable
fun TtsTestScreen(
    navController: NavController,
    viewModel: TtsTestViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val data = (uiState as? UiState.Success)?.data

    if (data != null) {
        TtsTestContent(
            uiState = data,
            onBackClick = { navController.popBackStack() },
            onTextChange = viewModel::updateText,
            onSpeakerIdChange = viewModel::updateSpeakerId,
            onSpeedChange = viewModel::updateSpeed,
            onPipelineSelect = viewModel::selectPipeline,
            onSynthesizeClick = viewModel::synthesize,
            onPlayClick = viewModel::playLastAudio
        )
    }
}

@Composable
private fun TtsTestContent(
    uiState: TtsTestUiState,
    onBackClick: () -> Unit,
    onTextChange: (String) -> Unit,
    onSpeakerIdChange: (String) -> Unit,
    onSpeedChange: (String) -> Unit,
    onPipelineSelect: (TtsPipelineType) -> Unit,
    onSynthesizeClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    AppScaffold(
        title = "TTS 测试",
        onBackClick = onBackClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = uiState.modelName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = uiState.modelPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PipelineSelector(
                selected = uiState.selectedPipeline,
                onPipelineSelect = onPipelineSelect
            )

            OutlinedTextField(
                value = uiState.text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                label = { Text(text = "输入文本") }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.speakerId,
                    onValueChange = onSpeakerIdChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(text = "Speaker") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.speed,
                    onValueChange = onSpeedChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(text = "Speed") },
                    singleLine = true
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onSynthesizeClick,
                    enabled = !uiState.isSynthesizing && uiState.text.isNotBlank()
                ) {
                    Text(text = if (uiState.isSynthesizing) "合成中" else "合成")
                }
                Button(
                    onClick = onPlayClick,
                    enabled = uiState.lastResult != null
                ) {
                    Text(text = "播放")
                }
            }

            uiState.lastResult?.let { result ->
                ResultSummary(result)
            }
            uiState.lastError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun PipelineSelector(
    selected: TtsPipelineType,
    onPipelineSelect: (TtsPipelineType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "链路", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TtsPipelineType.entries.forEach { type ->
                FilterChip(
                    selected = selected == type,
                    onClick = { onPipelineSelect(type) },
                    label = { Text(text = type.label) }
                )
            }
        }
        Text(
            text = selected.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ResultSummary(result: TtsResultSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "结果", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text(text = result.pipelineLabel) })
            AssistChip(onClick = {}, label = { Text(text = "${result.sampleRate}Hz") })
            AssistChip(onClick = {}, label = { Text(text = "${result.channels}ch") })
        }
        Text(
            text = "samples=${result.sampleCount}, elapsed=${result.elapsedMillis ?: 0}ms",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun TtsTestScreenPreview() {
    LocalAIEngineTesterTheme {
        TtsTestContent(
            uiState = TtsTestUiState(
                modelName = "kokoro-multi-lang-v1_1",
                modelPath = "/sdcard/Android/data/com.zure.localaienginetester/files/models/onnxruntime/kokoro-multi-lang-v1_1",
                lastResult = TtsResultSummary(
                    pipelineLabel = "sherpa-onnx",
                    sampleRate = 24000,
                    channels = 1,
                    sampleCount = 48000,
                    elapsedMillis = 320
                )
            ),
            onBackClick = {},
            onTextChange = {},
            onSpeakerIdChange = {},
            onSpeedChange = {},
            onPipelineSelect = {},
            onSynthesizeClick = {},
            onPlayClick = {}
        )
    }
}
