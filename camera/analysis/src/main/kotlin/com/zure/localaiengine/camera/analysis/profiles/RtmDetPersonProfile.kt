package com.zure.localaiengine.camera.analysis.profiles

import com.zure.localaiengine.camera.analysis.api.CropPolicy
import com.zure.localaiengine.camera.analysis.api.NormalizationSpec
import com.zure.localaiengine.camera.analysis.api.PixelOrder
import com.zure.localaiengine.camera.analysis.api.ResizePolicy
import com.zure.localaiengine.camera.analysis.api.TensorDataType
import com.zure.localaiengine.camera.analysis.api.TensorLayout

object RtmDetPersonProfile {
    fun create(
        inputName: String? = null,
        inputSize: Int = 640,
        preprocessMode: RtmDetPersonPreprocessMode = RtmDetPersonPreprocessMode.RgbNone
    ): GenericVisionInputProfile {
        return GenericVisionInputProfile(
            id = "rtmdet_person_${preprocessMode.idSuffix}",
            inputName = inputName,
            inputShape = intArrayOf(1, inputSize, inputSize, 3),
            tensorLayout = TensorLayout.NHWC,
            tensorDataType = TensorDataType.Float32,
            pixelOrder = preprocessMode.pixelOrder,
            cropPolicy = CropPolicy.Letterbox(width = inputSize, height = inputSize, fillRed = 114, fillGreen = 114, fillBlue = 114),
            resizePolicy = ResizePolicy.None,
            normalization = preprocessMode.normalization
        )
    }
}

enum class RtmDetPersonPreprocessMode(
    val idSuffix: String,
    val label: String,
    val pixelOrder: PixelOrder,
    val normalization: NormalizationSpec
) {
    RgbNone(
        idSuffix = "rgb_none",
        label = "RGB/None",
        pixelOrder = PixelOrder.RGB,
        normalization = NormalizationSpec.None
    ),
    BgrNone(
        idSuffix = "bgr_none",
        label = "BGR/None",
        pixelOrder = PixelOrder.BGR,
        normalization = NormalizationSpec.None
    ),
    RgbMeanStd(
        idSuffix = "rgb_meanstd",
        label = "RGB/MeanStd",
        pixelOrder = PixelOrder.RGB,
        normalization = NormalizationSpec.MeanStd(
            mean = floatArrayOf(123.675f, 116.28f, 103.53f),
            std = floatArrayOf(58.395f, 57.12f, 57.375f)
        )
    ),
    BgrMeanStd(
        idSuffix = "bgr_meanstd",
        label = "BGR/MeanStd",
        pixelOrder = PixelOrder.BGR,
        normalization = NormalizationSpec.MeanStd(
            mean = floatArrayOf(103.53f, 116.28f, 123.675f),
            std = floatArrayOf(57.375f, 57.12f, 58.395f)
        )
    );

    fun next(): RtmDetPersonPreprocessMode {
        val modes = entries
        return modes[(ordinal + 1) % modes.size]
    }
}
