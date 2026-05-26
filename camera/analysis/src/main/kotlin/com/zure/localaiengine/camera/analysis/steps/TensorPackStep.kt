package com.zure.localaiengine.camera.analysis.steps

import android.graphics.Bitmap
import android.util.Log
import com.zure.localaiengine.camera.analysis.api.NormalizationSpec
import com.zure.localaiengine.camera.analysis.api.PixelOrder
import com.zure.localaiengine.camera.analysis.api.TensorDataType
import com.zure.localaiengine.camera.analysis.api.TensorLayout
import com.zure.localaiengine.camera.analysis.pipeline.FrameMetadataKeys
import com.zure.localaiengine.camera.analysis.pipeline.FrameProcessContext
import com.zure.localaiengine.camera.analysis.pipeline.FrameProcessorStep
import com.zure.localaiengine.camera.analysis.pipeline.MutableFrameBuffer
import com.zure.localaiengine.core.inference.InferenceInput
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TensorPackStep : FrameProcessorStep {
    private var logFrameCount = 0

    override suspend fun process(buffer: MutableFrameBuffer, context: FrameProcessContext) {
        val bitmap = buffer.bitmap ?: error("TensorPackStep requires a bitmap input.")
        val spec = buffer.metadata[NormalizeStep.NormalizationKey] as? NormalizationSpec
            ?: NormalizationSpec.None
        val tensor = when (context.profile.tensorDataType) {
            TensorDataType.Float32 -> packFloatTensor(bitmap, context, spec)
            TensorDataType.UInt8 -> packUInt8Tensor(bitmap, context)
        }
        logFrameCount += 1
        if (shouldLog(logFrameCount)) {
            Log.i(
                TAG,
                "kotlin-preprocess frame=$logFrameCount profile=${context.profile.id} " +
                    "bitmap=${bitmap.width}x${bitmap.height} inputShape=${context.profile.inputShape.contentToString()} " +
                    "layout=${context.profile.tensorLayout} order=${context.profile.pixelOrder} norm=$spec " +
                    "tensorShape=${tensor.shape.contentToString()} data=${tensor.data.javaClass.simpleName} " +
                    tensor.data.floatSummary()
            )
        }
        buffer.metadata[FrameMetadataKeys.Tensors] = listOf(tensor)
    }

    private fun packFloatTensor(
        bitmap: Bitmap,
        context: FrameProcessContext,
        spec: NormalizationSpec
    ): InferenceInput.Tensor {
        val output = ByteBuffer
            .allocateDirect(bitmap.width * bitmap.height * 3 * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        writePixels(bitmap, context.profile.tensorLayout) { index, channel, color ->
            output.putFloat(
                index * Float.SIZE_BYTES,
                normalize(channelValue(color, channel, context.profile.pixelOrder), channel, spec)
            )
        }
        output.rewind()
        return InferenceInput.Tensor(
            name = context.profile.inputName.orEmpty(),
            data = output,
            shape = context.profile.inputShape
        )
    }

    private fun packUInt8Tensor(bitmap: Bitmap, context: FrameProcessContext): InferenceInput.Tensor {
        val output = ByteBuffer
            .allocateDirect(bitmap.width * bitmap.height * 3)
            .order(ByteOrder.nativeOrder())
        writePixels(bitmap, context.profile.tensorLayout) { _, channel, color ->
            output.put(channelValue(color, channel, context.profile.pixelOrder).toByte())
        }
        output.rewind()
        return InferenceInput.Tensor(
            name = context.profile.inputName.orEmpty(),
            data = output,
            shape = context.profile.inputShape
        )
    }

    private inline fun writePixels(
        bitmap: Bitmap,
        layout: TensorLayout,
        write: (index: Int, channel: Int, color: Int) -> Unit
    ) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixelIndex = y * bitmap.width + x
                val color = pixels[pixelIndex]
                for (channel in 0 until 3) {
                    val tensorIndex = when (layout) {
                        TensorLayout.NHWC -> pixelIndex * 3 + channel
                        TensorLayout.NCHW -> channel * bitmap.width * bitmap.height + pixelIndex
                    }
                    write(tensorIndex, channel, color)
                }
            }
        }
    }

    private fun channelValue(color: Int, channel: Int, order: PixelOrder): Int {
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        return when (order) {
            PixelOrder.RGB -> when (channel) {
                0 -> red
                1 -> green
                else -> blue
            }
            PixelOrder.BGR -> when (channel) {
                0 -> blue
                1 -> green
                else -> red
            }
        }
    }

    private fun normalize(value: Int, channel: Int, spec: NormalizationSpec): Float {
        return when (spec) {
            NormalizationSpec.None -> value.toFloat()
            NormalizationSpec.ZeroToOne -> value / 255f
            NormalizationSpec.MinusOneToOne -> value / 127.5f - 1f
            is NormalizationSpec.MeanStd -> {
                val mean = spec.mean.getOrElse(channel) { 0f }
                val std = spec.std.getOrElse(channel) { 1f }
                (value - mean) / std
            }
        }
    }

    private fun shouldLog(count: Int): Boolean = count <= 5 || count % 30 == 0

    private fun Any.floatSummary(sampleSize: Int = 8): String {
        val values = when (this) {
            is FloatArray -> this
            is ByteBuffer -> {
                val duplicate = duplicate().order(order())
                duplicate.rewind()
                if (duplicate.remaining() % Float.SIZE_BYTES != 0) return "bytes=${duplicate.remaining()}"
                val floats = FloatArray(duplicate.remaining() / Float.SIZE_BYTES)
                for (index in floats.indices) floats[index] = duplicate.float
                floats
            }
            else -> return "summary=unsupported"
        }
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

    private companion object {
        const val TAG = "RTMPoseDebug"
    }
}
