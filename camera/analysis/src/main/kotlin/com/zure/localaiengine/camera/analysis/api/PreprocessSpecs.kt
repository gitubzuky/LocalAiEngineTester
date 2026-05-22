package com.zure.localaiengine.camera.analysis.api

enum class PixelFormat {
    Yuv420,
    Rgba8888,
    Rgb888
}

enum class PixelOrder {
    RGB,
    BGR
}

enum class TensorDataType {
    Float32,
    UInt8
}

enum class TensorLayout {
    NHWC,
    NCHW
}

sealed interface CropPolicy {
    data object None : CropPolicy
    data class CenterCrop(val width: Int, val height: Int) : CropPolicy
    data class CenterAspectFit(val width: Int, val height: Int) : CropPolicy
    data class Letterbox(
        val width: Int,
        val height: Int,
        val fillRed: Int = 0,
        val fillGreen: Int = 0,
        val fillBlue: Int = 0
    ) : CropPolicy
}

sealed interface ResizePolicy {
    data object None : ResizePolicy
    data class Bilinear(val width: Int, val height: Int) : ResizePolicy
}

sealed interface NormalizationSpec {
    data object None : NormalizationSpec
    data object ZeroToOne : NormalizationSpec
    data object MinusOneToOne : NormalizationSpec
    data class MeanStd(val mean: FloatArray, val std: FloatArray) : NormalizationSpec
}
