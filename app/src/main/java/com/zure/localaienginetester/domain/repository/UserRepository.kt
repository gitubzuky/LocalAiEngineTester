package com.zure.localaienginetester.domain.repository

import com.zure.localaienginetester.domain.entity.User
import kotlinx.coroutines.flow.Flow

// [Example] 参考示例，正式开发时去除

/**
 * 用户仓库接口（抽象）。定义在 domain 层，由 data 层实现。
 * 返回 Flow，ViewModel 收集并映射为 StateFlow<UiState>。
 */
interface UserRepository {
    fun getUsers(): Flow<List<User>>
    fun getUserById(id: Int): Flow<User?>
    suspend fun refreshUsers()
}
