package com.zure.localaiengine.camera.analysis.profiles

import com.zure.localaiengine.camera.analysis.api.CropPolicy
import com.zure.localaiengine.camera.analysis.api.NormalizationSpec
import com.zure.localaiengine.camera.analysis.api.PixelOrder
import com.zure.localaiengine.camera.analysis.api.ResizePolicy
import com.zure.localaiengine.camera.analysis.api.TensorDataType
import com.zure.localaiengine.camera.analysis.api.TensorLayout

object YoloPersonProfile {
    fun create(
        inputName: String? = null,
        inputSize: Int = 640
    ): GenericVisionInputProfile {
        return GenericVisionInputProfile(
            id = "yolo_person",
            inputName = inputName,
            inputShape = intArrayOf(1, inputSize, inputSize, 3),
            tensorLayout = TensorLayout.NHWC,
            tensorDataType = TensorDataType.Float32,
            pixelOrder = PixelOrder.RGB,
            cropPolicy = CropPolicy.Letterbox(
                width = inputSize,
                height = inputSize,
                fillRed = 114,
                fillGreen = 114,
                fillBlue = 114
            ),
            resizePolicy = ResizePolicy.None,
            normalization = NormalizationSpec.ZeroToOne
        )
    }
}
