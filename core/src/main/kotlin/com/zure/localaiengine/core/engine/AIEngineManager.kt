package com.zure.localaiengine.core.engine

import com.zure.localaiengine.core.inference.InferenceChunk
import com.zure.localaiengine.core.inference.InferenceRequest
import com.zure.localaiengine.core.inference.InferenceResult
import com.zure.localaiengine.core.inference.InferenceTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AIEngineManager(
    private val registry: EngineRegistry
) {
    private val mutex = Mutex()
    @Volatile
    private var activeEngine: AIEngine? = null

    val availableEngines: List<EngineDescriptor>
        get() = registry.availableEngines

    val currentEngine: EngineDescriptor?
        get() = activeEngine?.descriptor

    fun findEngines(task: InferenceTask): List<EngineDescriptor> = registry.findEngines(task)

    suspend fun switchEngine(engineId: String, config: EngineConfig) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val nextEngine = registry.create(engineId)
                try {
                    nextEngine.load(config)

                    // Keep the previous engine alive until the replacement is fully loaded.
                    val previousEngine = activeEngine
                    activeEngine = nextEngine
                    previousEngine?.close()
                } catch (throwable: Throwable) {
                    nextEngine.close()
                    throw throwable
                }
            }
        }
    }

    suspend fun infer(request: InferenceRequest): InferenceResult {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val engine = activeEngine
                    ?: throw IllegalStateException("No AI engine is currently loaded.")
                ensureTaskSupported(engine, request.task)
                engine.infer(request)
            }
        }
    }

    fun stream(request: InferenceRequest): Flow<InferenceChunk> = flow {
        mutex.withLock {
            val engine = activeEngine
                ?: throw IllegalStateException("No AI engine is currently loaded.")
            ensureTaskSupported(engine, request.task)
            emitAll(engine.stream(request))
        }
    }

    suspend fun closeCurrentEngine() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                activeEngine?.close()
                activeEngine = null
            }
        }
    }

    private fun ensureTaskSupported(engine: AIEngine, task: InferenceTask) {
        require(task in engine.descriptor.supportedTasks) {
            "${engine.descriptor.id} does not support task $task."
        }
    }
}
