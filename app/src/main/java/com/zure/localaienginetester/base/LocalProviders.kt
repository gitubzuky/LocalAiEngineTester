package com.zure.localaienginetester.base

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf

/**
 * 全局 CompositionLocal 定义。
 * 全局能力（SnackbarHostState、AnalyticsHelper 等）在根 Composable 提供，
 * 每个 Screen 用 LocalXxx.current 一行接入，不做 Base Composable 包裹。
 */
val LocalSnackbarHostState = compositionLocalOf { SnackbarHostState() }
// 按需扩展：
// val LocalAnalyticsHelper = compositionLocalOf<AnalyticsHelper> { ... }
// val LocalConnectivityObserver = compositionLocalOf<ConnectivityObserver> { ... }
