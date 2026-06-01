package com.zure.localaienginetester.ui.screen.model

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.zure.localaiengine.core.engine.EngineDescriptor
import com.zure.localaiengine.core.inference.InferenceTask
import com.zure.localaiengine.core.model.ModelFormat
import com.zure.localaienginetester.base.UiState
import com.zure.localaienginetester.domain.entity.LocalModel
import com.zure.localaienginetester.domain.entity.ModelSource
import com.zure.localaienginetester.navigation.Route
import com.zure.localaienginetester.ui.component.AppScaffold
import com.zure.localaienginetester.ui.theme.LocalAIEngineTesterTheme

@Composable
fun ModelListScreen(
    navController: NavController,
    viewModel: ModelListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                is ModelListEvent.CameraPoseReady -> {
                    navController.navigate(Route.CameraPoseTest(event.modelName))
                }
                is ModelListEvent.ModelReady -> {
                    navController.navigate(Route.TranslationTest(event.modelName))
                }
                is ModelListEvent.TtsReady -> {
                    navController.navigate(Route.TtsTest(event.modelName, event.modelPath))
                }
                is ModelListEvent.Error -> Unit
            }
        }
    }

    ModelListContent(
        uiState = uiState,
        onBackClick = { navController.popBackStack() },
        onModelClick = viewModel::loadSelectedModel
    )
}

@Composable
private fun ModelListContent(
    uiState: UiState<ModelListUiData>,
    onBackClick: () -> Unit,
    onModelClick: (LocalModel) -> Unit
) {
    AppScaffold(
        title = "模型列表",
        onBackClick = onBackClick
    ) {
        when (uiState) {
            is UiState.Idle,
            is UiState.Loading -> CenterContent {
                CircularProgressIndicator()
            }

            is UiState.Error -> CenterContent {
                Text(text = uiState.message)
            }

            is UiState.Success -> {
                val loadingModelId = uiState.data.loadingModelId
                if (uiState.data.models.isEmpty()) {
                    CenterContent {
                        EmptyModelMessage(uiState.data)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        uiState.data.errorMessage?.let { message ->
                            item {
                                Text(
                                    text = message,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        loadingModelId?.let {
                            item {
                                LoadingModelMessage()
                            }
                        }
                        items(uiState.data.models, key = { it.id }) { model ->
                            ModelItem(
                                model = model,
                                isLoading = loadingModelId == model.id,
                                isEnabled = loadingModelId == null,
                                onClick = { onModelClick(model) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyModelMessage(data: ModelListUiData) {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "未发现 ${data.engine.name} 支持的模型")
        data.externalDirectoryPath?.let { path ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "请将模型放到：\n$path",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        data.errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ModelItem(
    model: LocalModel,
    isLoading: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(enabled = isEnabled, onClick = onClick)
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
                    text = model.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = model.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isLoading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                    Text(text = "加载中...")
                }
            } else {
                AssistChip(
                    onClick = onClick,
                    enabled = isEnabled,
                    label = { Text(text = "${model.source.name} ${model.format.name}") }
                )
            }
        }
    }
}

@Composable
private fun LoadingModelMessage() {
    Text(
        text = "正在加载模型，请稍候",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun CenterContent(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Preview(showBackground = true)
@Composable
fun ModelListScreenPreview() {
    val engine = EngineDescriptor(
        id = "llama",
        name = "Llama.cpp",
        supportedFormats = setOf(ModelFormat.GGUF),
        supportedTasks = setOf(InferenceTask.TEXT_GENERATION),
        supportsStreaming = true
    )
    LocalAIEngineTesterTheme {
        ModelListContent(
            uiState = UiState.Success(
                ModelListUiData(
                    engine = engine,
                    externalDirectoryPath = "/sdcard/Android/data/com.zure.localaienginetester/files/models/llama",
                    models = listOf(
                        LocalModel(
                            id = "sample",
                            engineId = "llama",
                            name = "Hy-MT1.5-1.8B-1.25bit-GGUF.gguf",
                            format = ModelFormat.GGUF,
                            source = ModelSource.External,
                            path = "/sdcard/Android/data/com.zure.localaienginetester/files/models/llama/Hy-MT1.5-1.8B-1.25bit-GGUF.gguf"
                        )
                    )
                )
            ),
            onBackClick = {},
            onModelClick = {}
        )
    }
}
