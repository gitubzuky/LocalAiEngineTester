package com.zure.localaiengine.engines.llama

import com.zure.localaiengine.core.engine.AIEngine
import com.zure.localaiengine.core.engine.EngineConfig
import com.zure.localaiengine.core.engine.EngineDescriptor
import com.zure.localaiengine.core.inference.InferenceChunk
import com.zure.localaiengine.core.inference.InferenceInput
import com.zure.localaiengine.core.inference.InferenceOutput
import com.zure.localaiengine.core.inference.InferenceRequest
import com.zure.localaiengine.core.inference.InferenceResult
import com.zure.localaiengine.core.inference.InferenceTask
import com.zure.localaiengine.core.model.ModelFormat
import java.io.File
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal class LlamaEngine(
    override val descriptor: EngineDescriptor,
    private val nativeBridge: LlamaNativeBridge
) : AIEngine {
    private var handle: Long = 0L

    override suspend fun load(config: EngineConfig) {
        require(config.modelFormat == ModelFormat.GGUF) {
            "Llama engine requires ${ModelFormat.GGUF}, but got ${config.modelFormat}."
        }

        val modelFile = File(config.modelPath)
        require(modelFile.exists() && modelFile.isFile) {
            "GGUF model file does not exist: ${config.modelPath}"
        }
        require(modelFile.length() > MIN_GGUF_MODEL_BYTES) {
            "GGUF model file is too small (${modelFile.length()} bytes). " +
                "It may be an incomplete download or a Hugging Face LFS pointer: ${config.modelPath}"
        }

        if (!nativeBridge.isRuntimeAvailable()) {
            val cause = nativeBridge.runtimeError()
            throw IllegalStateException(
                "llama.cpp runtime is unavailable. Add llama.cpp submodule and build libllama_jni.so.",
                cause
            )
        }

        val loadConfig = LlamaLoadConfig.from(config)
        close()
        handle = nativeBridge.loadModel(
            modelPath = modelFile.absolutePath,
            contextSize = loadConfig.contextSize,
            batchSize = loadConfig.batchSize,
            microBatchSize = loadConfig.microBatchSize,
            threads = loadConfig.threads,
            gpuLayers = loadConfig.gpuLayers,
            seed = loadConfig.seed
        )
    }

    override suspend fun infer(request: InferenceRequest): InferenceResult {
        ensureLoaded()
        ensureTextGeneration(request)

        val prompt = request.promptText()
        val generationConfig = LlamaGenerationConfig.from(request.parameters)
        lateinit var text: String

        val elapsedMillis = measureTimeMillis {
            text = nativeBridge.generate(
                handle = handle,
                prompt = prompt,
                maxTokens = generationConfig.maxTokens,
                temperature = generationConfig.temperature,
                topP = generationConfig.topP,
                topK = generationConfig.topK,
                repetitionPenalty = generationConfig.repetitionPenalty,
                seed = generationConfig.seed,
                useChatTemplate = generationConfig.useChatTemplate,
                stopSequences = generationConfig.stopSequences
            )
        }

        return InferenceResult(
            outputs = listOf(InferenceOutput.Text(text)),
            elapsedMillis = elapsedMillis,
            metadata = mapOf("engineId" to descriptor.id)
        )
    }

    override fun stream(request: InferenceRequest): Flow<InferenceChunk> = callbackFlow {
        ensureLoaded()
        ensureTextGeneration(request)

        val generationConfig = LlamaGenerationConfig.from(request.parameters)
        var cancelled = false

        try {
            nativeBridge.generateStream(
                handle = handle,
                prompt = request.promptText(),
                maxTokens = generationConfig.maxTokens,
                temperature = generationConfig.temperature,
                topP = generationConfig.topP,
                topK = generationConfig.topK,
                repetitionPenalty = generationConfig.repetitionPenalty,
                seed = generationConfig.seed,
                useChatTemplate = generationConfig.useChatTemplate,
                stopSequences = generationConfig.stopSequences,
                callback = LlamaTokenCallback { token ->
                    if (cancelled) {
                        false
                    } else {
                        trySend(InferenceChunk(output = InferenceOutput.Text(token)))
                        true
                    }
                }
            )
            trySend(InferenceChunk(output = InferenceOutput.Text(""), isFinal = true))
            close()
        } catch (throwable: Throwable) {
            close(throwable)
        }

        awaitClose {
            cancelled = true
        }
    }

    override suspend fun close() {
        if (handle != 0L) {
            nativeBridge.release(handle)
            handle = 0L
        }
    }

    private fun ensureLoaded() {
        check(handle != 0L) {
            "Llama model is not loaded."
        }
    }

    private fun ensureTextGeneration(request: InferenceRequest) {
        require(request.task == InferenceTask.TEXT_GENERATION) {
            "Llama engine currently supports ${InferenceTask.TEXT_GENERATION}, but got ${request.task}."
        }
    }

    private fun InferenceRequest.promptText(): String {
        return inputs.filterIsInstance<InferenceInput.Text>().firstOrNull()?.text
            ?: throw IllegalArgumentException("Llama text generation requires a Text input prompt.")
    }

    companion object {
        private const val MIN_GGUF_MODEL_BYTES = 1024L * 1024L
    }
}
