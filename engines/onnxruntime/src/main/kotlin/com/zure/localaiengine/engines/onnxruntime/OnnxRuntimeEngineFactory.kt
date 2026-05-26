package com.zure.localaiengine.engines.onnxruntime

import ai.onnxruntime.NodeInfo
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.util.Log
import com.zure.localaiengine.core.engine.AIEngine
import com.zure.localaiengine.core.engine.EngineConfig
import com.zure.localaiengine.core.engine.EngineDescriptor
import com.zure.localaiengine.core.engine.EngineFactory
import com.zure.localaiengine.core.inference.InferenceInput
import com.zure.localaiengine.core.inference.InferenceOutput
import com.zure.localaiengine.core.inference.InferenceRequest
import com.zure.localaiengine.core.inference.InferenceResult
import com.zure.localaiengine.core.inference.InferenceTask
import com.zure.localaiengine.core.model.ModelFormat
import java.io.File
import java.lang.reflect.Array as ReflectArray
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.DoubleBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.nio.ShortBuffer
import kotlin.system.measureTimeMillis

class OnnxRuntimeEngineFactory : EngineFactory {
    override val descriptor = EngineDescriptor(
        id = ENGINE_ID,
        name = "ONNX Runtime",
        supportedFormats = setOf(ModelFormat.ONNX),
        supportedTasks = setOf(
            InferenceTask.IMAGE_CLASSIFICATION,
            InferenceTask.OBJECT_DETECTION,
            InferenceTask.IMAGE_SEGMENTATION,
            InferenceTask.OCR,
            InferenceTask.TEXT_EMBEDDING,
            InferenceTask.TENSOR
        ),
        supportsStreaming = false
    )

    override fun create(): AIEngine = OnnxRuntimeEngine(descriptor)

    companion object {
        const val ENGINE_ID = "onnxruntime"
    }
}

private class OnnxRuntimeEngine(
    override val descriptor: EngineDescriptor
) : AIEngine {
    private var environment: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var sessionOptions: OrtSession.SessionOptions? = null

    override suspend fun load(config: EngineConfig) {
        require(config.modelFormat == ModelFormat.ONNX) {
            "ONNX Runtime engine requires ${ModelFormat.ONNX}, but got ${config.modelFormat}."
        }

        val modelFile = File(config.modelPath)
        require(modelFile.exists() && modelFile.isFile) {
            "ONNX model file does not exist: ${config.modelPath}"
        }

        val nextEnvironment = OrtEnvironment.getEnvironment()
        val nextOptions = buildSessionOptions(config)
        try {
            val nextSession = nextEnvironment.createSession(modelFile.absolutePath, nextOptions)
            close()
            environment = nextEnvironment
            sessionOptions = nextOptions
            session = nextSession
            logSession(modelFile, nextSession)
        } catch (throwable: Throwable) {
            nextOptions.close()
            throw throwable
        }
    }

    override suspend fun infer(request: InferenceRequest): InferenceResult {
        val activeEnvironment = requireNotNull(environment) {
            "ONNX Runtime environment is not loaded."
        }
        val activeSession = requireNotNull(session) {
            "ONNX Runtime model is not loaded."
        }
        require(request.task in descriptor.supportedTasks) {
            "ONNX Runtime engine does not support task ${request.task}."
        }

        val tensorInputs = request.inputs.filterIsInstance<InferenceInput.Tensor>()
        require(tensorInputs.isNotEmpty()) {
            "ONNX Runtime inference currently requires Tensor inputs. Text, image, and audio preprocessing should be handled before infer()."
        }

        val inputInfo = activeSession.inputInfo
        val inputTensors = buildInputTensors(activeEnvironment, activeSession.inputNames.toList(), inputInfo, tensorInputs)
        val elapsedHolder = longArrayOf(0L)
        val outputs = inputTensors.useAll { feeds ->
            lateinit var copiedOutputs: List<InferenceOutput.Tensor>
            elapsedHolder[0] = measureTimeMillis {
                activeSession.run(feeds).use { result ->
                    copiedOutputs = result.map { entry ->
                        entry.value.toTensorOutput(entry.key)
                    }
                }
            }
            copiedOutputs
        }

        return InferenceResult(
            outputs = outputs,
            elapsedMillis = elapsedHolder[0],
            metadata = mapOf("engineId" to descriptor.id)
        )
    }

    override suspend fun close() {
        session?.close()
        session = null
        sessionOptions?.close()
        sessionOptions = null
        environment = null
    }

    private fun buildSessionOptions(config: EngineConfig): OrtSession.SessionOptions {
        return OrtSession.SessionOptions().apply {
            (config.runtime.threads ?: config.options["numThreads"]?.toIntOrNull())
                ?.takeIf { it > 0 }
                ?.let(::setIntraOpNumThreads)
            config.options["interOpNumThreads"]
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?.let(::setInterOpNumThreads)
            config.options["optimizationLevel"]
                ?.toOptLevel()
                ?.let(::setOptimizationLevel)
            if (config.options["useNNAPI"]?.toBooleanStrictOrNull() == true) {
                addNnapi()
            }
        }
    }

    private fun buildInputTensors(
        environment: OrtEnvironment,
        inputNames: List<String>,
        inputInfo: Map<String, NodeInfo>,
        tensorInputs: List<InferenceInput.Tensor>
    ): Map<String, OnnxTensor> {
        return inputNames.associateWith { inputName ->
            val input = tensorInputs.firstOrNull { it.name == inputName }
                ?: tensorInputs.getOrNull(inputNames.indexOf(inputName))
                ?: error("Missing ONNX input tensor: $inputName.")
            val tensorInfo = inputInfo[inputName]?.info as? TensorInfo
            input.toOnnxTensor(environment, tensorInfo)
        }
    }

    private fun InferenceInput.Tensor.toOnnxTensor(
        environment: OrtEnvironment,
        tensorInfo: TensorInfo?
    ): OnnxTensor {
        val shape = resolveShape(tensorInfo)
        val nativeByteOrder = ByteOrder.nativeOrder()
        return when (val value = data) {
            is FloatArray -> OnnxTensor.createTensor(environment, FloatBuffer.wrap(value), shape)
            is DoubleArray -> OnnxTensor.createTensor(environment, DoubleBuffer.wrap(value), shape)
            is IntArray -> OnnxTensor.createTensor(environment, IntBuffer.wrap(value), shape)
            is LongArray -> OnnxTensor.createTensor(environment, LongBuffer.wrap(value), shape)
            is ShortArray -> OnnxTensor.createTensor(environment, ShortBuffer.wrap(value), shape)
            is ByteArray -> {
                val buffer = ByteBuffer.allocateDirect(value.size).order(nativeByteOrder)
                buffer.put(value)
                buffer.rewind()
                OnnxTensor.createTensor(environment, buffer, shape, tensorInfo?.type ?: OnnxJavaType.INT8)
            }
            is FloatBuffer -> OnnxTensor.createTensor(environment, value.duplicate(), shape)
            is DoubleBuffer -> OnnxTensor.createTensor(environment, value.duplicate(), shape)
            is IntBuffer -> OnnxTensor.createTensor(environment, value.duplicate(), shape)
            is LongBuffer -> OnnxTensor.createTensor(environment, value.duplicate(), shape)
            is ShortBuffer -> OnnxTensor.createTensor(environment, value.duplicate(), shape)
            is ByteBuffer -> OnnxTensor.createTensor(
                environment,
                value.duplicate().order(nativeByteOrder),
                shape,
                tensorInfo?.type ?: OnnxJavaType.INT8
            )
            is Array<*> -> if (value.isArrayOf<String>() && shape.isNotEmpty()) {
                @Suppress("UNCHECKED_CAST")
                OnnxTensor.createTensor(environment, value as Array<String>, shape)
            } else {
                OnnxTensor.createTensor(environment, value)
            }
            else -> OnnxTensor.createTensor(environment, value)
        }
    }

    private fun InferenceInput.Tensor.resolveShape(tensorInfo: TensorInfo?): LongArray {
        if (shape.isNotEmpty()) return shape.map { it.toLong() }.toLongArray()
        val modelShape = tensorInfo?.shape
            ?.takeIf { candidate -> candidate.all { it > 0 } }
        if (modelShape != null) return modelShape

        val elementCount = data.elementCount()
        return if (elementCount > 0) longArrayOf(elementCount.toLong()) else longArrayOf()
    }

    private fun OnnxValue.toTensorOutput(name: String): InferenceOutput.Tensor {
        val tensor = this as? OnnxTensor
            ?: error("ONNX Runtime output '$name' is not a tensor: $info.")
        val tensorInfo = tensor.info as TensorInfo
        return InferenceOutput.Tensor(
            name = name,
            data = tensor.value.deepCopy(),
            shape = tensorInfo.shape.map { it.toInt() }.toIntArray()
        )
    }

    private fun Any?.deepCopy(): Any {
        return when (this) {
            null -> Unit
            is FloatArray -> copyOf()
            is DoubleArray -> copyOf()
            is IntArray -> copyOf()
            is LongArray -> copyOf()
            is ShortArray -> copyOf()
            is ByteArray -> copyOf()
            is BooleanArray -> copyOf()
            is Array<*> -> Array(size) { index -> this[index].deepCopy() }
            else -> this
        }
    }

    private fun Any.elementCount(): Int {
        return when (this) {
            is FloatArray -> size
            is DoubleArray -> size
            is IntArray -> size
            is LongArray -> size
            is ShortArray -> size
            is ByteArray -> size
            is BooleanArray -> size
            is FloatBuffer -> remaining()
            is DoubleBuffer -> remaining()
            is IntBuffer -> remaining()
            is LongBuffer -> remaining()
            is ShortBuffer -> remaining()
            is ByteBuffer -> remaining()
            is Array<*> -> size
            else -> if (javaClass.isArray) ReflectArray.getLength(this) else 1
        }
    }

    private inline fun <R> Map<String, OnnxTensor>.useAll(block: (Map<String, OnnxTensor>) -> R): R {
        try {
            return block(this)
        } finally {
            values.forEach { it.close() }
        }
    }

    private fun String.toOptLevel(): OrtSession.SessionOptions.OptLevel? {
        return when (lowercase()) {
            "basic", "basic_opt" -> OrtSession.SessionOptions.OptLevel.BASIC_OPT
            "extended", "extended_opt" -> OrtSession.SessionOptions.OptLevel.EXTENDED_OPT
            "all", "all_opt" -> OrtSession.SessionOptions.OptLevel.ALL_OPT
            "layout", "layout_opt" -> OrtSession.SessionOptions.OptLevel.LAYOUT_OPT
            else -> null
        }
    }

    private fun logSession(modelFile: File, session: OrtSession) {
        Log.i(
            TAG,
            "loaded model=${modelFile.name} inputs=${session.numInputs} outputs=${session.numOutputs}"
        )
        session.inputInfo.forEach { (name, nodeInfo) ->
            Log.i(TAG, "model-input name=$name info=${nodeInfo.info}")
        }
        session.outputInfo.forEach { (name, nodeInfo) ->
            Log.i(TAG, "model-output name=$name info=${nodeInfo.info}")
        }
    }

    private companion object {
        const val TAG = "OnnxRuntimeEngine"
    }
}
