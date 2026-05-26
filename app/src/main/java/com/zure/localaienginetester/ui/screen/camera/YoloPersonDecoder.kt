package com.zure.localaienginetester.ui.screen.camera

import com.zure.localaiengine.camera.analysis.api.FrameTransform
import com.zure.localaiengine.core.inference.InferenceOutput
import com.zure.localaiengine.core.vision.BoundingBox
import com.zure.localaiengine.core.vision.Detection
import com.zure.localaienginetester.util.AppLog
import java.lang.reflect.Array as ReflectArray
import java.nio.ByteBuffer

class YoloPersonDecoder(
    private val scoreThreshold: Float = 0.25f,
    private val nmsThreshold: Float = 0.45f,
    private val personClassIndex: Int = 0
) {
    private var decodeCount = 0

    fun decode(
        outputs: List<InferenceOutput>,
        transform: FrameTransform
    ): List<Detection> {
        decodeCount += 1
        val tensors = outputs.filterIsInstance<InferenceOutput.Tensor>()
        if (shouldLog(decodeCount)) {
            AppLog.i(
                TAG,
                "yolo-decode[$decodeCount] outputs=${tensors.tensorSummary()} " +
                    "scoreThreshold=$scoreThreshold nmsThreshold=$nmsThreshold transform=$transform"
            )
        }
        val decoded = tensors
            .asSequence()
            .flatMap { tensor -> tensor.decodeTensor(transform).asSequence() }
            .toList()
        val selected = nms(decoded.sortedByDescending { it.score }).take(1)
        if (shouldLog(decodeCount)) {
            AppLog.i(
                TAG,
                "yolo-decode[$decodeCount] candidates=${decoded.size} nms=${selected.size} " +
                    "best=${selected.firstOrNull()?.summary() ?: "none"}"
            )
        }
        if (decoded.isEmpty() && tensors.isNotEmpty()) {
            AppLog.w(
                TAG,
                "yolo-decode[$decodeCount] no detections decoded. outputs=${tensors.tensorSummary()}"
            )
        }
        return selected
    }

    private fun InferenceOutput.Tensor.decodeTensor(transform: FrameTransform): List<Detection> {
        val shape = shape
        val values = data.flattenFloats()
        if (shape.size < 3 || values.isEmpty()) return emptyList()
        val dimA = shape[shape.lastIndex - 1]
        val dimB = shape[shape.lastIndex - 2]
        return when {
            dimA in 5..256 -> decodeRows(
                values = values,
                rowCount = dimB,
                channelCount = dimA,
                channelFirst = false,
                transform = transform
            )
            dimB in 5..256 -> decodeRows(
                values = values,
                rowCount = dimA,
                channelCount = dimB,
                channelFirst = true,
                transform = transform
            )
            else -> emptyList()
        }
    }

    private fun decodeRows(
        values: FloatArray,
        rowCount: Int,
        channelCount: Int,
        channelFirst: Boolean,
        transform: FrameTransform
    ): List<Detection> {
        var passed = 0
        val detections = buildList {
            for (rowIndex in 0 until rowCount) {
                val row = FloatArray(channelCount) { channelIndex ->
                    if (channelFirst) {
                        values.getOrElse(channelIndex * rowCount + rowIndex) { 0f }
                    } else {
                        values.getOrElse(rowIndex * channelCount + channelIndex) { 0f }
                    }
                }
                val detection = row.toDetection(transform) ?: continue
                passed += 1
                add(detection)
            }
        }
        if (shouldLog(decodeCount)) {
            AppLog.i(
                TAG,
                "yolo-decode[$decodeCount] rows=$rowCount channels=$channelCount " +
                    "channelFirst=$channelFirst passed=$passed"
            )
        }
        return detections
    }

    private fun FloatArray.toDetection(transform: FrameTransform): Detection? {
        if (size < 5) return null
        val score = when {
            size == 5 -> this[4]
            size == 6 && this[5].toInt() != personClassIndex -> return null
            size == 6 -> this[4]
            else -> getOrNull(4 + personClassIndex) ?: return null
        }
        if (score < scoreThreshold) return null
        val box = when {
            size == 6 -> xyxyToSourceBox(transform)
            else -> xywhToSourceBox(transform)
        }
        if (box.area() <= 1f) return null
        return Detection(
            label = "person",
            score = score,
            boundingBox = box
        )
    }

    private fun FloatArray.xywhToSourceBox(transform: FrameTransform): BoundingBox {
        val scale = coordinateScale(transform)
        val centerX = this[0] * scale
        val centerY = this[1] * scale
        val width = this[2] * scale
        val height = this[3] * scale
        return modelBoxToSource(
            left = centerX - width / 2f,
            top = centerY - height / 2f,
            right = centerX + width / 2f,
            bottom = centerY + height / 2f,
            transform = transform
        )
    }

    private fun FloatArray.xyxyToSourceBox(transform: FrameTransform): BoundingBox {
        val scale = coordinateScale(transform)
        return modelBoxToSource(
            left = this[0] * scale,
            top = this[1] * scale,
            right = this[2] * scale,
            bottom = this[3] * scale,
            transform = transform
        )
    }

    private fun FloatArray.coordinateScale(transform: FrameTransform): Float {
        val maxCoordinate = take(4).maxOrNull() ?: 0f
        return if (maxCoordinate <= 2f) transform.modelInputWidth.toFloat() else 1f
    }

    private fun modelBoxToSource(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        transform: FrameTransform
    ): BoundingBox {
        val sourceLeft = (left - transform.padLeft) / transform.scaleX + transform.cropLeft
        val sourceTop = (top - transform.padTop) / transform.scaleY + transform.cropTop
        val sourceRight = (right - transform.padLeft) / transform.scaleX + transform.cropLeft
        val sourceBottom = (bottom - transform.padTop) / transform.scaleY + transform.cropTop
        return BoundingBox(
            left = minOf(sourceLeft, sourceRight).coerceIn(transform.cropLeft, transform.cropLeft + transform.cropWidth),
            top = minOf(sourceTop, sourceBottom).coerceIn(transform.cropTop, transform.cropTop + transform.cropHeight),
            right = maxOf(sourceLeft, sourceRight).coerceIn(transform.cropLeft, transform.cropLeft + transform.cropWidth),
            bottom = maxOf(sourceTop, sourceBottom).coerceIn(transform.cropTop, transform.cropTop + transform.cropHeight)
        )
    }

    private fun nms(detections: List<Detection>): List<Detection> {
        val selected = mutableListOf<Detection>()
        detections.forEach { candidate ->
            if (selected.none { it.boundingBox.iou(candidate.boundingBox) > nmsThreshold }) {
                selected += candidate
            }
        }
        return selected
    }

    private fun BoundingBox.iou(other: BoundingBox): Float {
        val interLeft = maxOf(left, other.left)
        val interTop = maxOf(top, other.top)
        val interRight = minOf(right, other.right)
        val interBottom = minOf(bottom, other.bottom)
        val interWidth = (interRight - interLeft).coerceAtLeast(0f)
        val interHeight = (interBottom - interTop).coerceAtLeast(0f)
        val interArea = interWidth * interHeight
        val union = area() + other.area() - interArea
        return if (union <= 0f) 0f else interArea / union
    }

    private fun BoundingBox.area(): Float {
        return (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)
    }

    private fun Any.flattenFloats(): FloatArray {
        val output = mutableListOf<Float>()
        appendFloats(this, output)
        return output.toFloatArray()
    }

    private fun appendFloats(value: Any?, output: MutableList<Float>) {
        when (value) {
            null -> Unit
            is FloatArray -> output.addAll(value.asIterable())
            is IntArray -> value.forEach { output.add(it.toFloat()) }
            is LongArray -> value.forEach { output.add(it.toFloat()) }
            is ByteArray -> value.forEach { output.add((it.toInt() and 0xFF).toFloat()) }
            is ByteBuffer -> {
                val duplicate = value.duplicate().order(value.order())
                duplicate.rewind()
                repeat(duplicate.remaining() / Float.SIZE_BYTES) {
                    output.add(duplicate.float)
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

    private fun shouldLog(count: Int): Boolean = count <= 5 || count % 30 == 0

    private fun List<InferenceOutput.Tensor>.tensorSummary(): String {
        return joinToString(prefix = "[", postfix = "]") { tensor ->
            "${tensor.name}:${tensor.shape.contentToString()}:${tensor.data.javaClass.simpleName}"
        }
    }

    private fun Detection.summary(): String {
        return "score=$score box=(${boundingBox.left},${boundingBox.top},${boundingBox.right},${boundingBox.bottom})"
    }

    private companion object {
        const val TAG = "RTMPoseDebug"
    }
}
