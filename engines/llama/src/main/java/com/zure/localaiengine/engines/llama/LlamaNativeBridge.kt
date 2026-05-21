package com.zure.localaiengine.engines.llama

internal class LlamaNativeBridge {
    fun isRuntimeAvailable(): Boolean = LlamaLibraryLoader.ensureLoaded().isSuccess

    fun runtimeError(): Throwable? = LlamaLibraryLoader.ensureLoaded().exceptionOrNull()

    external fun loadModel(
        modelPath: String,
        contextSize: Int,
        threads: Int,
        gpuLayers: Int,
        seed: Int
    ): Long

    external fun generate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repetitionPenalty: Float,
        useChatTemplate: Boolean,
        stopSequences: Array<String>
    ): String

    external fun generateStream(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repetitionPenalty: Float,
        useChatTemplate: Boolean,
        stopSequences: Array<String>,
        callback: LlamaTokenCallback
    )

    external fun release(handle: Long)
}
