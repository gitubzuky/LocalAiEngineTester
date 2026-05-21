package com.zure.localaienginetester.base

/**
 * 一次性 UI 事件的标记接口。
 * 用于 Snackbar、导航跳转等不需要在配置变更时重放的事件。
 * 所有 ViewModel 的 Event 密封类都必须实现此接口，
 * 且必须包含 `data class Error(val message: String)` 子类。
 */
interface UiEvent
