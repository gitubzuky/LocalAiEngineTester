package com.zure.localaiengine.engines.tflite

import android.util.Log
import com.zure.localaiengine.core.engine.AIEngine
import com.zure.localaiengine.core.engine.EngineConfig
import com.zure.localaiengine.core.engine.EngineDescriptor
import com.zure.localaiengine.core.inference.InferenceInput
import com.zure.localaiengine.core.inference.InferenceOutput
import com.zure.localaiengine.core.inference.InferenceRequest
import com.zure.localaiengine.core.inference.InferenceResult
import com.zure.localaiengine.core.inference.InferenceTask
import com.zure.localaiengine.core.model.ModelFormat
import java.io.File
import java.lang.reflect.Array as ReflectArray
import kotlin.system.measureTimeMillis
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

class TfLiteEngine(
    override val descriptor: EngineDescriptor
) : AIEngine {
    private var interpreter: Interpreter? = null
    private var inferenceLogCount = 0

    override suspend fun load(config: EngineConfig) {
        require(config.modelFormat == ModelFormat.TFLITE) {
            "TensorFlow Lite engine requires ${ModelFormat.TFLITE}, but got ${config.modelFormat}."
        }

        val modelFile = File(config.modelPath)
        require(modelFile.exists() && modelFile.isFile) {
            "TensorFlow Lite model file does not exist: ${config.modelPath}"
        }

        val options = Interpreter.Options().apply {
            config.options["numThreads"]?.toIntOrNull()?.let(::setNumThreads)
        }

        close()
        interpreter = Interpreter(modelFile, options)
        interpreter?.let { activeInterpreter ->
            Log.i(
                TAG,
                "loaded model=${modelFile.name} inputs=${activeInterpreter.inputTensorCount} " +
                    "outputs=${activeInterpreter.outputTensorCount}"
            )
            for (index in 0 until activeInterpreter.inputTensorCount) {
                val tensor = activeInterpreter.getInputTensor(index)
                Log.i(
                    TAG,
                    "model-input[$index] name=${tensor.name()} shape=${tensor.shape().contentToString()} " +
                        "dtype=${tensor.dataType()}"
                )
            }
            for (index in 0 until activeInterpreter.outputTensorCount) {
                val tensor = activeInterpreter.getOutputTensor(index)
                Log.i(
                    TAG,
                    "model-output[$index] name=${tensor.name()} shape=${tensor.shape().contentToString()} " +
                        "dtype=${tensor.dataType()}"
                )
            }
        }
    }

    override suspend fun infer(request: InferenceRequest): InferenceResult {
        val activeInterpreter = requireNotNull(interpreter) {
            "TensorFlow Lite model is not loaded."
        }
        require(request.task in descriptor.supportedTasks) {
            "TensorFlow Lite engine does not support task ${request.task}."
        }

        val tensorInputs = request.inputs.filterIsInstance<InferenceInput.Tensor>()
        require(tensorInputs.isNotEmpty()) {
            "TensorFlow Lite inference currently requires Tensor inputs. Image, audio, and text preprocessing should be handled before infer()."
        }

        val inputs = buildInputArray(activeInterpreter, tensorInputs)
        val outputs = buildOutputMap(activeInterpreter)
        inferenceLogCount += 1
        val shouldLog = shouldLog(inferenceLogCount)
        if (shouldLog) {
            tensorInputs.forEachIndexed { index, tensor ->
                val modelTensor = activeInterpreter.getInputTensor(index)
                Log.i(
                    TAG,
                    "infer[$inferenceLogCount]-input[$index] modelName=${modelTensor.name()} " +
                        "modelShape=${modelTensor.shape().contentToString()} modelType=${modelTensor.dataType()} " +
                        "requestName=${tensor.name} requestShape=${tensor.shape.contentToString()} " +
                        "data=${tensor.data.javaClass.simpleName} ${tensor.data.floatSummary()}"
                )
            }
        }

        val elapsedMillis = measureTimeMillis {
            activeInterpreter.runForMultipleInputsOutputs(inputs, outputs)
        }
        if (shouldLog) {
            outputs.forEach { (index, data) ->
                val tensor = activeInterpreter.getOutputTensor(index)
                Log.i(
                    TAG,
                    "infer[$inferenceLogCount]-output[$index] name=${tensor.name()} " +
                        "shape=${tensor.shape().contentToString()} type=${tensor.dataType()} " +
                        "elapsed=${elapsedMillis}ms ${data.floatSummary()}"
                )
            }
        }

        return InferenceResult(
            outputs = outputs.map { (index, data) ->
                val tensor = activeInterpreter.getOutputTensor(index)
                InferenceOutput.Tensor(
                    name = tensor.name(),
                    data = data,
                    shape = tensor.shape()
                )
            },
            elapsedMillis = elapsedMillis,
            metadata = mapOf("engineId" to descriptor.id)
        )
    }

    override suspend fun close() {
        interpreter?.close()
        interpreter = null
    }

    private fun buildInputArray(
        interpreter: Interpreter,
        tensorInputs: List<InferenceInput.Tensor>
    ): Array<Any> {
        return Array(interpreter.inputTensorCount) { index ->
            val inputTensor = interpreter.getInputTensor(index)
            val input = tensorInputs.firstOrNull { it.name == inputTensor.name() }
                ?: tensorInputs.getOrNull(index)
                ?: error("Missing TensorFlow Lite input tensor at index $index (${inputTensor.name()}).")
            input.data
        }
    }

    private fun buildOutputMap(interpreter: Interpreter): MutableMap<Int, Any> {
        return (0 until interpreter.outputTensorCount).associateWithTo(mutableMapOf()) { index ->
            val outputTensor = interpreter.getOutputTensor(index)
            createTensorBuffer(outputTensor.dataType(), outputTensor.shape())
        }
    }

    private fun createTensorBuffer(dataType: DataType, shape: IntArray): Any {
        val safeShape = shape.map { dimension -> dimension.coerceAtLeast(1) }.toIntArray()
        val componentType = when (dataType) {
            DataType.FLOAT32 -> Float::class.javaPrimitiveType
            DataType.INT32 -> Int::class.javaPrimitiveType
            DataType.UINT8 -> Byte::class.javaPrimitiveType
            DataType.INT64 -> Long::class.javaPrimitiveType
            DataType.BOOL -> Boolean::class.javaPrimitiveType
            else -> error("Unsupported TensorFlow Lite output data type: $dataType")
        } ?: error("Unsupported TensorFlow Lite output data type: $dataType")

        return if (safeShape.isEmpty()) {
            ReflectArray.newInstance(componentType, 1)
        } else {
            ReflectArray.newInstance(componentType, *safeShape)
        }
    }

    private fun shouldLog(count: Int): Boolean = count <= 5 || count % 30 == 0

    private fun Any.floatSummary(sampleSize: Int = 8): String {
        val values = flattenFloats(this)
        if (values.isEmpty()) return "count=0"
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        var sum = 0.0
        values.forEach { value ->
            min = minOf(min, value)
            max = maxOf(max, value)
            sum += value
        }
        val sample = values.take(sampleSize).joinToString(prefix = "[", postfix = "]") { "%.4f".format(it) }
        return "count=${values.size} min=$min max=$max mean=${sum / values.size} sample=$sample"
    }

    private fun flattenFloats(data: Any): FloatArray {
        val output = mutableListOf<Float>()
        appendFloats(data, output)
        return output.toFloatArray()
    }

    private fun appendFloats(value: Any?, output: MutableList<Float>) {
        when (value) {
            null -> Unit
            is FloatArray -> output.addAll(value.asIterable())
            is DoubleArray -> value.forEach { output.add(it.toFloat()) }
            is IntArray -> value.forEach { output.add(it.toFloat()) }
            is LongArray -> value.forEach { output.add(it.toFloat()) }
            is ByteArray -> value.forEach { output.add((it.toInt() and 0xFF).toFloat()) }
            is java.nio.ByteBuffer -> {
                val duplicate = value.duplicate().order(value.order())
                duplicate.rewind()
                if (duplicate.remaining() % Float.SIZE_BYTES == 0) {
                    repeat(duplicate.remaining() / Float.SIZE_BYTES) {
                        output.add(duplicate.float)
                    }
                }
            }
            is Array<*> -> value.forEach { appendFloats(it, output) }
            else -> if (value.javaClass.isArray) {
                for (index in 0 until ReflectArray.getLength(value)) {
                    appendFloats(ReflectArray.get(value, index), output)
                }
            }
        }
    }

    private companion object {
        const val TAG = "RTMPoseDebug"
    }
}
