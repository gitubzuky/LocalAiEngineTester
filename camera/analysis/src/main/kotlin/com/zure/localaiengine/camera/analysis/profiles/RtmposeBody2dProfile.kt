package com.zure.localaiengine.camera.analysis.profiles

import com.zure.localaiengine.camera.analysis.api.CropPolicy
import com.zure.localaiengine.camera.analysis.api.NormalizationSpec
import com.zure.localaiengine.camera.analysis.api.PixelOrder
import com.zure.localaiengine.camera.analysis.api.ResizePolicy
import com.zure.localaiengine.camera.analysis.api.TensorDataType
import com.zure.localaiengine.camera.analysis.api.TensorLayout

object RtmposeBody2dProfile {
    fun create(
        inputName: String? = null,
        cropPolicy: CropPolicy = CropPolicy.CenterCrop(width = 360, height = 480)
    ): GenericVisionInputProfile {
        return GenericVisionInputProfile(
            id = "rtmpose_body2d",
            inputName = inputName,
            inputShape = intArrayOf(1, 3, 256, 192),
            tensorLayout = TensorLayout.NCHW,
            tensorDataType = TensorDataType.Float32,
            pixelOrder = PixelOrder.RGB,
            cropPolicy = cropPolicy,
            resizePolicy = ResizePolicy.Bilinear(width = 192, height = 256),
            normalization = NormalizationSpec.MeanStd(
                mean = floatArrayOf(123.675f, 116.28f, 103.53f),
                std = floatArrayOf(58.395f, 57.12f, 57.375f)
            )
        )
    }
}
