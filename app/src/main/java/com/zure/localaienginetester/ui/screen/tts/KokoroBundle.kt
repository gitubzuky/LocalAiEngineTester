package com.zure.localaienginetester.ui.screen.tts

import com.zure.localaiengine.core.engine.EngineArtifactKeys
import java.io.File

data class KokoroBundle(
    val rootDir: File,
    val modelFile: File,
    val voicesFile: File,
    val tokensFile: File,
    val espeakDataDir: File?,
    val lexiconZh: File?,
    val lexiconUsEn: File?,
    val lexiconGbEn: File?,
    val dateZhFst: File?,
    val numberZhFst: File?,
    val phoneZhFst: File?,
    val dictDir: File?
) {
    val artifacts: Map<String, String>
        get() = buildMap {
            put(EngineArtifactKeys.VOICES, voicesFile.absolutePath)
            put(EngineArtifactKeys.TOKENS, tokensFile.absolutePath)
            espeakDataDir?.let { put(EngineArtifactKeys.DATA_DIR, it.absolutePath) }
            lexiconZh?.let { put("lexicon.zh", it.absolutePath) }
            lexiconUsEn?.let { put("lexicon.usEn", it.absolutePath) }
            lexiconGbEn?.let { put("lexicon.gbEn", it.absolutePath) }
            dateZhFst?.let { put("normalizer.dateZh", it.absolutePath) }
            numberZhFst?.let { put("normalizer.numberZh", it.absolutePath) }
            phoneZhFst?.let { put("normalizer.phoneZh", it.absolutePath) }
            dictDir?.let { put("dictDir", it.absolutePath) }
        }

    fun validate() {
        require(rootDir.isDirectory) { "Kokoro bundle directory does not exist: ${rootDir.absolutePath}" }
        require(modelFile.isFile) { "Missing Kokoro model.onnx: ${modelFile.absolutePath}" }
        require(voicesFile.isFile) { "Missing Kokoro voices.bin: ${voicesFile.absolutePath}" }
        require(tokensFile.isFile) { "Missing Kokoro tokens.txt: ${tokensFile.absolutePath}" }
    }

    companion object {
        fun fromPath(path: String): KokoroBundle {
            val root = File(path)
            return KokoroBundle(
                rootDir = root,
                modelFile = File(root, "model.onnx"),
                voicesFile = File(root, "voices.bin"),
                tokensFile = File(root, "tokens.txt"),
                espeakDataDir = File(root, "espeak-ng-data").takeIf { it.isDirectory },
                lexiconZh = File(root, "lexicon-zh.txt").takeIf { it.isFile },
                lexiconUsEn = File(root, "lexicon-us-en.txt").takeIf { it.isFile },
                lexiconGbEn = File(root, "lexicon-gb-en.txt").takeIf { it.isFile },
                dateZhFst = File(root, "date-zh.fst").takeIf { it.isFile },
                numberZhFst = File(root, "number-zh.fst").takeIf { it.isFile },
                phoneZhFst = File(root, "phone-zh.fst").takeIf { it.isFile },
                dictDir = File(root, "dict").takeIf { it.isDirectory }
            )
        }
    }
}
