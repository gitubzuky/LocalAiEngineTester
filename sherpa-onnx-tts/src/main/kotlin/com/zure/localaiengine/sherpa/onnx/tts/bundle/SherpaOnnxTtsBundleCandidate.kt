package com.zure.localaiengine.sherpa.onnx.tts.bundle

import java.io.File

data class SherpaOnnxTtsBundleCandidate(
    val displayName: String,
    val source: SherpaOnnxTtsBundleSource,
    val sizeBytes: Long? = null
)

sealed interface SherpaOnnxTtsBundleSource {
    data class AssetZip(val assetPath: String) : SherpaOnnxTtsBundleSource
    data class ExternalZip(val file: File) : SherpaOnnxTtsBundleSource
    data class ExternalDirectory(val directory: File) : SherpaOnnxTtsBundleSource
}
