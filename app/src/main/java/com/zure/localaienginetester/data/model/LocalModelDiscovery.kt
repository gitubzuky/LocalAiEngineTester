package com.zure.localaienginetester.data.model

import android.content.Context
import com.zure.localaiengine.core.engine.EngineDescriptor
import com.zure.localaiengine.core.model.ModelFormat
import com.zure.localaiengine.sherpa.onnx.tts.SherpaOnnxTtsInitConfig
import com.zure.localaiengine.sherpa.onnx.tts.bundle.SherpaOnnxTtsBundleCandidate
import com.zure.localaiengine.sherpa.onnx.tts.bundle.SherpaOnnxTtsBundleDiscovery
import com.zure.localaiengine.sherpa.onnx.tts.bundle.SherpaOnnxTtsBundlePreparer
import com.zure.localaiengine.sherpa.onnx.tts.bundle.SherpaOnnxTtsBundleSource
import com.zure.localaienginetester.domain.entity.LocalModel
import com.zure.localaienginetester.domain.entity.ModelSource
import com.zure.localaienginetester.util.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalModelDiscovery @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun discover(engine: EngineDescriptor): ModelDiscoveryResult {
        if (engine.id == SHERPA_ONNX_ENGINE_ID) {
            return discoverSherpaOnnxTts()
        }

        val supportedExtensions = engine.supportedFormats
            .mapNotNull { format -> format.fileExtensionOrNull()?.let { it to format } }
            .toMap()

        val externalDirectory = ensureExternalModelDirectory(engine.id)
        if (supportedExtensions.isEmpty()) {
            return ModelDiscoveryResult(
                models = emptyList(),
                externalDirectoryPath = externalDirectory.path,
                externalDirectoryError = externalDirectory.error
            )
        }

        return ModelDiscoveryResult(
            models = discoverExternal(engine.id, supportedExtensions, externalDirectory.directory) +
                discoverAssets(engine.id, supportedExtensions),
            externalDirectoryPath = externalDirectory.path,
            externalDirectoryError = externalDirectory.error
        )
    }

    fun prepareModelFile(model: LocalModel): File {
        return when (model.source) {
            ModelSource.External -> File(model.path)
            ModelSource.Assets -> copyAssetModel(model)
        }
    }

    fun prepareModelBundle(model: LocalModel): File {
        require(model.engineId == SHERPA_ONNX_ENGINE_ID) {
            "Only sherpa-onnx TTS models are prepared as bundles: ${model.engineId}"
        }
        val config = sherpaTtsInitConfig()
        val candidate = model.toSherpaCandidate()
        return SherpaOnnxTtsBundlePreparer(context)
            .prepare(candidate, config)
            .rootDir
    }

    private fun discoverExternal(
        engineId: String,
        supportedExtensions: Map<String, ModelFormat>,
        modelDir: File?
    ): List<LocalModel> {
        if (modelDir == null || !modelDir.isDirectory) {
            return emptyList()
        }

        return modelDir.listFiles()
            ?.flatMap { file ->
                when {
                    file.isFile -> {
                        val format = supportedExtensions[file.extension.lowercase()] ?: return@flatMap emptyList()
                        listOf(
                            LocalModel(
                                id = "external:${file.absolutePath}",
                                engineId = engineId,
                                name = file.name,
                                format = format,
                                source = ModelSource.External,
                                path = file.absolutePath,
                                sizeBytes = file.length()
                            )
                        )
                    }
                    else -> emptyList()
                }
            }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    private fun ensureExternalModelDirectory(engineId: String): ExternalModelDirectory {
        val modelDir = context.getExternalFilesDir("models/$engineId")
        if (modelDir == null) {
            val message = "External files directory is unavailable for engine: $engineId"
            AppLog.w(TAG, message)
            return ExternalModelDirectory(
                directory = null,
                path = null,
                error = message
            )
        }

        val mkdirsResult = modelDir.mkdirs()
        val exists = modelDir.exists()
        val isDirectory = modelDir.isDirectory
        AppLog.i(
            TAG,
            "external model dir engineId=$engineId path=${modelDir.absolutePath} " +
                "mkdirs=$mkdirsResult exists=$exists isDirectory=$isDirectory"
        )

        if (!isDirectory) {
            return ExternalModelDirectory(
                directory = null,
                path = modelDir.absolutePath,
                error = "模型目录创建失败：${modelDir.absolutePath}"
            )
        }

        return ExternalModelDirectory(
            directory = modelDir,
            path = modelDir.absolutePath,
            error = null
        )
    }

    private fun discoverAssets(
        engineId: String,
        supportedExtensions: Map<String, ModelFormat>
    ): List<LocalModel> {
        val assetDir = "models/$engineId"
        val modelAssets = context.assets.list(assetDir)
            ?.mapNotNull { fileName ->
                val format = supportedExtensions[fileName.substringAfterLast('.', "").lowercase()]
                    ?: return@mapNotNull null
                val assetPath = "$assetDir/$fileName"
                LocalModel(
                    id = "assets:$assetPath",
                    engineId = engineId,
                    name = fileName,
                    format = format,
                    source = ModelSource.Assets,
                    path = assetPath
                )
            }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
        return modelAssets
    }

    private fun copyAssetModel(model: LocalModel): File {
        val targetDir = File(context.filesDir, "models_cache/${model.engineId}")
        targetDir.mkdirs()
        val targetFile = File(targetDir, model.name)
        val assetLength = runCatching {
            context.assets.openFd(model.path).use { it.length }
        }.getOrNull()

        if (targetFile.exists() && (assetLength == null || targetFile.length() == assetLength)) {
            return targetFile
        }

        // Native engines require a filesystem path, so APK assets are materialized once.
        context.assets.open(model.path).use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return targetFile
    }

    private fun discoverSherpaOnnxTts(): ModelDiscoveryResult {
        val externalDirectory = ensureExternalTtsDirectory()
        val config = sherpaTtsInitConfig(externalDirectory.directory)
        val models = SherpaOnnxTtsBundleDiscovery(context)
            .discover(config)
            .map { candidate -> candidate.toLocalModel() }
        return ModelDiscoveryResult(
            models = models,
            externalDirectoryPath = externalDirectory.path,
            externalDirectoryError = externalDirectory.error
        )
    }

    private fun sherpaTtsInitConfig(
        externalDirectory: File? = context.getExternalFilesDir(SHERPA_TTS_EXTERNAL_DIR)
    ): SherpaOnnxTtsInitConfig {
        return SherpaOnnxTtsInitConfig(
            assetDirs = SHERPA_TTS_ASSET_DIRS,
            externalDirs = listOfNotNull(externalDirectory)
        )
    }

    private fun ensureExternalTtsDirectory(): ExternalModelDirectory {
        val ttsDir = context.getExternalFilesDir(SHERPA_TTS_EXTERNAL_DIR)
        if (ttsDir == null) {
            val message = "External files directory is unavailable for sherpa-onnx TTS."
            AppLog.w(TAG, message)
            return ExternalModelDirectory(
                directory = null,
                path = null,
                error = message
            )
        }

        val mkdirsResult = ttsDir.mkdirs()
        val exists = ttsDir.exists()
        val isDirectory = ttsDir.isDirectory
        AppLog.i(
            TAG,
            "external sherpa tts dir path=${ttsDir.absolutePath} " +
                "mkdirs=$mkdirsResult exists=$exists isDirectory=$isDirectory"
        )

        if (!isDirectory) {
            return ExternalModelDirectory(
                directory = null,
                path = ttsDir.absolutePath,
                error = "TTS 模型目录创建失败：${ttsDir.absolutePath}"
            )
        }

        return ExternalModelDirectory(
            directory = ttsDir,
            path = ttsDir.absolutePath,
            error = null
        )
    }

    private fun SherpaOnnxTtsBundleCandidate.toLocalModel(): LocalModel {
        val candidateSource = source
        val sourcePath = when (candidateSource) {
            is SherpaOnnxTtsBundleSource.AssetZip -> candidateSource.assetPath
            is SherpaOnnxTtsBundleSource.ExternalDirectory -> candidateSource.directory.absolutePath
            is SherpaOnnxTtsBundleSource.ExternalZip -> candidateSource.file.absolutePath
        }
        val modelSource = when (candidateSource) {
            is SherpaOnnxTtsBundleSource.AssetZip -> ModelSource.Assets
            is SherpaOnnxTtsBundleSource.ExternalDirectory,
            is SherpaOnnxTtsBundleSource.ExternalZip -> ModelSource.External
        }
        val sourcePrefix = when (candidateSource) {
            is SherpaOnnxTtsBundleSource.AssetZip -> "assets"
            is SherpaOnnxTtsBundleSource.ExternalDirectory -> "external-dir"
            is SherpaOnnxTtsBundleSource.ExternalZip -> "external-zip"
        }
        return LocalModel(
            id = "$sourcePrefix:$sourcePath",
            engineId = SHERPA_ONNX_ENGINE_ID,
            name = displayName,
            format = ModelFormat.ONNX,
            source = modelSource,
            path = sourcePath,
            sizeBytes = sizeBytes
        )
    }

    private fun LocalModel.toSherpaCandidate(): SherpaOnnxTtsBundleCandidate {
        val source = when (source) {
            ModelSource.Assets -> SherpaOnnxTtsBundleSource.AssetZip(path)
            ModelSource.External -> {
                val file = File(path)
                when {
                    file.isDirectory -> SherpaOnnxTtsBundleSource.ExternalDirectory(file)
                    file.isFile -> SherpaOnnxTtsBundleSource.ExternalZip(file)
                    else -> error("sherpa-onnx TTS bundle source does not exist: $path")
                }
            }
        }
        return SherpaOnnxTtsBundleCandidate(
            displayName = name,
            source = source,
            sizeBytes = sizeBytes
        )
    }

    private fun ModelFormat.fileExtensionOrNull(): String? {
        return when (this) {
            ModelFormat.GGUF -> "gguf"
            ModelFormat.TFLITE -> "tflite"
            ModelFormat.ONNX -> "onnx"
            else -> null
        }
    }

    private data class ExternalModelDirectory(
        val directory: File?,
        val path: String?,
        val error: String?
    )

    companion object {
        private const val TAG = "LocalModelDiscovery"
        private const val SHERPA_ONNX_ENGINE_ID = "sherpa-onnx"
        private const val SHERPA_TTS_EXTERNAL_DIR = "tts"
        private val SHERPA_TTS_ASSET_DIRS = listOf("res/tts", "tts")
    }
}

data class ModelDiscoveryResult(
    val models: List<LocalModel>,
    val externalDirectoryPath: String?,
    val externalDirectoryError: String?
)
