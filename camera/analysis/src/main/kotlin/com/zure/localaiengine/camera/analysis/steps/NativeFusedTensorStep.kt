package com.zure.localaiengine.camera.analysis.steps

import com.zure.localaiengine.camera.analysis.api.AnalysisLensFacing
import com.zure.localaiengine.camera.analysis.api.CropPolicy
import com.zure.localaiengine.camera.analysis.api.FrameTransform
import com.zure.localaiengine.camera.analysis.api.NormalizationSpec
import com.zure.localaiengine.camera.analysis.api.PixelFormat
import com.zure.localaiengine.camera.analysis.api.PixelOrder
import com.zure.localaiengine.camera.analysis.api.ResizePolicy
import com.zure.localaiengine.camera.analysis.api.TensorDataType
import com.zure.localaiengine.camera.analysis.api.TensorLayout
import com.zure.localaiengine.camera.analysis.nativebackend.NativePreprocessor
import com.zure.localaiengine.camera.analysis.pipeline.CameraFrame
import com.zure.localaiengine.camera.analysis.pipeline.FrameMetadataKeys
import com.zure.localaiengine.camera.analysis.pipeline.FrameProcessContext
import com.zure.localaiengine.camera.analysis.pipeline.FrameProcessorStep
import com.zure.localaiengine.camera.analysis.pipeline.MutableFrameBuffer
import com.zure.localaiengine.core.inference.InferenceInput
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NativeFusedTensorStep(
    private val fallbackSteps: List<FrameProcessorStep>
) : FrameProcessorStep {
    private var logFrameCount = 0

    override suspend fun process(buffer: MutableFrameBuffer, context: FrameProcessContext) {
        val processed = if (NativePreprocessor.isLoaded && canProcess(context)) {
            runCatching { processNative(buffer, context) }.getOrDefault(false)
        } else {
            false
        }
        if (!processed) fallbackSteps.forEach { step -> step.process(buffer, context) }
    }

    private fun canProcess(context: FrameProcessContext): Boolean {
        return context.profile.tensorDataType == TensorDataType.Float32 &&
            context.profile.inputWidth > 0 &&
            context.profile.inputHeight > 0 &&
            context.profile.resizePolicy is ResizePolicy.Bilinear &&
            context.profile.cropPolicy !is CropPolicy.Letterbox &&
            context.profile.cropPolicy !is CropPolicy.Roi
    }

    private fun processNative(buffer: MutableFrameBuffer, context: FrameProcessContext): Boolean {
        val frame = buffer.metadata[FrameMetadataKeys.SourceFrame] as? CameraFrame ?: return false
        if (frame.format != PixelFormat.Yuv420) return false
        val yPlane = frame.yPlane ?: return false
        val uPlane = frame.uPlane ?: return false
        val vPlane = frame.vPlane ?: return false

        val outputWidth = context.profile.inputWidth
        val outputHeight = context.profile.inputHeight
        val output = ByteBuffer
            .allocateDirect(outputWidth * outputHeight * 3 * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        val crop = cropRectFor(context, frame)
        val mirror = context.config.mirrorFrontCamera &&
            context.sourceInfo.lensFacing == AnalysisLensFacing.Front
        val normalization = context.profile.normalization.nativeCode()
        val mean = (context.profile.normalization as? NormalizationSpec.MeanStd)?.mean ?: floatArrayOf()
        val std = (context.profile.normalization as? NormalizationSpec.MeanStd)?.std ?: floatArrayOf()

        NativePreprocessor.preprocessYuv420ToFloatTensor(
            yPlane = yPlane.buffer,
            uPlane = uPlane.buffer,
            vPlane = vPlane.buffer,
            sourceWidth = frame.width,
            sourceHeight = frame.height,
            yRowStride = yPlane.rowStride,
            yPixelStride = yPlane.pixelStride,
            uRowStride = uPlane.rowStride,
            uPixelStride = uPlane.pixelStride,
            vRowStride = vPlane.rowStride,
            vPixelStride = vPlane.pixelStride,
            rotationDegrees = frame.rotationDegrees,
            mirrorHorizontal = mirror,
            cropLeft = crop.left,
            cropTop = crop.top,
            cropWidth = crop.width,
            cropHeight = crop.height,
            outputWidth = outputWidth,
            outputHeight = outputHeight,
            tensorLayout = context.profile.tensorLayout.nativeCode(),
            pixelOrder = context.profile.pixelOrder.nativeCode(),
            normalization = normalization,
            mean = mean,
            std = std,
            output = output
        )
        output.rewind()
        logFrameCount += 1
        if (shouldLog(logFrameCount)) {
            Log.i(
                TAG,
                "native-preprocess frame=$logFrameCount profile=${context.profile.id} " +
                    "source=${frame.width}x${frame.height} rotation=${frame.rotationDegrees} " +
                    "crop=(${crop.left},${crop.top},${crop.width},${crop.height}) output=${outputWidth}x${outputHeight} " +
                    "shape=${context.profile.inputShape.contentToString()} layout=${context.profile.tensorLayout} " +
                    "order=${context.profile.pixelOrder} norm=${context.profile.normalization} mirror=$mirror " +
                    output.floatSummary()
            )
        }

        context.setTransform(
            FrameTransform(
                modelInputWidth = outputWidth,
                modelInputHeight = outputHeight,
                sourceWidth = if (frame.rotationDegrees % 180 == 0) frame.width.toFloat() else frame.height.toFloat(),
                sourceHeight = if (frame.rotationDegrees % 180 == 0) frame.height.toFloat() else frame.width.toFloat(),
                cropLeft = crop.left,
                cropTop = crop.top,
                cropWidth = crop.width,
                cropHeight = crop.height,
                scaleX = outputWidth / crop.width,
                scaleY = outputHeight / crop.height,
                isMirrored = mirror
            )
        )
        buffer.metadata[FrameMetadataKeys.Tensors] = listOf(
            InferenceInput.Tensor(
                name = context.profile.inputName.orEmpty(),
                data = output,
                shape = context.profile.inputShape
            )
        )
        buffer.metadata[NativeBackendVersionKey] = NativePreprocessor.nativeVersion()
        return true
    }

    private fun cropRectFor(context: FrameProcessContext, frame: CameraFrame): CropRect {
        val rotatedWidth = if (frame.rotationDegrees % 180 == 0) frame.width else frame.height
        val rotatedHeight = if (frame.rotationDegrees % 180 == 0) frame.height else frame.width
        return when (val policy = context.profile.cropPolicy) {
            CropPolicy.None -> CropRect(0f, 0f, rotatedWidth.toFloat(), rotatedHeight.toFloat())
            is CropPolicy.CenterAspectFit -> centerAspectCrop(rotatedWidth, rotatedHeight, policy.width, policy.height)
            is CropPolicy.CenterCrop -> fixedCenterCrop(rotatedWidth, rotatedHeight, policy.width, policy.height)
            is CropPolicy.Roi -> CropRect(policy.left, policy.top, policy.width, policy.height)
            is CropPolicy.Letterbox -> CropRect(0f, 0f, rotatedWidth.toFloat(), rotatedHeight.toFloat())
        }
    }

    private fun fixedCenterCrop(width: Int, height: Int, targetWidth: Int, targetHeight: Int): CropRect {
        val cropWidth = targetWidth.toFloat().coerceIn(1f, width.toFloat())
        val cropHeight = targetHeight.toFloat().coerceIn(1f, height.toFloat())
        return CropRect(
            left = (width - cropWidth) / 2f,
            top = (height - cropHeight) / 2f,
            width = cropWidth,
            height = cropHeight
        )
    }

    private fun centerAspectCrop(width: Int, height: Int, targetWidth: Int, targetHeight: Int): CropRect {
        val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()
        val sourceRatio = width.toFloat() / height.toFloat()
        return if (sourceRatio > targetRatio) {
            val cropWidth = height * targetRatio
            CropRect((width - cropWidth) / 2f, 0f, cropWidth, height.toFloat())
        } else {
            val cropHeight = width / targetRatio
            CropRect(0f, (height - cropHeight) / 2f, width.toFloat(), cropHeight)
        }
    }

    private fun TensorLayout.nativeCode(): Int = when (this) {
        TensorLayout.NHWC -> 0
        TensorLayout.NCHW -> 1
    }

    private fun PixelOrder.nativeCode(): Int = when (this) {
        PixelOrder.RGB -> 0
        PixelOrder.BGR -> 1
    }

    private fun NormalizationSpec.nativeCode(): Int = when (this) {
        NormalizationSpec.None -> 0
        NormalizationSpec.ZeroToOne -> 1
        NormalizationSpec.MinusOneToOne -> 2
        is NormalizationSpec.MeanStd -> 3
    }

    private data class CropRect(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float
    )

    companion object {
        const val NativeBackendVersionKey = "native_backend_version"
        private const val TAG = "RTMPoseDebug"
    }

    private fun shouldLog(count: Int): Boolean = count <= 5 || count % 30 == 0

    private fun ByteBuffer.floatSummary(sampleSize: Int = 8): String {
        val duplicate = duplicate().order(order())
        duplicate.rewind()
        if (duplicate.remaining() % Float.SIZE_BYTES != 0) return "bytes=${duplicate.remaining()}"
        val count = duplicate.remaining() / Float.SIZE_BYTES
        if (count == 0) return "count=0"
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        var sum = 0.0
        val sample = mutableListOf<Float>()
        repeat(count) { index ->
            val value = duplicate.float
            min = minOf(min, value)
            max = maxOf(max, value)
            sum += value
            if (index < sampleSize) sample += value
        }
        return "count=$count min=$min max=$max mean=${sum / count} " +
            "sample=${sample.joinToString(prefix = "[", postfix = "]") { "%.4f".format(it) }}"
    }
}
