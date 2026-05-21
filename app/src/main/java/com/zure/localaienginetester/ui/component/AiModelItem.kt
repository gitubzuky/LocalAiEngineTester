package com.zure.localaienginetester.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zure.localaienginetester.domain.entity.AiModel
import com.zure.localaienginetester.ui.theme.LocalAIEngineTesterTheme

@Composable
fun AiModelItem(
    aiModel: AiModel,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = aiModel.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = aiModel.type,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AssistChip(
                onClick = {},
                label = { Text(text = aiModel.type) }
            )
        }
    }
}

object AiModelMockData {
    val models = listOf(
        AiModel(id = 1, engineId = "llama", type = "LLM", name = "Qwen2.5-7B-Instruct"),
        AiModel(id = 2, engineId = "llama", type = "LLM", name = "Llama-3.1-8B-Instruct"),
        AiModel(id = 3, engineId = "llama", type = "LLM", name = "ChatGLM4-9B"),
        AiModel(id = 4, engineId = "tflite", type = "Vision", name = "Qwen2-VL-7B-Instruct"),
        AiModel(id = 5, engineId = "tflite", type = "Vision", name = "InternVL2-8B"),
        AiModel(id = 6, engineId = "tflite", type = "Audio", name = "Whisper-Large-V3"),
        AiModel(id = 7, engineId = "tflite", type = "Audio", name = "SenseVoice-Small"),
        AiModel(id = 8, engineId = "tflite", type = "Embedding", name = "BGE-M3"),
        AiModel(id = 9, engineId = "tflite", type = "Embedding", name = "GTE-Qwen2-1.5B-Instruct")
    )
    val sampleModel = AiModel(id = 1, engineId = "llama", type = "LLM", name = "Qwen2.5-7B-Instruct")
}

@Preview(showBackground = true)
@Composable
fun AiModelItemPreview() {
    LocalAIEngineTesterTheme {
        AiModelItem(aiModel = AiModelMockData.sampleModel)
    }
}
