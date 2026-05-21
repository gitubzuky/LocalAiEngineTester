package com.zure.localaiengine.core.engine

import com.zure.localaiengine.core.inference.InferenceChunk
import com.zure.localaiengine.core.inference.InferenceRequest
import com.zure.localaiengine.core.inference.InferenceResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface AIEngine {
    val descriptor: EngineDescriptor

    suspend fun load(config: EngineConfig)

    suspend fun infer(request: InferenceRequest): InferenceResult

    fun stream(request: InferenceRequest): Flow<InferenceChunk> = flow {
        throw UnsupportedOperationException("${descriptor.id} does not support streaming inference.")
    }

    suspend fun close()
}
