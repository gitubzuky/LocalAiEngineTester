package com.zure.localaienginetester.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * ViewModel 基类，提供共享的事件通道基础设施。
 * 具体 ViewModel 继承此类并自行管理 _uiState: MutableStateFlow。
 *
 * 约定：子类 Event 密封类必须包含 Error(message) 子类，
 * 以便 ObserveErrors() 扩展函数统一消费错误事件。
 */
abstract class BaseViewModel<Event : UiEvent, State : UiState<*>>(
    initialEvent: Event? = null
) : ViewModel() {

    private val _event = MutableSharedFlow<Event>()
    val event: SharedFlow<Event> = _event.asSharedFlow()

    protected fun sendEvent(event: Event) {
        viewModelScope.launch { _event.emit(event) }
    }
}
