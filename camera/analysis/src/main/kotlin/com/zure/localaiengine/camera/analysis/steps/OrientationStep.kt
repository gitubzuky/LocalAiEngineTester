package com.zure.localaiengine.camera.analysis.steps

import android.graphics.Matrix
import com.zure.localaiengine.camera.analysis.api.AnalysisLensFacing
import com.zure.localaiengine.camera.analysis.pipeline.FrameProcessContext
import com.zure.localaiengine.camera.analysis.pipeline.FrameProcessorStep
import com.zure.localaiengine.camera.analysis.pipeline.MutableFrameBuffer

class OrientationStep : FrameProcessorStep {
    override suspend fun process(buffer: MutableFrameBuffer, context: FrameProcessContext) {
        val bitmap = buffer.bitmap ?: return
        val shouldMirror = context.config.mirrorFrontCamera &&
            context.sourceInfo.lensFacing == AnalysisLensFacing.Front
        if (buffer.rotationDegrees == 0 && !shouldMirror) return

        val matrix = Matrix().apply {
            postRotate(buffer.rotationDegrees.toFloat())
            if (shouldMirror) postScale(-1f, 1f)
        }
        val rotated = android.graphics.Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
        buffer.bitmap = rotated
        buffer.width = rotated.width
        buffer.height = rotated.height
        buffer.rotationDegrees = 0
    }
}
