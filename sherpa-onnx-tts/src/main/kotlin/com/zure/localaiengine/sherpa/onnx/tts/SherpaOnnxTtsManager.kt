package com.zure.localaiengine.sherpa.onnx.tts

import android.annotation.TargetApi
import android.content.Context
import android.net.Uri
import android.os.Build
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.zure.localaiengine.sherpa.onnx.tts.audio.SherpaOnnxAudioPlayer
import com.zure.localaiengine.sherpa.onnx.tts.audio.SherpaOnnxGeneratedAudio
import com.zure.localaiengine.sherpa.onnx.tts.audio.SherpaOnnxWavSaver
import com.zure.localaiengine.sherpa.onnx.tts.bundle.SherpaOnnxTtsBundle
import com.zure.localaiengine.sherpa.onnx.tts.bundle.SherpaOnnxTtsBundleDiscovery
import com.zure.localaiengine.sherpa.onnx.tts.bundle.SherpaOnnxTtsBundlePreparer
import java.io.File
import kotlin.system.measureTimeMillis

class SherpaOnnxTtsManager(
    private val context: Context
) {
    private val player = SherpaOnnxAudioPlayer()
    private var activeBundle: SherpaOnnxTtsBundle? = null
    private var activeTts: OfflineTts? = null

    fun init(config: SherpaOnnxTtsInitConfig = SherpaOnnxTtsInitConfig()): SherpaOnnxTtsBundle {
        val candidates = SherpaOnnxTtsBundleDiscovery(context).discover(config)
        val candidate = selectCandidate(candidates, config)
        val bundle = SherpaOnnxTtsBundlePreparer(context).prepare(candidate, config)
        activeBundle = bundle
        return bundle
    }

    fun createOfflineTtsConfig(
        bundle: SherpaOnnxTtsBundle = requireActiveBundle(),
        modelConfig: SherpaOnnxTtsModelConfig = SherpaOnnxTtsModelConfig.Kokoro()
    ): OfflineTtsConfig {
        return when (modelConfig) {
            is SherpaOnnxTtsModelConfig.Kokoro -> createKokoroConfig(bundle, modelConfig)
        }
    }

    fun createOfflineTts(config: OfflineTtsConfig): OfflineTts {
        activeTts?.release()
        return OfflineTts(config = config).also { activeTts = it }
    }

    fun generate(
        tts: OfflineTts = requireActiveTts(),
        text: String,
        sid: Int = 0,
        speed: Float = 1.0f
    ): SherpaOnnxGeneratedAudio {
        require(text.isNotBlank()) { "TTS text must not be blank." }
        lateinit var samples: FloatArray
        var sampleRate = 0
        val elapsedMillis = measureTimeMillis {
            val audio = tts.generate(text = text, sid = sid, speed = speed)
            samples = audio.samples
            sampleRate = audio.sampleRate
        }
        return SherpaOnnxGeneratedAudio(
            samples = samples,
            sampleRate = sampleRate,
            channels = 1,
            elapsedMillis = elapsedMillis,
            metadata = mapOf("engine" to "sherpa-onnx")
        )
    }

    fun play(audio: SherpaOnnxGeneratedAudio) {
        player.play(audio)
    }

    fun stopPlayback() {
        player.stop()
    }

    fun save(audio: SherpaOnnxGeneratedAudio, outputFile: File): File {
        return SherpaOnnxWavSaver.save(audio, outputFile)
    }

    @TargetApi(Build.VERSION_CODES.Q)
    fun saveToExternalDownloads(
        audio: SherpaOnnxGeneratedAudio,
        relativeDir: String,
        fileName: String
    ): Uri {
        return SherpaOnnxWavSaver.saveToExternalDownloads(
            context = context,
            audio = audio,
            relativeDir = relativeDir,
            fileName = fileName
        )
    }

    fun release() {
        activeTts?.release()
        activeTts = null
        player.release()
    }

    private fun selectCandidate(
        candidates: List<com.zure.localaiengine.sherpa.onnx.tts.bundle.SherpaOnnxTtsBundleCandidate>,
        config: SherpaOnnxTtsInitConfig
    ): com.zure.localaiengine.sherpa.onnx.tts.bundle.SherpaOnnxTtsBundleCandidate {
        require(candidates.isNotEmpty()) {
            "No sherpa-onnx TTS bundle found. Check assetDirs, externalDirs, or explicitBundlePath."
        }
        val preferredName = config.preferredBundleName
        return if (preferredName.isNullOrBlank()) {
            candidates.first()
        } else {
            candidates.firstOrNull { it.displayName.contains(preferredName, ignoreCase = true) }
                ?: error("Preferred sherpa-onnx TTS bundle was not found: $preferredName")
        }
    }

    private fun createKokoroConfig(
        bundle: SherpaOnnxTtsBundle,
        modelConfig: SherpaOnnxTtsModelConfig.Kokoro
    ): OfflineTtsConfig {
        bundle.validateRequired("model.onnx", "voices.bin", "tokens.txt")
        val dataDir = requireNotNull(bundle.dataDir) {
            "Kokoro sherpa-onnx config requires espeak-ng-data."
        }
        val dictDir = requireNotNull(bundle.dictDir) {
            "Kokoro sherpa-onnx config requires dict directory."
        }
        require(bundle.lexiconFiles.isNotEmpty()) {
            "Kokoro sherpa-onnx config requires at least one lexicon file."
        }
        return OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = bundle.modelFile.absolutePath,
                    voices = requireNotNull(bundle.voicesFile).absolutePath,
                    tokens = requireNotNull(bundle.tokensFile).absolutePath,
                    dataDir = dataDir.absolutePath,
                    dictDir = dictDir.absolutePath,
                    lexicon = bundle.lexiconFiles.joinToString(",") { it.absolutePath },
                    lengthScale = modelConfig.lengthScale
                ),
                numThreads = modelConfig.numThreads,
                debug = modelConfig.debug
            )
        )
    }

    private fun requireActiveBundle(): SherpaOnnxTtsBundle {
        return requireNotNull(activeBundle) {
            "SherpaOnnxTtsManager.init() must be called before creating OfflineTtsConfig."
        }
    }

    private fun requireActiveTts(): OfflineTts {
        return requireNotNull(activeTts) {
            "Call createOfflineTts(config), or pass an OfflineTts instance to generate()."
        }
    }
}
