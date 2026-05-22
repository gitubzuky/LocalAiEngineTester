package com.zure.localaienginetester.ui.screen.camera

import com.zure.localaiengine.core.inference.InferenceOutput
import com.zure.localaienginetester.util.AppLog
import java.lang.reflect.Array as ReflectArray

class RtmposeOutputDecoder {
    private var decodeLogCount = 0

    fun decode(outputs: List<InferenceOutput>, inputWidth: Int, inputHeight: Int): PoseResult {
        val tensors = outputs.filterIsInstance<InferenceOutput.Tensor>()
        require(tensors.size >= 2) {
            "RTMPose 需要至少 2 个输出 Tensor，实际 ${tensors.size} 个。"
        }

        val candidates = tensors.filter { it.shape.size >= 3 }
        require(candidates.size >= 2) {
            "未识别到 SimCC 输出，shapes=${tensors.joinToString { it.shape.contentToString() }}"
        }

        val xTensor = candidates.firstOrNull { it.name == "pred_x" }
            ?: candidates.firstOrNull { it.shape.last() == (inputWidth * SIMCC_SPLIT_RATIO).toInt() }
            ?: candidates.minBy { it.shape.last() }
        val yTensor = candidates.firstOrNull { it.name == "pred_y" }
            ?: candidates.firstOrNull {
                it !== xTensor &&
                    it.keypointCount() == xTensor.keypointCount() &&
                    it.shape.last() == (inputHeight * SIMCC_SPLIT_RATIO).toInt()
            }
            ?: candidates.filter { it !== xTensor }.maxBy { it.shape.last() }
        val keypointCount = minOf(xTensor.keypointCount(), yTensor.keypointCount(), BODY_KEYPOINT_COUNT)
        val xBins = xTensor.shape.last()
        val yBins = yTensor.shape.last()
        val xValues = flattenFloats(xTensor.data)
        val yValues = flattenFloats(yTensor.data)
        val xRatio = xBins / inputWidth.toFloat()
        val yRatio = yBins / inputHeight.toFloat()
        decodeLogCount += 1

        val keypoints = (0 until keypointCount).map { index ->
            val xOffset = index * xBins
            val yOffset = index * yBins
            val xArgMax = argMax(xValues, xOffset, xBins)
            val yArgMax = argMax(yValues, yOffset, yBins)
            PoseKeypoint(
                index = index,
                x = xArgMax.index / xRatio,
                y = yArgMax.index / yRatio,
                score = minOf(xArgMax.value, yArgMax.value)
            )
        }
        if (shouldLog(decodeLogCount)) {
            AppLog.i(
                TAG,
                "decode[$decodeLogCount] input=${inputWidth}x$inputHeight " +
                    "outputs=${tensors.joinToString { "${it.name}:${it.shape.contentToString()}" }} " +
                    "x=${xTensor.name}:${xTensor.shape.contentToString()} y=${yTensor.name}:${yTensor.shape.contentToString()} " +
                    "keypoints=$keypointCount xBins=$xBins yBins=$yBins xRatio=$xRatio yRatio=$yRatio " +
                    "xSummary=${xValues.summary()} ySummary=${yValues.summary()} " +
                    "body=${keypoints.summary()}"
            )
        }

        return PoseResult(
            keypoints = keypoints,
            inputWidth = inputWidth,
            inputHeight = inputHeight
        )
    }

    private fun InferenceOutput.Tensor.keypointCount(): Int {
        return shape.getOrNull(shape.size - 2) ?: 0
    }

    private fun argMax(values: FloatArray, start: Int, length: Int): ArgMax {
        var bestIndex = 0
        var bestValue = Float.NEGATIVE_INFINITY
        for (i in 0 until length) {
            val value = values.getOrElse(start + i) { Float.NEGATIVE_INFINITY }
            if (value > bestValue) {
                bestValue = value
                bestIndex = i
            }
        }
        return ArgMax(bestIndex, bestValue)
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
            is Array<*> -> value.forEach { appendFloats(it, output) }
            else -> if (value.javaClass.isArray) {
                for (index in 0 until ReflectArray.getLength(value)) {
                    appendFloats(ReflectArray.get(value, index), output)
                }
            }
        }
    }

    private fun shouldLog(count: Int): Boolean = count <= 5 || count % 30 == 0

    private fun FloatArray.summary(sampleSize: Int = 8): String {
        if (isEmpty()) return "count=0"
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        var sum = 0.0
        forEach { value ->
            min = minOf(min, value)
            max = maxOf(max, value)
            sum += value
        }
        val sample = take(sampleSize).joinToString(prefix = "[", postfix = "]") { "%.4f".format(it) }
        return "count=$size min=$min max=$max mean=${sum / size} sample=$sample"
    }

    private fun List<PoseKeypoint>.summary(): String {
        if (isEmpty()) return "count=0"
        val visible = count { it.score >= OVERLAY_MIN_SCORE }
        val minScore = minOf { it.score }
        val maxScore = maxOf { it.score }
        val minX = minOf { it.x }
        val maxX = maxOf { it.x }
        val minY = minOf { it.y }
        val maxY = maxOf { it.y }
        val sample = take(6).joinToString(prefix = "[", postfix = "]") {
            "${it.index}=(${it.x.format1()},${it.y.format1()},${it.score.format4()})"
        }
        return "count=$size visible@$OVERLAY_MIN_SCORE=$visible score=$minScore..$maxScore " +
            "x=$minX..$maxX y=$minY..$maxY sample=$sample"
    }

    private fun Float.format1(): String = "%.1f".format(this)

    private fun Float.format4(): String = "%.4f".format(this)

    private data class ArgMax(
        val index: Int,
        val value: Float
    )

    private companion object {
        const val TAG = "RTMPoseDebug"
        const val BODY_KEYPOINT_COUNT = 17
        const val SIMCC_SPLIT_RATIO = 2f
        const val OVERLAY_MIN_SCORE = 0.15f
    }
}
