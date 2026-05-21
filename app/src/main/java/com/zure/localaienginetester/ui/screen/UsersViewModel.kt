package com.zure.localaienginetester.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zure.localaienginetester.base.BaseViewModel
import com.zure.localaienginetester.base.ErrorEvent
import com.zure.localaienginetester.base.UiEvent
import com.zure.localaienginetester.base.UiState
import com.zure.localaienginetester.domain.entity.User
import com.zure.localaienginetester.domain.usecase.GetUsersUseCase
import com.zure.localaienginetester.util.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// [Example] 参考示例，正式开发时去除

/**
 * 用户列表 ViewModel。继承 BaseViewModel，自行管理 _uiState。
 * _uiState 是 private MutableStateFlow，uiState 是 public StateFlow —— 禁止向 UI 暴露可变流。
 */
@HiltViewModel
class UsersViewModel @Inject constructor(
    private val getUsersUseCase: GetUsersUseCase
) : BaseViewModel<UsersEvent, UiState<List<User>>>() {

    private final val TAG = "UsersViewModel"

    private val _uiState = MutableStateFlow<UiState<List<User>>>(UiState.Idle)
    val uiState: StateFlow<UiState<List<User>>> = _uiState.asStateFlow()

    init {
        AppLog.d(TAG, "init");
        loadUsers()
    }

    private fun loadUsers() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            getUsersUseCase().collect { users ->
                AppLog.d(TAG, users.toString());
                _uiState.value = UiState.Success(users)
            }
        }
    }
}

/**
 * 用户列表事件。Error 子类实现 ErrorEvent 接口，
 * 以便 ObserveErrors() 扩展函数统一消费。
 */
sealed class UsersEvent : UiEvent {
    data class Error(override val message: String) : UsersEvent(), ErrorEvent
}
