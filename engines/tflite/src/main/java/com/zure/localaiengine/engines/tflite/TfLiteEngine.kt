package com.zure.localaiengine.engines.tflite

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

        val elapsedMillis = measureTimeMillis {
            activeInterpreter.runForMultipleInputsOutputs(inputs, outputs)
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
}
