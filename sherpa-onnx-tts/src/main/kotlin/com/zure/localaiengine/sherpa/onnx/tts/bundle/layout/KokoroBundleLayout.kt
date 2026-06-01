package com.zure.localaiengine.sherpa.onnx.tts.bundle.layout

import com.zure.localaiengine.sherpa.onnx.tts.bundle.SherpaOnnxTtsBundle
import java.io.File

object KokoroBundleLayout : SherpaOnnxTtsBundleLayout {
    override val id: String = "kokoro"

    override fun matches(directory: File): Boolean {
        return directory.isDirectory &&
            File(directory, "model.onnx").isFile &&
            File(directory, "voices.bin").isFile &&
            File(directory, "tokens.txt").isFile
    }

    override fun resolve(directory: File): SherpaOnnxTtsBundle {
        require(matches(directory)) {
            "Kokoro bundle is incomplete: ${directory.absolutePath}. Required files: model.onnx, voices.bin, tokens.txt."
        }
        return SherpaOnnxTtsBundle(
            rootDir = directory,
            layoutId = id,
            modelFile = File(directory, "model.onnx"),
            voicesFile = File(directory, "voices.bin"),
            tokensFile = File(directory, "tokens.txt"),
            dataDir = File(directory, "espeak-ng-data").takeIf { it.isDirectory },
            dictDir = File(directory, "dict").takeIf { it.isDirectory },
            lexiconFiles = listOf(
                "lexicon-us-en.txt",
                "lexicon-gb-en.txt",
                "lexicon-zh.txt"
            ).mapNotNull { name -> File(directory, name).takeIf { it.isFile } },
            normalizerFiles = listOf(
                "date-zh.fst",
                "number-zh.fst",
                "phone-zh.fst"
            ).mapNotNull { name -> File(directory, name).takeIf { it.isFile } },
            extraFiles = buildMap {
                File(directory, "espeak-ng-data").takeIf { it.isDirectory }?.let { put("dataDir", it) }
                File(directory, "dict").takeIf { it.isDirectory }?.let { put("dictDir", it) }
            }
        )
    }
}
