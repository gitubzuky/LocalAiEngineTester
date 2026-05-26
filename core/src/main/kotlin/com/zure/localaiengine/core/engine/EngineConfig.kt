package com.zure.localaiengine.core.engine

import com.zure.localaiengine.core.model.ModelFormat

data class EngineConfig(
    val modelPath: String,
    val modelFormat: ModelFormat,
    val runtime: EngineRuntimeConfig = EngineRuntimeConfig(),
    val artifacts: Map<String, String> = emptyMap(),
    val options: Map<String, String> = emptyMap()
)

data class EngineRuntimeConfig(
    val contextSize: Int? = null,
    val batchSize: Int? = null,
    val microBatchSize: Int? = null,
    val threads: Int? = null,
    val gpuLayers: Int? = null,
    val seed: Int? = null
)

object EngineArtifactKeys {
    const val TOKENIZER = "tokenizer"
    const val TOKENS = "tokens"
    const val VOCAB = "vocab"
    const val VOICES = "voices"
    const val SPEAKER_EMBEDDINGS = "speakerEmbeddings"
    const val LEXICON = "lexicon"
    const val DATA_DIR = "dataDir"
    const val NORMALIZER = "normalizer"
    const val VOCODER = "vocoder"
}
