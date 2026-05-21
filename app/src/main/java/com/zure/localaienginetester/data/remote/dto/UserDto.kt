package com.zure.localaienginetester.data.remote.dto

import kotlinx.serialization.Serializable

// [Example] 参考示例，正式开发时去除

/**
 * 用户 DTO，对应远程 API 的响应结构。
 * 仅在 data 层使用，不直接流向 UI。
 */
@Serializable
data class UserDto(
    val id: Int,
    val name: String,
    val email: String
)

/** DTO Mock 工厂，供 @Preview 和测试使用 */
object UserDtoMock {
    val sample = UserDto(id = 1, name = "Alice", email = "alice@example.com")
    val list = listOf(
        UserDto(id = 1, name = "Alice", email = "alice@example.com"),
        UserDto(id = 2, name = "Bob", email = "bob@example.com")
    )
}
