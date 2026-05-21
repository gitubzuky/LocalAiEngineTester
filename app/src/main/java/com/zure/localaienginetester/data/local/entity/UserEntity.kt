package com.zure.localaienginetester.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// [Example] 参考示例，正式开发时去除

/**
 * 用户 Room Entity，对应本地数据库表结构。
 * 仅在 data 层使用，不直接流向 UI。
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val email: String
)
