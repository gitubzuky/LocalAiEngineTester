package com.zure.localaienginetester.data.remote

import com.zure.localaienginetester.data.remote.dto.UserDto
import kotlinx.coroutines.flow.Flow

// [Example] 参考示例，正式开发时去除

/**
 * Retrofit API 接口。
 * 端点桩，正式开发时替换为真实 API 端点。
 */
interface ApiService {
    suspend fun getUsers(): List<UserDto>
    suspend fun getUserById(id: Int): UserDto
}
