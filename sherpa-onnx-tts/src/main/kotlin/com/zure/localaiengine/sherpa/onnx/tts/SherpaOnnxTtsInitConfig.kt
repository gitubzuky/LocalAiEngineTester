package com.zure.localaiengine.sherpa.onnx.tts

import com.zure.localaiengine.sherpa.onnx.tts.bundle.layout.KokoroBundleLayout
import com.zure.localaiengine.sherpa.onnx.tts.bundle.layout.SherpaOnnxTtsBundleLayout
import java.io.File

data class SherpaOnnxTtsInitConfig(
    val assetDirs: List<String> = listOf("res/tts", "tts"),
    val externalDirs: List<File> = emptyList(),
    val explicitBundlePath: File? = null,
    val preferredBundleName: String? = null,
    val cacheDir: File? = null,
    val cacheNamespace: String = "sherpa-onnx-tts",
    val layouts: List<SherpaOnnxTtsBundleLayout> = listOf(KokoroBundleLayout),
    val zipNameMatcher: SherpaOnnxTtsZipNameMatcher = SherpaOnnxTtsZipNameMatcher { name ->
        name.endsWith(".zip", ignoreCase = true)
    }
)

fun interface SherpaOnnxTtsZipNameMatcher {
    fun matches(fileName: String): Boolean
}
