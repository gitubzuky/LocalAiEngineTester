package com.zure.localaiengine.sherpa.onnx.tts.audio

import android.annotation.TargetApi
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

object SherpaOnnxWavSaver {
    fun save(audio: SherpaOnnxGeneratedAudio, outputFile: File): File {
        require(audio.sampleRate > 0) { "WAV sampleRate must be positive." }
        require(audio.channels > 0) { "WAV channels must be positive." }
        outputFile.parentFile?.mkdirs()

        outputFile.outputStream().use { output ->
            writeWav(audio, output)
        }
        return outputFile
    }

    @TargetApi(Build.VERSION_CODES.Q)
    fun saveToExternalDownloads(
        context: Context,
        audio: SherpaOnnxGeneratedAudio,
        relativeDir: String,
        fileName: String
    ): Uri {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Saving to MediaStore Downloads requires Android 10 (API 29) or higher."
        }
        require(relativeDir.isNotBlank()) { "External relativeDir must not be blank." }
        require(fileName.isNotBlank()) { "External fileName must not be blank." }
        require(audio.sampleRate > 0) { "WAV sampleRate must be positive." }
        require(audio.channels > 0) { "WAV channels must be positive." }

        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val normalizedDir = relativeDir.trim('/')
        deleteExistingDownload(
            resolver = resolver,
            collection = collection,
            relativeDir = normalizedDir,
            fileName = fileName
        )

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, WAV_MIME_TYPE)
            put(MediaStore.Downloads.RELATIVE_PATH, normalizedDir)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = requireNotNull(resolver.insert(collection, values)) {
            "Cannot create MediaStore Downloads item."
        }
        runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                writeWav(audio, output)
            } ?: error("Cannot open output stream for $uri")

            val completeValues = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(uri, completeValues, null, null)
        }.onFailure {
            resolver.delete(uri, null, null)
            throw it
        }
        return uri
    }

    private fun writeWav(audio: SherpaOnnxGeneratedAudio, output: OutputStream) {
        val pcmBytes = audio.samples.size * PCM_16_BYTES
        output.write(wavHeader(pcmBytes, audio.sampleRate, audio.channels))
        val buffer = ByteBuffer.allocate(PCM_16_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        audio.samples.forEach { sample ->
            val pcm = (sample.coerceIn(-1.0f, 1.0f) * Short.MAX_VALUE).roundToInt().toShort()
            buffer.clear()
            buffer.putShort(pcm)
            output.write(buffer.array())
        }
    }

    private fun deleteExistingDownload(
        resolver: ContentResolver,
        collection: Uri,
        relativeDir: String,
        fileName: String
    ) {
        // MediaStore stores RELATIVE_PATH with a trailing slash, so query both forms defensively.
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND " +
            "(${MediaStore.Downloads.RELATIVE_PATH} = ? OR ${MediaStore.Downloads.RELATIVE_PATH} = ?)"
        val selectionArgs = arrayOf(fileName, relativeDir, "$relativeDir/")
        resolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            while (cursor.moveToNext()) {
                val itemUri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                resolver.delete(itemUri, null, null)
            }
        }
    }

    private fun wavHeader(pcmBytes: Int, sampleRate: Int, channels: Int): ByteArray {
        val byteRate = sampleRate * channels * PCM_16_BYTES
        val blockAlign = channels * PCM_16_BYTES
        return ByteBuffer.allocate(WAV_HEADER_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putAscii("RIFF")
            .putInt(WAV_HEADER_BYTES - 8 + pcmBytes)
            .putAscii("WAVE")
            .putAscii("fmt ")
            .putInt(16)
            .putShort(1)
            .putShort(channels.toShort())
            .putInt(sampleRate)
            .putInt(byteRate)
            .putShort(blockAlign.toShort())
            .putShort(16)
            .putAscii("data")
            .putInt(pcmBytes)
            .array()
    }

    private fun ByteBuffer.putAscii(value: String): ByteBuffer {
        put(value.toByteArray(Charsets.US_ASCII))
        return this
    }

    private const val PCM_16_BYTES = 2
    private const val WAV_HEADER_BYTES = 44
    private const val WAV_MIME_TYPE = "audio/wav"
}
