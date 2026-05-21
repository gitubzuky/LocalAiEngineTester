package com.zure.localaienginetester.ui.screen

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zure.localaienginetester.base.UiState
import com.zure.localaienginetester.domain.entity.User
import com.zure.localaienginetester.ui.component.UserItem
import com.zure.localaienginetester.ui.component.UserMockData
import com.zure.localaienginetester.ui.theme.LocalAIEngineTesterTheme

// [Example] 参考示例，正式开发时去除

/**
 * 用户列表页面。遵循 Clean Architecture：
 * ViewModel 收集 Flow 并映射为 StateFlow<UiState>，UI 层仅消费不可变状态。
 */
@Composable
fun UsersScreen(
    viewModel: UsersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    UsersContent(uiState = uiState)
}

@Composable
private fun UsersContent(uiState: UiState<List<User>>) {
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
                items(uiState.data, key = { it.id }) { user ->
                    UserItem(user = user)
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

@Preview(showBackground = true)
@Composable
fun UsersScreenPreview() {
    LocalAIEngineTesterTheme {
        UsersContent(uiState = UiState.Success(listOf(UserMockData.sampleUser)))
    }
}
