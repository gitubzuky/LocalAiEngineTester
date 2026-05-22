package com.zure.localaiengine.camera.analysis.api

import com.zure.localaiengine.camera.analysis.pipeline.FrameProcessorStep

interface VisionInputProfile {
    val id: String
    val inputName: String?
    val inputShape: IntArray
    val tensorLayout: TensorLayout
    val tensorDataType: TensorDataType
    val pixelOrder: PixelOrder
    val cropPolicy: CropPolicy
    val resizePolicy: ResizePolicy
    val normalization: NormalizationSpec

    fun createPipeline(config: CameraAnalysisConfig): List<FrameProcessorStep>
}
