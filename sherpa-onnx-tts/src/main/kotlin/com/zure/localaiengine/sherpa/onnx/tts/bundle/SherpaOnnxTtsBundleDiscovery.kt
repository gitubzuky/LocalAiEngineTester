package com.zure.localaiengine.sherpa.onnx.tts.bundle

import android.content.Context
import com.zure.localaiengine.sherpa.onnx.tts.SherpaOnnxTtsInitConfig
import java.io.File

class SherpaOnnxTtsBundleDiscovery(
    private val context: Context
) {
    fun discover(config: SherpaOnnxTtsInitConfig): List<SherpaOnnxTtsBundleCandidate> {
        return buildList {
            config.explicitBundlePath?.let { path ->
                addAll(discoverExplicitPath(path, config))
            }
            addAll(discoverExternal(config))
            addAll(discoverAssets(config))
        }.distinctBy { candidate ->
            when (val source = candidate.source) {
                is SherpaOnnxTtsBundleSource.AssetZip -> "asset:${source.assetPath}"
                is SherpaOnnxTtsBundleSource.ExternalDirectory -> "dir:${source.directory.absolutePath}"
                is SherpaOnnxTtsBundleSource.ExternalZip -> "zip:${source.file.absolutePath}"
            }
        }.sortedBy { it.displayName.lowercase() }
    }

    private fun discoverExplicitPath(
        path: File,
        config: SherpaOnnxTtsInitConfig
    ): List<SherpaOnnxTtsBundleCandidate> {
        return when {
            path.isDirectory && config.layouts.any { it.matches(path) } -> listOf(
                SherpaOnnxTtsBundleCandidate(
                    displayName = path.name,
                    source = SherpaOnnxTtsBundleSource.ExternalDirectory(path),
                    sizeBytes = path.directorySize()
                )
            )
            path.isFile && config.zipNameMatcher.matches(path.name) -> listOf(
                SherpaOnnxTtsBundleCandidate(
                    displayName = path.nameWithoutExtension,
                    source = SherpaOnnxTtsBundleSource.ExternalZip(path),
                    sizeBytes = path.length()
                )
            )
            else -> emptyList()
        }
    }

    private fun discoverExternal(config: SherpaOnnxTtsInitConfig): List<SherpaOnnxTtsBundleCandidate> {
        return config.externalDirs
            .filter { it.isDirectory }
            .flatMap { directory ->
                directory.listFiles()
                    ?.flatMap { file ->
                        when {
                            file.isDirectory && config.layouts.any { it.matches(file) } -> listOf(
                                SherpaOnnxTtsBundleCandidate(
                                    displayName = file.name,
                                    source = SherpaOnnxTtsBundleSource.ExternalDirectory(file),
                                    sizeBytes = file.directorySize()
                                )
                            )
                            file.isFile && config.zipNameMatcher.matches(file.name) -> listOf(
                                SherpaOnnxTtsBundleCandidate(
                                    displayName = file.nameWithoutExtension,
                                    source = SherpaOnnxTtsBundleSource.ExternalZip(file),
                                    sizeBytes = file.length()
                                )
                            )
                            else -> emptyList()
                        }
                    }
                    ?: emptyList()
            }
    }

    private fun discoverAssets(config: SherpaOnnxTtsInitConfig): List<SherpaOnnxTtsBundleCandidate> {
        return config.assetDirs.flatMap { assetDir ->
            context.assets.listOrEmpty(assetDir)
                .filter { fileName -> config.zipNameMatcher.matches(fileName) }
                .map { fileName ->
                    val assetPath = "$assetDir/$fileName"
                    SherpaOnnxTtsBundleCandidate(
                        displayName = fileName.removeSuffix(".zip"),
                        source = SherpaOnnxTtsBundleSource.AssetZip(assetPath),
                        sizeBytes = assetLengthOrNull(assetPath)
                    )
                }
        }
    }

    private fun android.content.res.AssetManager.listOrEmpty(path: String): List<String> {
        return runCatching { list(path)?.toList().orEmpty() }.getOrDefault(emptyList())
    }

    private fun assetLengthOrNull(assetPath: String): Long? {
        return runCatching {
            context.assets.openFd(assetPath).use { it.length }
        }.getOrNull()
    }

    private fun File.directorySize(): Long {
        return walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
}
