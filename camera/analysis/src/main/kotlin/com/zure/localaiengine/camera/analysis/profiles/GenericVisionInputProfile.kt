package com.zure.localaiengine.camera.analysis.profiles

import com.zure.localaiengine.camera.analysis.api.CameraAnalysisConfig
import com.zure.localaiengine.camera.analysis.api.CropPolicy
import com.zure.localaiengine.camera.analysis.api.NormalizationSpec
import com.zure.localaiengine.camera.analysis.api.PixelOrder
import com.zure.localaiengine.camera.analysis.api.PreprocessBackend
import com.zure.localaiengine.camera.analysis.api.ResizePolicy
import com.zure.localaiengine.camera.analysis.api.TensorDataType
import com.zure.localaiengine.camera.analysis.api.TensorLayout
import com.zure.localaiengine.camera.analysis.api.VisionInputProfile
import com.zure.localaiengine.camera.analysis.nativebackend.NativePreprocessor
import com.zure.localaiengine.camera.analysis.pipeline.FrameProcessorStep
import com.zure.localaiengine.camera.analysis.steps.CropStep
import com.zure.localaiengine.camera.analysis.steps.NativeFusedTensorStep
import com.zure.localaiengine.camera.analysis.steps.NormalizeStep
import com.zure.localaiengine.camera.analysis.steps.OrientationStep
import com.zure.localaiengine.camera.analysis.steps.ResizeStep
import com.zure.localaiengine.camera.analysis.steps.TensorPackStep
import com.zure.localaiengine.camera.analysis.steps.YuvToRgbStep

data class GenericVisionInputProfile(
    override val id: String,
    override val inputName: String? = null,
    override val inputShape: IntArray,
    override val tensorLayout: TensorLayout = TensorLayout.NHWC,
    override val tensorDataType: TensorDataType = TensorDataType.Float32,
    override val pixelOrder: PixelOrder = PixelOrder.RGB,
    override val cropPolicy: CropPolicy,
    override val resizePolicy: ResizePolicy,
    override val normalization: NormalizationSpec
) : VisionInputProfile {
    override fun createPipeline(config: CameraAnalysisConfig): List<FrameProcessorStep> {
        val fallback = createKotlinPipeline()
        return when (config.backend) {
            PreprocessBackend.Kotlin -> fallback
            PreprocessBackend.Native -> listOf(NativeFusedTensorStep(fallback))
            PreprocessBackend.Auto -> if (NativePreprocessor.isLoaded) {
                listOf(NativeFusedTensorStep(fallback))
            } else {
                fallback
            }
        }
    }

    private fun createKotlinPipeline(): List<FrameProcessorStep> {
        return listOf(
            YuvToRgbStep(),
            OrientationStep(),
            CropStep(cropPolicy),
            ResizeStep(resizePolicy),
            NormalizeStep(normalization),
            TensorPackStep()
        )
    }
}
