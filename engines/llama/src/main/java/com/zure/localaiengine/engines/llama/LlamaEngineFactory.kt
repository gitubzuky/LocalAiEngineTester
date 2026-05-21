package com.zure.localaiengine.engines.llama

import com.zure.localaiengine.core.engine.AIEngine
import com.zure.localaiengine.core.engine.EngineDescriptor
import com.zure.localaiengine.core.engine.EngineFactory
import com.zure.localaiengine.core.inference.InferenceTask
import com.zure.localaiengine.core.model.ModelFormat

class LlamaEngineFactory : EngineFactory {
    override val descriptor = EngineDescriptor(
        id = ENGINE_ID,
        name = "Llama.cpp",
        supportedFormats = setOf(ModelFormat.GGUF),
        supportedTasks = setOf(InferenceTask.TEXT_GENERATION),
        supportsStreaming = true
    )

    override fun create(): AIEngine = LlamaEngine(
        descriptor = descriptor,
        nativeBridge = LlamaNativeBridge()
    )

    companion object {
        const val ENGINE_ID = "llama"
    }
}
