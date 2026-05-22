package com.zure.localaiengine.camera.analysis.steps

import com.zure.localaiengine.camera.analysis.api.TensorLayout
import com.zure.localaiengine.camera.analysis.api.VisionInputProfile

internal val VisionInputProfile.inputHeight: Int
    get() = when (tensorLayout) {
        TensorLayout.NHWC -> inputShape.getOrNull(1) ?: 0
        TensorLayout.NCHW -> inputShape.getOrNull(2) ?: 0
    }

internal val VisionInputProfile.inputWidth: Int
    get() = when (tensorLayout) {
        TensorLayout.NHWC -> inputShape.getOrNull(2) ?: 0
        TensorLayout.NCHW -> inputShape.getOrNull(3) ?: 0
    }
