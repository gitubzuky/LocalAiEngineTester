package com.zure.localaiengine.camera.analysis.steps

import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import com.zure.localaiengine.camera.analysis.api.PixelFormat
import com.zure.localaiengine.camera.analysis.pipeline.CameraFrame
import com.zure.localaiengine.camera.analysis.pipeline.FrameMetadataKeys
import com.zure.localaiengine.camera.analysis.pipeline.FrameProcessContext
import com.zure.localaiengine.camera.analysis.pipeline.FrameProcessorStep
import com.zure.localaiengine.camera.analysis.pipeline.MutableFrameBuffer
import com.zure.localaiengine.camera.analysis.pipeline.PlaneBuffer
import java.io.ByteArrayOutputStream

class YuvToRgbStep : FrameProcessorStep {
    override suspend fun process(buffer: MutableFrameBuffer, context: FrameProcessContext) {
        if (buffer.bitmap != null) return
        val frame = buffer.metadata[FrameMetadataKeys.SourceFrame] as? CameraFrame
            ?: error("Missing source frame for YUV conversion.")
        require(frame.format == PixelFormat.Yuv420) {
            "YuvToRgbStep requires a YUV_420_888 source frame."
        }
        val nv21 = yuv420ToNv21(frame)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, frame.width, frame.height, null)
        val jpeg = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, frame.width, frame.height), 95, jpeg)
        buffer.bitmap = BitmapFactory.decodeByteArray(jpeg.toByteArray(), 0, jpeg.size())
        buffer.pixelFormat = PixelFormat.Rgb888
    }

    private fun yuv420ToNv21(frame: CameraFrame): ByteArray {
        val y = requireNotNull(frame.yPlane)
        val u = requireNotNull(frame.uPlane)
        val v = requireNotNull(frame.vPlane)
        val output = ByteArray(frame.width * frame.height * 3 / 2)
        copyLuma(y, frame.width, frame.height, output)
        copyChromaAsVu(u, v, frame.width, frame.height, output, frame.width * frame.height)
        return output
    }

    private fun copyLuma(plane: PlaneBuffer, width: Int, height: Int, output: ByteArray) {
        val source = plane.buffer.duplicate()
        var dst = 0
        for (row in 0 until height) {
            val rowStart = row * plane.rowStride
            for (col in 0 until width) {
                output[dst++] = source.get(rowStart + col * plane.pixelStride)
            }
        }
    }

    private fun copyChromaAsVu(
        u: PlaneBuffer,
        v: PlaneBuffer,
        width: Int,
        height: Int,
        output: ByteArray,
        start: Int
    ) {
        val uBuffer = u.buffer.duplicate()
        val vBuffer = v.buffer.duplicate()
        var dst = start
        val chromaHeight = height / 2
        val chromaWidth = width / 2
        for (row in 0 until chromaHeight) {
            val uRow = row * u.rowStride
            val vRow = row * v.rowStride
            for (col in 0 until chromaWidth) {
                output[dst++] = vBuffer.get(vRow + col * v.pixelStride)
                output[dst++] = uBuffer.get(uRow + col * u.pixelStride)
            }
        }
    }
}
