package com.zure.localaienginetester.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.zure.localaienginetester.data.local.dao.UserDao
import com.zure.localaienginetester.data.local.entity.UserEntity

// [Example] 参考示例，正式开发时去除（UserEntity、UserDao 部分）

/**
 * Room 数据库定义。
 * 正式开发时在此添加新的 Entity 和 DAO。
 */
@Database(
    entities = [UserEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}
