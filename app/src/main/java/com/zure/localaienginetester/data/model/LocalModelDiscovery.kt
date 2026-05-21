package com.zure.localaienginetester.data.model

import android.content.Context
import com.zure.localaiengine.core.engine.EngineDescriptor
import com.zure.localaiengine.core.model.ModelFormat
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

    private fun discoverExternal(
        engineId: String,
        supportedExtensions: Map<String, ModelFormat>,
        modelDir: File?
    ): List<LocalModel> {
        if (modelDir == null || !modelDir.isDirectory) {
            return emptyList()
        }

        return modelDir.listFiles()
            ?.filter { it.isFile }
            ?.mapNotNull { file ->
                val format = supportedExtensions[file.extension.lowercase()] ?: return@mapNotNull null
                LocalModel(
                    id = "external:${file.absolutePath}",
                    engineId = engineId,
                    name = file.name,
                    format = format,
                    source = ModelSource.External,
                    path = file.absolutePath,
                    sizeBytes = file.length()
                )
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
        return context.assets.list(assetDir)
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
    }
}

data class ModelDiscoveryResult(
    val models: List<LocalModel>,
    val externalDirectoryPath: String?,
    val externalDirectoryError: String?
)
