package com.zure.localaiengine.core.tts

import com.zure.localaiengine.core.engine.AIEngineManager
import com.zure.localaiengine.core.inference.InferenceInput
import com.zure.localaiengine.core.inference.InferenceOutput
import com.zure.localaiengine.core.inference.InferenceParameters
import com.zure.localaiengine.core.inference.InferenceRequest
import com.zure.localaiengine.core.inference.InferenceTask

class EngineTtsSynthesisPipeline(
    override val id: TtsPipelineId,
    override val displayName: String,
    private val engineManager: AIEngineManager
) : TtsSynthesisPipeline {

    override suspend fun synthesize(request: TtsRequest): TtsResult {
        val result = engineManager.infer(
            InferenceRequest(
                task = InferenceTask.TEXT_TO_SPEECH,
                inputs = listOf(InferenceInput.Text(request.text)),
                parameters = InferenceParameters(extras = request.toExtras())
            )
        )
        val audio = result.outputs.filterIsInstance<InferenceOutput.Audio>().firstOrNull()
            ?: error("$displayName did not return audio output.")
        return TtsResult(
            samples = audio.samples,
            sampleRate = audio.sampleRate,
            channels = audio.channels,
            elapsedMillis = result.elapsedMillis,
            metadata = result.metadata
        )
    }
}
