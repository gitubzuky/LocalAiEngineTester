package com.zure.localaienginetester.data.mappers

import com.zure.localaienginetester.data.local.entity.UserEntity
import com.zure.localaienginetester.data.remote.dto.UserDto
import com.zure.localaienginetester.domain.entity.User

// [Example] 参考示例，正式开发时去除

/**
 * 三层数据流映射器，每层边界显式映射：DTO ↔ Domain ↔ Entity。
 * 使用扩展函数实现，绝不让 DTO 直接流向 UI。
 */

// DTO → Domain Entity
fun UserDto.toDomain() = User(id = id, name = name, email = email)

// Domain Entity → Room Entity
fun User.toEntity() = UserEntity(id = id, name = name, email = email)

// Room Entity → Domain Entity
fun UserEntity.toDomain() = User(id = id, name = name, email = email)

// 批量映射
fun List<UserDto>.toDomainList() = map { it.toDomain() }
fun List<UserEntity>.toDomainEntityList() = map { it.toDomain() }
fun List<User>.toEntityList() = map { it.toEntity() }
