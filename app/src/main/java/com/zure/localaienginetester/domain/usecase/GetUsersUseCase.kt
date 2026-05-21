package com.zure.localaienginetester.domain.usecase

import com.zure.localaienginetester.domain.entity.User
import com.zure.localaienginetester.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// [Example] 参考示例，正式开发时去除

/**
 * 获取用户列表用例。单一职责：一个用例 = 一个公开函数 = 一个业务操作。
 */
class GetUsersUseCase @Inject constructor(
    private val repository: UserRepository
) {
    operator fun invoke(): Flow<List<User>> = repository.getUsers()
}
