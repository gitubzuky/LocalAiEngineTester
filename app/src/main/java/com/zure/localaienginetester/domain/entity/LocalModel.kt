package com.zure.localaienginetester.domain.entity

import com.zure.localaiengine.core.model.ModelFormat

data class LocalModel(
    val id: String,
    val engineId: String,
    val name: String,
    val format: ModelFormat,
    val source: ModelSource,
    val path: String,
    val sizeBytes: Long? = null
)

enum class ModelSource {
    External,
    Assets
}
