package com.zure.localaiengine.core.engine

import com.zure.localaiengine.core.model.ModelFormat

data class EngineConfig(
    val modelPath: String,
    val modelFormat: ModelFormat,
    val runtime: EngineRuntimeConfig = EngineRuntimeConfig(),
    val options: Map<String, String> = emptyMap()
)

data class EngineRuntimeConfig(
    val contextSize: Int? = null,
    val batchSize: Int? = null,
    val microBatchSize: Int? = null,
    val threads: Int? = null,
    val gpuLayers: Int? = null,
    val seed: Int? = null
)
