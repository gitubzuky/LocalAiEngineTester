package com.zure.localaiengine.camera.analysis.steps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import com.zure.localaiengine.camera.analysis.api.CropPolicy
import com.zure.localaiengine.camera.analysis.api.FrameTransform
import com.zure.localaiengine.camera.analysis.pipeline.FrameProcessContext
import com.zure.localaiengine.camera.analysis.pipeline.FrameProcessorStep
import com.zure.localaiengine.camera.analysis.pipeline.MutableFrameBuffer

class CropStep(
    private val policy: CropPolicy
) : FrameProcessorStep {
    override suspend fun process(buffer: MutableFrameBuffer, context: FrameProcessContext) {
        val bitmap = buffer.bitmap ?: return
        val targetWidth = context.profile.inputWidth
        val targetHeight = context.profile.inputHeight
        val sourceRect = when (policy) {
            CropPolicy.None -> Rect(0, 0, bitmap.width, bitmap.height)
            is CropPolicy.CenterCrop -> fixedCenterCrop(bitmap.width, bitmap.height, policy.width, policy.height)
            is CropPolicy.CenterAspectFit -> centerAspectCrop(bitmap.width, bitmap.height, policy.width, policy.height)
            is CropPolicy.Roi -> roiCrop(bitmap.width, bitmap.height, policy)
            is CropPolicy.Letterbox -> Rect(0, 0, bitmap.width, bitmap.height)
        }

        val letterbox = if (policy is CropPolicy.Letterbox) letterbox(bitmap, policy) else null
        val cropped = if (letterbox != null) {
            letterbox.bitmap
        } else {
            Bitmap.createBitmap(bitmap, sourceRect.left, sourceRect.top, sourceRect.width(), sourceRect.height())
        }
        buffer.bitmap = cropped
        buffer.width = cropped.width
        buffer.height = cropped.height

        context.setTransform(
            FrameTransform(
                modelInputWidth = targetWidth,
                modelInputHeight = targetHeight,
                sourceWidth = bitmap.width.toFloat(),
                sourceHeight = bitmap.height.toFloat(),
                cropLeft = sourceRect.left.toFloat(),
                cropTop = sourceRect.top.toFloat(),
                cropWidth = sourceRect.width().toFloat(),
                cropHeight = sourceRect.height().toFloat(),
                scaleX = letterbox?.scale ?: targetWidth / sourceRect.width().toFloat(),
                scaleY = letterbox?.scale ?: targetHeight / sourceRect.height().toFloat(),
                padLeft = letterbox?.padLeft ?: 0f,
                padTop = letterbox?.padTop ?: 0f,
                isMirrored = context.config.mirrorFrontCamera
            )
        )
    }

    private fun fixedCenterCrop(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Rect {
        val cropWidth = targetWidth.coerceIn(1, width)
        val cropHeight = targetHeight.coerceIn(1, height)
        val left = (width - cropWidth) / 2
        val top = (height - cropHeight) / 2
        return Rect(left, top, left + cropWidth, top + cropHeight)
    }

    private fun centerAspectCrop(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Rect {
        val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()
        val sourceRatio = width.toFloat() / height.toFloat()
        return if (sourceRatio > targetRatio) {
            val cropWidth = (height * targetRatio).toInt().coerceAtLeast(1)
            val left = (width - cropWidth) / 2
            Rect(left, 0, left + cropWidth, height)
        } else {
            val cropHeight = (width / targetRatio).toInt().coerceAtLeast(1)
            val top = (height - cropHeight) / 2
            Rect(0, top, width, top + cropHeight)
        }
    }

    private fun roiCrop(width: Int, height: Int, policy: CropPolicy.Roi): Rect {
        val left = policy.left.coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat())
        val top = policy.top.coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat())
        val right = (policy.left + policy.width).coerceIn(left + 1f, width.toFloat())
        val bottom = (policy.top + policy.height).coerceIn(top + 1f, height.toFloat())
        return Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
    }

    private fun letterbox(bitmap: Bitmap, policy: CropPolicy.Letterbox): LetterboxResult {
        val result = Bitmap.createBitmap(policy.width, policy.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.rgb(policy.fillRed, policy.fillGreen, policy.fillBlue))
        val scale = minOf(policy.width / bitmap.width.toFloat(), policy.height / bitmap.height.toFloat())
        val scaledWidth = (bitmap.width * scale).toInt()
        val scaledHeight = (bitmap.height * scale).toInt()
        val left = (policy.width - scaledWidth) / 2
        val top = (policy.height - scaledHeight) / 2
        canvas.drawBitmap(bitmap, null, Rect(left, top, left + scaledWidth, top + scaledHeight), null)
        return LetterboxResult(result, scale, left.toFloat(), top.toFloat())
    }

    private data class LetterboxResult(
        val bitmap: Bitmap,
        val scale: Float,
        val padLeft: Float,
        val padTop: Float
    )
}
