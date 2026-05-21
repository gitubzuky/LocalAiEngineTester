package com.zure.localaienginetester.domain.entity

// [Example] 参考示例，正式开发时去除

/**
 * 用户领域实体。纯 Kotlin 数据类，不依赖 Android 框架、Room 或 Retrofit。
 * 在 domain 层和 UI 层之间流通。
 */
data class User(
    val id: Int,
    val name: String,
    val email: String
)
