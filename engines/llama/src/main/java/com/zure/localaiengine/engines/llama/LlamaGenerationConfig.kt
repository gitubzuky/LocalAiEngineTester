package com.zure.localaiengine.engines.llama

import com.zure.localaiengine.core.engine.EngineConfig
import com.zure.localaiengine.core.inference.InferenceParameters

internal data class LlamaLoadConfig(
    val contextSize: Int,
    val threads: Int,
    val gpuLayers: Int,
    val seed: Int
) {
    companion object {
        fun from(config: EngineConfig): LlamaLoadConfig {
            return LlamaLoadConfig(
                contextSize = config.runtime.contextSize
                    ?: config.options["contextSize"]?.toIntOrNull()
                    ?: 2048,
                threads = config.runtime.threads
                    ?: config.options["threads"]?.toIntOrNull()
                    ?: Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
                gpuLayers = config.runtime.gpuLayers
                    ?: config.options["gpuLayers"]?.toIntOrNull()
                    ?: 0,
                seed = config.runtime.seed
                    ?: config.options["seed"]?.toIntOrNull()
                    ?: -1
            )
        }
    }
}

internal data class LlamaGenerationConfig(
    val maxTokens: Int,
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val repetitionPenalty: Float,
    val useChatTemplate: Boolean,
    val stopSequences: Array<String>
) {
    companion object {
        fun from(parameters: InferenceParameters): LlamaGenerationConfig {
            return LlamaGenerationConfig(
                maxTokens = parameters.maxTokens ?: 256,
                temperature = parameters.temperature ?: 0.8f,
                topP = parameters.topP ?: 0.95f,
                topK = parameters.topK ?: 40,
                repetitionPenalty = parameters.extras["repetitionPenalty"]?.toFloatOrNull() ?: 1.0f,
                useChatTemplate = parameters.extras["useChatTemplate"]?.toBooleanStrictOrNull() ?: true,
                stopSequences = parameters.stopSequences.toTypedArray()
            )
        }
    }
}
