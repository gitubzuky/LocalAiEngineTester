package com.zure.localaiengine.sherpa.onnx.tts.bundle

import java.io.File

data class SherpaOnnxTtsBundle(
    val rootDir: File,
    val layoutId: String,
    val modelFile: File,
    val voicesFile: File? = null,
    val tokensFile: File? = null,
    val dataDir: File? = null,
    val dictDir: File? = null,
    val lexiconFiles: List<File> = emptyList(),
    val normalizerFiles: List<File> = emptyList(),
    val extraFiles: Map<String, File> = emptyMap()
) {
    fun validateRequired(vararg relativePaths: String) {
        require(rootDir.isDirectory) {
            "Sherpa-onnx TTS bundle directory does not exist: ${rootDir.absolutePath}"
        }
        relativePaths.forEach { relativePath ->
            val file = File(rootDir, relativePath)
            require(file.isFile) {
                "Missing required sherpa-onnx TTS bundle file: ${file.absolutePath}"
            }
        }
    }
}
