package com.zure.localaiengine.core.engine

import com.zure.localaiengine.core.inference.InferenceTask

class EngineRegistry(
    factories: Set<EngineFactory>
) {
    private val factoriesById = factories.associateBy { it.descriptor.id }

    val availableEngines: List<EngineDescriptor>
        get() = factoriesById.values.map { it.descriptor }

    fun findEngines(task: InferenceTask): List<EngineDescriptor> {
        return availableEngines.filter { task in it.supportedTasks }
    }

    fun create(engineId: String): AIEngine {
        return requireNotNull(factoriesById[engineId]) {
            "AI engine $engineId is not registered."
        }.create()
    }
}
