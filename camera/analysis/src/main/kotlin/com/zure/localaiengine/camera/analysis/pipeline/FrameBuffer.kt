package com.zure.localaiengine.camera.analysis.pipeline

import android.graphics.Bitmap
import com.zure.localaiengine.camera.analysis.api.CameraFrameInfo
import com.zure.localaiengine.camera.analysis.api.PixelFormat
import java.nio.ByteBuffer

data class CameraFrame(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val timestampNanos: Long,
    val format: PixelFormat,
    val yPlane: PlaneBuffer? = null,
    val uPlane: PlaneBuffer? = null,
    val vPlane: PlaneBuffer? = null,
    val rgbaBuffer: ByteBuffer? = null,
    val info: CameraFrameInfo
)

data class PlaneBuffer(
    val buffer: ByteBuffer,
    val rowStride: Int,
    val pixelStride: Int
)

class MutableFrameBuffer(
    var width: Int,
    var height: Int,
    var rotationDegrees: Int,
    var pixelFormat: PixelFormat,
    var bitmap: Bitmap? = null,
    var floatArray: FloatArray? = null,
    var byteBuffer: ByteBuffer? = null,
    val metadata: MutableMap<String, Any> = mutableMapOf()
)

fun CameraFrame.toMutableBuffer(): MutableFrameBuffer {
    return MutableFrameBuffer(
        width = width,
        height = height,
        rotationDegrees = rotationDegrees,
        pixelFormat = format
    ).also { buffer ->
        buffer.metadata[FrameMetadataKeys.SourceFrame] = this
    }
}

object FrameMetadataKeys {
    const val SourceFrame = "source_frame"
    const val Tensors = "tensors"
}
