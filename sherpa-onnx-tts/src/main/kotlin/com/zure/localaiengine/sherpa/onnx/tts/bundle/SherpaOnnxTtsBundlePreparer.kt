package com.zure.localaiengine.sherpa.onnx.tts.bundle

import android.content.Context
import com.zure.localaiengine.sherpa.onnx.tts.SherpaOnnxTtsInitConfig
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

class SherpaOnnxTtsBundlePreparer(
    private val context: Context
) {
    fun prepare(
        candidate: SherpaOnnxTtsBundleCandidate,
        config: SherpaOnnxTtsInitConfig
    ): SherpaOnnxTtsBundle {
        val bundleDir = when (val source = candidate.source) {
            is SherpaOnnxTtsBundleSource.ExternalDirectory -> source.directory
            is SherpaOnnxTtsBundleSource.AssetZip,
            is SherpaOnnxTtsBundleSource.ExternalZip -> extractZipBundle(candidate, config)
        }
        val layout = config.layouts.firstOrNull { it.matches(bundleDir) }
            ?: error("Unsupported sherpa-onnx TTS bundle layout: ${bundleDir.absolutePath}")
        return layout.resolve(bundleDir)
    }

    private fun extractZipBundle(
        candidate: SherpaOnnxTtsBundleCandidate,
        config: SherpaOnnxTtsInitConfig
    ): File {
        val cacheRoot = config.cacheDir ?: File(context.filesDir, "models_cache/${config.cacheNamespace}")
        val targetDir = File(cacheRoot, candidate.displayName.safeFileName())
        val sourceSignature = candidate.sourceSignature()
        val markerFile = File(targetDir, BUNDLE_MARKER_FILE)
        val cachedBundleDir = normalizeBundleRoot(targetDir, config)
        if (cachedBundleDir != null && markerFile.readTextOrNull() == sourceSignature) {
            return cachedBundleDir
        }

        targetDir.deleteRecursively()
        targetDir.mkdirs()
        val targetCanonical = targetDir.canonicalFile
        openZipInput(candidate.source).use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val targetFile = File(targetDir, entry.name).canonicalFile
                        require(targetFile.path.startsWith(targetCanonical.path + File.separator)) {
                            "Unsafe zip entry path: ${entry.name}"
                        }
                        targetFile.parentFile?.mkdirs()
                        targetFile.outputStream().use { output -> zip.copyTo(output) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val bundleDir = normalizeBundleRoot(targetDir, config)
            ?: error("Zip does not contain a supported sherpa-onnx TTS bundle: ${candidate.displayName}")
        markerFile.writeText(sourceSignature)
        return bundleDir
    }

    private fun openZipInput(source: SherpaOnnxTtsBundleSource): InputStream {
        return when (source) {
            is SherpaOnnxTtsBundleSource.AssetZip -> context.assets.open(source.assetPath)
            is SherpaOnnxTtsBundleSource.ExternalZip -> source.file.inputStream()
            is SherpaOnnxTtsBundleSource.ExternalDirectory -> {
                error("External directory does not need zip extraction: ${source.directory.absolutePath}")
            }
        }
    }

    private fun normalizeBundleRoot(targetDir: File, config: SherpaOnnxTtsInitConfig): File? {
        if (config.layouts.any { it.matches(targetDir) }) return targetDir
        return targetDir.listFiles()
            ?.firstOrNull { child -> child.isDirectory && config.layouts.any { it.matches(child) } }
    }

    private fun SherpaOnnxTtsBundleCandidate.sourceSignature(): String {
        return when (val source = source) {
            is SherpaOnnxTtsBundleSource.AssetZip -> {
                "assets|${source.assetPath}|${assetLengthOrNull(source.assetPath) ?: -1L}|$BUNDLE_CACHE_VERSION"
            }
            is SherpaOnnxTtsBundleSource.ExternalZip -> {
                "external|${source.file.absolutePath}|${source.file.length()}|${source.file.lastModified()}"
            }
            is SherpaOnnxTtsBundleSource.ExternalDirectory -> {
                "directory|${source.directory.absolutePath}|${source.directory.lastModified()}"
            }
        }
    }

    private fun assetLengthOrNull(assetPath: String): Long? {
        return runCatching {
            context.assets.openFd(assetPath).use { it.length }
        }.getOrNull()
    }

    private fun File.readTextOrNull(): String? {
        return runCatching { readText() }.getOrNull()
    }

    private fun String.safeFileName(): String {
        return replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "bundle" }
    }

    private companion object {
        const val BUNDLE_MARKER_FILE = ".bundle_source"
        const val BUNDLE_CACHE_VERSION = "1"
    }
}
