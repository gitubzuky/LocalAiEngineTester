package com.zure.localaiengine.core.engine

interface EngineFactory {
    val descriptor: EngineDescriptor

    fun create(): AIEngine
}
