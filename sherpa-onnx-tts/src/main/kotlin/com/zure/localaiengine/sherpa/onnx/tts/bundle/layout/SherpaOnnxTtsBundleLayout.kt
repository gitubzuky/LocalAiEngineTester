package com.zure.localaiengine.sherpa.onnx.tts.bundle.layout

import com.zure.localaiengine.sherpa.onnx.tts.bundle.SherpaOnnxTtsBundle
import java.io.File

interface SherpaOnnxTtsBundleLayout {
    val id: String

    fun matches(directory: File): Boolean

    fun resolve(directory: File): SherpaOnnxTtsBundle
}
