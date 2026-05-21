package com.zure.localaiengine.core.engine

import com.zure.localaiengine.core.inference.InferenceTask
import com.zure.localaiengine.core.model.ModelFormat

data class EngineDescriptor(
    val id: String,
    val name: String,
    val supportedFormats: Set<ModelFormat>,
    val supportedTasks: Set<InferenceTask>,
    val supportsStreaming: Boolean = false
)
