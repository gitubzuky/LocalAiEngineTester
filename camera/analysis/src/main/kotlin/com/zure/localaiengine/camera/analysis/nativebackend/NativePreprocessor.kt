package com.zure.localaiengine.camera.analysis.nativebackend

import java.nio.ByteBuffer

internal object NativePreprocessor {
    val isLoaded: Boolean
    private val loadError: Throwable?

    init {
        var error: Throwable? = null
        val loaded = try {
            System.loadLibrary("camera_analysis_preprocessor")
            true
        } catch (throwable: Throwable) {
            error = throwable
            false
        }
        isLoaded = loaded
        loadError = error
    }

    fun unavailableCause(): Throwable? = loadError

    external fun nativeVersion(): String

    external fun preprocessYuv420ToFloatTensor(
        yPlane: ByteBuffer,
        uPlane: ByteBuffer,
        vPlane: ByteBuffer,
        sourceWidth: Int,
        sourceHeight: Int,
        yRowStride: Int,
        yPixelStride: Int,
        uRowStride: Int,
        uPixelStride: Int,
        vRowStride: Int,
        vPixelStride: Int,
        rotationDegrees: Int,
        mirrorHorizontal: Boolean,
        cropLeft: Float,
        cropTop: Float,
        cropWidth: Float,
        cropHeight: Float,
        outputWidth: Int,
        outputHeight: Int,
        tensorLayout: Int,
        pixelOrder: Int,
        normalization: Int,
        mean: FloatArray,
        std: FloatArray,
        output: ByteBuffer
    )
}
