package com.zure.localaienginetester.base

/**
 * 页面状态密封接口，表示 UI 的四种可能状态。
 * 所有页面的 UiState 都应基于此接口扩展。
 */
sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
