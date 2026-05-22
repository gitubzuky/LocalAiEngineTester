package com.zure.localaiengine.camera.analysis.steps

import android.graphics.Bitmap
import com.zure.localaiengine.camera.analysis.api.ResizePolicy
import com.zure.localaiengine.camera.analysis.pipeline.FrameProcessContext
import com.zure.localaiengine.camera.analysis.pipeline.FrameProcessorStep
import com.zure.localaiengine.camera.analysis.pipeline.MutableFrameBuffer

class ResizeStep(
    private val policy: ResizePolicy
) : FrameProcessorStep {
    override suspend fun process(buffer: MutableFrameBuffer, context: FrameProcessContext) {
        val bitmap = buffer.bitmap ?: return
        val resized = when (policy) {
            ResizePolicy.None -> bitmap
            is ResizePolicy.Bilinear -> Bitmap.createScaledBitmap(bitmap, policy.width, policy.height, true)
        }
        buffer.bitmap = resized
        buffer.width = resized.width
        buffer.height = resized.height
    }
}
