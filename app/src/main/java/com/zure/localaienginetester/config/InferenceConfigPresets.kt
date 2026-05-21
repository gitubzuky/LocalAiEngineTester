package com.zure.localaienginetester.config

import com.zure.localaiengine.core.engine.EngineRuntimeConfig
import com.zure.localaiengine.core.inference.InferenceParameters

object InferenceConfigPresets {
    val llamaRuntime: EngineRuntimeConfig = EngineRuntimeConfig(
        contextSize = 1024,
        batchSize = 128,
        microBatchSize = 128,
        threads = 4,
        gpuLayers = 0
    )

    val textGeneration: InferenceParameters = InferenceParameters(
        maxTokens = 256,
        temperature = 0.8f,
        topP = 0.95f,
        topK = 40,
        repetitionPenalty = 1.0f,
        useChatTemplate = true
    )

    val conciseTextGeneration: InferenceParameters = textGeneration.copy(
        maxTokens = 128,
        temperature = 0.3f,
        topP = 0.8f,
        topK = 20,
        repetitionPenalty = 1.05f
    )
}
