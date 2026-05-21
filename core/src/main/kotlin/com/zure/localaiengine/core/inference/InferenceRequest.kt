package com.zure.localaiengine.core.inference

data class InferenceRequest(
    val task: InferenceTask,
    val inputs: List<InferenceInput>,
    val parameters: InferenceParameters = InferenceParameters()
)
