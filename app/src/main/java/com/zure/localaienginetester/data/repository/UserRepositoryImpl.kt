package com.zure.localaienginetester.data.repository

import com.zure.localaienginetester.data.local.dao.UserDao
import com.zure.localaienginetester.data.local.entity.UserEntity
import com.zure.localaienginetester.data.mappers.toDomainList
import com.zure.localaienginetester.data.mappers.toDomainEntityList
import com.zure.localaienginetester.data.mappers.toEntityList
import com.zure.localaienginetester.data.mappers.toDomain
import com.zure.localaienginetester.data.remote.ApiService
import com.zure.localaienginetester.data.remote.dto.UserDto
import com.zure.localaienginetester.data.remote.dto.UserDtoMock
import com.zure.localaienginetester.domain.entity.User
import com.zure.localaienginetester.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject

// [Example] 参考示例，正式开发时去除

/**
 * 用户仓库实现。组合本地 + 远程数据源，返回 Flow。
 * 策略：先返回本地缓存，再从远程刷新。
 */
class UserRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val userDao: UserDao
) : UserRepository {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun getUsers(): Flow<List<User>> {
        // 数据库为空时自动填充 Mock 数据，Room Flow 重发后进入 else 分支
        return userDao.getAllUsers().transformLatest { entities ->
            if (entities.isEmpty()) {
//                userDao.insertAll(mockUserEntities)
                emit(UserDtoMock.list.toDomainList())
            } else {
                emit(entities.toDomainEntityList())
            }
        }
    }

    override fun getUserById(id: Int): Flow<User?> {
        return userDao.getUserById(id).map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun refreshUsers() {
        val users = apiService.getUsers()
        userDao.insertAll(users.toDomainList().toEntityList())
    }
}
