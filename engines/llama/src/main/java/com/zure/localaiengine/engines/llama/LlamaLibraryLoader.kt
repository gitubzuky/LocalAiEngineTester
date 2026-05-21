package com.zure.localaiengine.engines.llama

internal object LlamaLibraryLoader {
    private var loadResult: Result<Unit>? = null

    fun ensureLoaded(): Result<Unit> {
        val existing = loadResult
        if (existing != null) return existing

        val result = runCatching {
            System.loadLibrary("llama_jni")
        }
        loadResult = result
        return result
    }
}
