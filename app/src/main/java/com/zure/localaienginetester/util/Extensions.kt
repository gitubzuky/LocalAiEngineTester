package com.zure.localaienginetester.util

/**
 * 常用 Kotlin 扩展函数。
 * 正式开发时在此添加通用扩展。
 */

/**
 * 安全执行，返回 Result 包装。
 */
inline fun <T> runCatchingResult(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: Exception) {
        Result.failure(e)
    }
}
