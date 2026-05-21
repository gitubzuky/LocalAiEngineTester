package com.zure.localaienginetester.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.filterIsInstance

/**
 * BaseViewModel 的 Composable 扩展，统一消费 ErrorEvent。
 * 每个 Screen 一行接入：viewModel.ObserveErrors { message -> ... }
 *
 * 约定：ViewModel 的 Event 密封类必须包含 `data class Error(val message: String)` 子类。
 */
@Composable
fun BaseViewModel<*, *>.ObserveErrors(onError: (String) -> Unit) {
    val currentOnError by rememberUpdatedState(onError)
    LaunchedEffect(Unit) {
        event.filterIsInstance<ErrorEvent>().collect { event ->
            currentOnError(event.message)
        }
    }
}

/**
 * 错误事件的通用接口，用于 ObserveErrors() 扩展函数过滤。
 * ViewModel 的 Event 密封类中的 Error 子类需同时实现 UiEvent 和此接口。
 */
interface ErrorEvent {
    val message: String
}
