package com.zure.localaiengine.camera.analysis.camerax

import androidx.camera.core.ImageProxy
import com.zure.localaiengine.camera.analysis.api.AnalysisLensFacing
import com.zure.localaiengine.camera.analysis.api.CameraFrameInfo
import com.zure.localaiengine.camera.analysis.api.PixelFormat
import com.zure.localaiengine.camera.analysis.pipeline.CameraFrame
import com.zure.localaiengine.camera.analysis.pipeline.PlaneBuffer
import java.nio.ByteBuffer

internal class ImageProxyFrameReader {
    fun read(image: ImageProxy, lensFacing: AnalysisLensFacing): CameraFrame {
        val planes = image.planes.map { plane ->
            PlaneBuffer(
                buffer = plane.buffer.copyDirect(),
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride
            )
        }
        return CameraFrame(
            width = image.width,
            height = image.height,
            rotationDegrees = image.imageInfo.rotationDegrees,
            timestampNanos = image.imageInfo.timestamp,
            format = PixelFormat.Yuv420,
            yPlane = planes.getOrNull(0),
            uPlane = planes.getOrNull(1),
            vPlane = planes.getOrNull(2),
            info = CameraFrameInfo(
                sourceWidth = image.width,
                sourceHeight = image.height,
                rotationDegrees = image.imageInfo.rotationDegrees,
                timestampNanos = image.imageInfo.timestamp,
                lensFacing = lensFacing
            )
        )
    }

    private fun ByteBuffer.copyDirect(): ByteBuffer {
        val duplicate = duplicate()
        duplicate.rewind()
        val output = ByteBuffer.allocateDirect(duplicate.remaining())
        output.put(duplicate)
        output.rewind()
        return output
    }
}
