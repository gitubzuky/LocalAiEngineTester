package com.zure.localaienginetester.ui.screen.camera

import com.zure.localaiengine.camera.analysis.api.FrameTransform
import com.zure.localaiengine.core.inference.InferenceOutput
import com.zure.localaiengine.core.vision.BoundingBox
import com.zure.localaiengine.core.vision.Detection
import com.zure.localaienginetester.util.AppLog
import java.lang.reflect.Array as ReflectArray
import java.nio.ByteBuffer

class RtmDetPersonDecoder(
    private val scoreThreshold: Float = 0.08f,
    private val topKScoreThreshold: Float = 0.03f,
    private val topKMinAreaRatio: Float = 0.003f,
    private val topKMaxAreaRatio: Float = 0.8f,
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
                "rtmdet-decode[$decodeCount] outputs=${tensors.tensorSummary()} " +
                    "transform=$transform scoreThreshold=$scoreThreshold " +
                    "topKScoreThreshold=$topKScoreThreshold topKMinAreaRatio=$topKMinAreaRatio " +
                    "topKMaxAreaRatio=$topKMaxAreaRatio " +
                    "nmsThreshold=$nmsThreshold"
            )
        }
        val topK = decodeTopKBoxesScoresLabels(tensors, transform)
        val detections = if (topK.supported) {
            topK.detections
        } else {
            val aiHub = decodeAiHubBoxesScoresClassIdx(tensors, transform)
            val packed = aiHub.ifEmpty { decodePackedDetections(tensors, transform) }
            packed.ifEmpty { decodeBoxesAndScores(tensors, transform) }
        }
        val selected = nms(detections.sortedByDescending { it.score })
        if (shouldLog(decodeCount)) {
            AppLog.i(
                TAG,
                "rtmdet-decode[$decodeCount] candidates=${detections.size} nms=${selected.size} " +
                    "best=${selected.firstOrNull()?.summary() ?: "none"}"
            )
        }
        if (detections.isEmpty() && tensors.isNotEmpty()) {
            AppLog.w(
                TAG,
                "rtmdet-decode[$decodeCount] no detections decoded. " +
                    "If AI Hub outputs boxes/scores/class_idx, class_idx support is required. outputs=${tensors.tensorSummary()}"
            )
        }
        return selected
    }

    private fun decodeTopKBoxesScoresLabels(
        tensors: List<InferenceOutput.Tensor>,
        transform: FrameTransform
    ): DecodeAttempt {
        val boxScoreTensor = tensors.firstOrNull { tensor ->
            tensor.shape.lastOrNull() == 5
        } ?: return DecodeAttempt.Unsupported
        val labelTensor = tensors.firstOrNull { tensor ->
            tensor !== boxScoreTensor && tensor.shape.lastOrNull() == boxScoreTensor.shape.getOrNull(boxScoreTensor.shape.lastIndex - 1)
        } ?: tensors.firstOrNull { tensor ->
            tensor !== boxScoreTensor && tensor.shape.lastOrNull() != 5
        } ?: return DecodeAttempt.Unsupported
        val rows = boxScoreTensor.data.flattenFloats().asList().chunked(5)
        val labels = labelTensor.data.flattenLongs()
        if (rows.isEmpty() || labels.size < rows.size) {
            AppLog.w(
                TAG,
                "rtmdet-decode[$decodeCount] topK shape mismatch rows=${rows.size} labels=${labels.size}"
            )
            return DecodeAttempt(supported = true, detections = emptyList())
        }

        var personRows = 0
        var skippedSmallRows = 0
        var skippedLargeRows = 0
        var passedRows = 0
        val decoded = rows.mapIndexedNotNull { index, row ->
            val label = labels[index].toInt()
            if (label == personClassIndex) personRows += 1
            if (label != personClassIndex) return@mapIndexedNotNull null
            val score = row.getOrNull(4) ?: return@mapIndexedNotNull null
            val box = row.toSourceBox(transform)
            val areaRatio = box.areaRatio(transform)
            // The exported tiny top-k model often returns a low-confidence full-frame row first.
            // Drop that row before score filtering so tighter person boxes can be selected.
            if (areaRatio > topKMaxAreaRatio) {
                skippedLargeRows += 1
                return@mapIndexedNotNull null
            }
            if (areaRatio < topKMinAreaRatio) {
                skippedSmallRows += 1
                return@mapIndexedNotNull null
            }
            if (score < topKScoreThreshold) return@mapIndexedNotNull null
            passedRows += 1
            Detection(
                label = "person",
                score = score,
                boundingBox = box
            )
        }
        if (shouldLog(decodeCount)) {
            AppLog.i(
                TAG,
                "rtmdet-decode[$decodeCount] topK rows=${rows.size} labels=${labels.size} " +
                    "personRows=$personRows skippedSmall=$skippedSmallRows skippedLarge=$skippedLargeRows " +
                    "passed=$passedRows decoded=${decoded.size} " +
                    "best=${decoded.maxByOrNull { it.score }?.summary() ?: "none"}"
            )
            AppLog.i(
                TAG,
                "rtmdet-decode[$decodeCount] topK candidates=${rows.mapIndexed { index, row ->
                    val label = labels.getOrNull(index)?.toInt()
                    val box = row.toSourceBox(transform)
                    "i=$index label=$label score=${row.getOrNull(4)} raw=${row.take(4)} source=${box.summary()} areaRatio=${box.areaRatio(transform)}"
                }.joinToString()}"
            )
        }
        return DecodeAttempt(supported = true, detections = decoded)
    }

    private fun decodeAiHubBoxesScoresClassIdx(
        tensors: List<InferenceOutput.Tensor>,
        transform: FrameTransform
    ): List<Detection> {
        val boxesTensor = tensors.firstOrNull { it.name.equals("boxes", ignoreCase = true) }
            ?: return emptyList()
        val scoresTensor = tensors.firstOrNull { it.name.equals("scores", ignoreCase = true) }
            ?: return emptyList()
        val classIdxTensor = tensors.firstOrNull { it.name.equals("class_idx", ignoreCase = true) }
            ?: return emptyList()
        val boxes = boxesTensor.data.flattenFloats().asList().chunked(4)
        val scores = scoresTensor.data.flattenFloats()
        val classIdx = classIdxTensor.data.flattenSignedBytes()
        if (boxes.isEmpty() || scores.size != boxes.size || classIdx.size != boxes.size) {
            AppLog.w(
                TAG,
                "rtmdet-decode[$decodeCount] AI Hub shape mismatch boxes=${boxes.size} " +
                    "scores=${scores.size} classIdx=${classIdx.size}"
            )
            return emptyList()
        }

        var personRows = 0
        var passedRows = 0
        val decoded = boxes.mapIndexedNotNull { index, box ->
            val label = classIdx[index]
            if (label == personClassIndex) personRows += 1
            if (label != personClassIndex) return@mapIndexedNotNull null
            val score = scores[index]
            if (score < scoreThreshold) return@mapIndexedNotNull null
            passedRows += 1
            Detection(
                label = "person",
                score = score,
                boundingBox = box.toSourceBox(transform)
            )
        }
        if (shouldLog(decodeCount)) {
            AppLog.i(
                TAG,
                "rtmdet-decode[$decodeCount] aiHub boxes=${boxes.size} personRows=$personRows " +
                    "passed=$passedRows decoded=${decoded.size} best=${decoded.maxByOrNull { it.score }?.summary() ?: "none"}"
            )
        }
        return decoded
    }

    private fun decodePackedDetections(
        tensors: List<InferenceOutput.Tensor>,
        transform: FrameTransform
    ): List<Detection> {
        var totalRows = 0
        var personRows = 0
        var passedRows = 0
        return tensors.flatMap { tensor ->
            val shape = tensor.shape
            val values = tensor.data.flattenFloats()
            val stride = shape.lastOrNull()?.takeIf { it >= 5 } ?: return@flatMap emptyList()
            values.asList()
                .chunked(stride)
                .mapNotNull { row ->
                    totalRows += 1
                    val score = row.getOrNull(4) ?: return@mapNotNull null
                    val label = row.getOrNull(5)?.toInt() ?: personClassIndex
                    if (label == personClassIndex) personRows += 1
                    if (score < scoreThreshold || label != personClassIndex) return@mapNotNull null
                    passedRows += 1
                    Detection(
                        label = "person",
                        score = score,
                        boundingBox = row.toSourceBox(transform)
                    )
                }
        }.also { detections ->
            if (shouldLog(decodeCount) && totalRows > 0) {
                AppLog.i(
                    TAG,
                    "rtmdet-decode[$decodeCount] packed rows=$totalRows personRows=$personRows " +
                        "passed=$passedRows decoded=${detections.size}"
                )
            }
        }
    }

    private fun decodeBoxesAndScores(
        tensors: List<InferenceOutput.Tensor>,
        transform: FrameTransform
    ): List<Detection> {
        val boxesTensor = tensors.firstOrNull { it.shape.lastOrNull() == 4 } ?: return emptyList()
        val scoresTensor = tensors.firstOrNull { it !== boxesTensor && (it.shape.lastOrNull() ?: 0) >= 1 }
            ?: return emptyList()
        val classIdxTensor = tensors.firstOrNull { it.name.contains("class", ignoreCase = true) }
        val boxes = boxesTensor.data.flattenFloats().asList().chunked(4)
        val scores = scoresTensor.data.flattenFloats()
        val classCount = scoresTensor.shape.lastOrNull()?.coerceAtLeast(1) ?: 1
        var passedRows = 0
        val decoded = boxes.mapIndexedNotNull { index, box ->
            val scoreIndex = index * classCount + personClassIndex
            val score = scores.getOrNull(scoreIndex) ?: return@mapIndexedNotNull null
            if (score < scoreThreshold) return@mapIndexedNotNull null
            passedRows += 1
            Detection(
                label = "person",
                score = score,
                boundingBox = box.toSourceBox(transform)
            )
        }
        if (shouldLog(decodeCount)) {
            AppLog.i(
                TAG,
                "rtmdet-decode[$decodeCount] boxes+scores boxes=${boxes.size} scores=${scores.size} " +
                    "classCount=$classCount classIdxTensor=${classIdxTensor?.shape?.contentToString() ?: "none"} " +
                    "passed=$passedRows decoded=${decoded.size}"
            )
        }
        return decoded
    }

    private fun List<Float>.toSourceBox(transform: FrameTransform): BoundingBox {
        val left = (getOrElse(0) { 0f } - transform.padLeft) / transform.scaleX + transform.cropLeft
        val top = (getOrElse(1) { 0f } - transform.padTop) / transform.scaleY + transform.cropTop
        val right = (getOrElse(2) { 0f } - transform.padLeft) / transform.scaleX + transform.cropLeft
        val bottom = (getOrElse(3) { 0f } - transform.padTop) / transform.scaleY + transform.cropTop
        return BoundingBox(
            left = minOf(left, right).coerceIn(transform.cropLeft, transform.cropLeft + transform.cropWidth),
            top = minOf(top, bottom).coerceIn(transform.cropTop, transform.cropTop + transform.cropHeight),
            right = maxOf(left, right).coerceIn(transform.cropLeft, transform.cropLeft + transform.cropWidth),
            bottom = maxOf(top, bottom).coerceIn(transform.cropTop, transform.cropTop + transform.cropHeight)
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

    private fun BoundingBox.areaRatio(transform: FrameTransform): Float {
        val sourceWidth = transform.sourceWidth.takeIf { it > 0f } ?: transform.cropWidth
        val sourceHeight = transform.sourceHeight.takeIf { it > 0f } ?: transform.cropHeight
        val sourceArea = sourceWidth * sourceHeight
        return if (sourceArea <= 0f) 0f else area() / sourceArea
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
                repeat(duplicate.remaining()) {
                    output.add((duplicate.get().toInt() and 0xFF).toFloat())
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

    private fun Any.flattenSignedBytes(): IntArray {
        val output = mutableListOf<Int>()
        appendSignedBytes(this, output)
        return output.toIntArray()
    }

    private fun Any.flattenLongs(): LongArray {
        val output = mutableListOf<Long>()
        appendLongs(this, output)
        return output.toLongArray()
    }

    private fun appendLongs(value: Any?, output: MutableList<Long>) {
        when (value) {
            null -> Unit
            is LongArray -> output.addAll(value.asIterable())
            is IntArray -> value.forEach { output.add(it.toLong()) }
            is ByteArray -> value.forEach { output.add(it.toLong()) }
            is ByteBuffer -> {
                val duplicate = value.duplicate().order(value.order())
                duplicate.rewind()
                repeat(duplicate.remaining()) {
                    output.add(duplicate.get().toLong())
                }
            }
            is Array<*> -> value.forEach { appendLongs(it, output) }
            else -> if (value.javaClass.isArray) {
                for (index in 0 until ReflectArray.getLength(value)) {
                    appendLongs(ReflectArray.get(value, index), output)
                }
            }
        }
    }

    private fun appendSignedBytes(value: Any?, output: MutableList<Int>) {
        when (value) {
            null -> Unit
            is ByteArray -> value.forEach { output.add(it.toInt()) }
            is ByteBuffer -> {
                val duplicate = value.duplicate().order(value.order())
                duplicate.rewind()
                repeat(duplicate.remaining()) {
                    output.add(duplicate.get().toInt())
                }
            }
            is Array<*> -> value.forEach { appendSignedBytes(it, output) }
            else -> if (value.javaClass.isArray) {
                for (index in 0 until ReflectArray.getLength(value)) {
                    appendSignedBytes(ReflectArray.get(value, index), output)
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

    private fun BoundingBox.summary(): String {
        return "(${left},${top},${right},${bottom})"
    }

    private data class DecodeAttempt(
        val supported: Boolean,
        val detections: List<Detection>
    ) {
        companion object {
            val Unsupported = DecodeAttempt(supported = false, detections = emptyList())
        }
    }

    private companion object {
        const val TAG = "RTMPoseDebug"
    }
}
