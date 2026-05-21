package com.zure.localaienginetester.ui.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.zure.localaienginetester.R
import com.zure.localaienginetester.base.UiState
import com.zure.localaienginetester.domain.entity.AiModel
import com.zure.localaienginetester.navigation.Route
import com.zure.localaienginetester.ui.component.AiModelItem
import com.zure.localaienginetester.ui.component.AiModelMockData
import com.zure.localaienginetester.ui.component.AppScaffold
import com.zure.localaienginetester.ui.theme.LocalAIEngineTesterTheme

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeContent(
        uiState = uiState,
        onEngineClick = { model ->
            navController.navigate(Route.ModelList(model.engineId))
        }
    )
}

@Composable
private fun HomeContent(
    uiState: UiState<List<AiModel>>,
    onEngineClick: (AiModel) -> Unit
) {
    AppScaffold(title = stringResource(R.string.home_title)) {
        when (uiState) {
            is UiState.Idle,
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Success -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.data, key = { it.id }) { model ->
                        AiModelItem(
                            aiModel = model,
                            onClick = { onEngineClick(model) }
                        )
                    }
                }
            }

            is UiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = uiState.message)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    LocalAIEngineTesterTheme {
        HomeContent(
            uiState = UiState.Success(AiModelMockData.models),
            onEngineClick = {}
        )
    }
}
