package com.ncmconverter.service

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.ncmconverter.core.AudioFormat
import java.io.File
import java.io.IOException

class SavedOutput(
    val uri: String,
    val displayName: String,
    val size: Long
)

class OutputWriter(
    private val context: Context,
    private val outputTreeUri: String?,
    private val outputRelativePath: String
) {
    fun save(decodedFile: File, format: AudioFormat): SavedOutput {
        val mimeType = mimeType(format)
        val baseName = decodedFile.name

        if (outputTreeUri != null) {
            return saveToDocumentTree(decodedFile, mimeType, baseName)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStore(decodedFile, mimeType, baseName)
        } else {
            saveToLegacyStorage(decodedFile, mimeType, baseName)
        }
    }

    private fun saveToDocumentTree(decodedFile: File, mimeType: String, baseName: String): SavedOutput {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(outputTreeUri))
            ?: throw IOException("输出目录不可用，请重新选择")

        val displayName = uniqueDocumentName(tree, baseName)
        val document = tree.createFile(mimeType, displayName)
            ?: throw IOException("无法创建输出文件")

        context.contentResolver.openOutputStream(document.uri)?.use { output ->
            decodedFile.inputStream().use { input -> input.copyTo(output) }
        } ?: throw IOException("无法写入输出文件")

        return SavedOutput(document.uri.toString(), displayName, document.length())
    }

    private fun saveToMediaStore(decodedFile: File, mimeType: String, baseName: String): SavedOutput {
        val relative = outputRelativePath.trimEnd('/') + "/"
        val displayName = uniqueMediaStoreName(relative, baseName)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法写入媒体库")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                decodedFile.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("无法写入媒体库文件")

            val done = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, done, null, null)
            return SavedOutput(uri.toString(), displayName, decodedFile.length())
        } catch (exception: Exception) {
            resolver.delete(uri, null, null)
            throw exception
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToLegacyStorage(decodedFile: File, mimeType: String, baseName: String): SavedOutput {
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "NCM Converter"
        )
        val directory = if (publicDir.exists() || publicDir.mkdirs()) {
            publicDir
        } else {
            File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "NCM Converter")
                .also { it.mkdirs() }
        }

        val displayName = uniqueFileName(directory, baseName)
        val target = File(directory, displayName)
        decodedFile.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }

        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mimeType), null)
        return SavedOutput(target.absolutePath, displayName, target.length())
    }

    private fun uniqueDocumentName(tree: DocumentFile, baseName: String): String {
        if (tree.findFile(baseName) == null) {
            return baseName
        }

        val stem = baseName.substringBeforeLast('.')
        val extension = baseName.substringAfterLast('.', "")
        val suffix = if (extension.isEmpty()) "" else ".$extension"
        for (index in 1..999) {
            val candidate = "$stem ($index)$suffix"
            if (tree.findFile(candidate) == null) {
                return candidate
            }
        }

        throw IOException("同名文件过多")
    }

    private fun uniqueMediaStoreName(relativePath: String, baseName: String): String {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"

        fun exists(name: String): Boolean {
            resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                arrayOf(relativePath, name),
                null
            )?.use { cursor -> return cursor.count > 0 }
            return false
        }

        if (!exists(baseName)) {
            return baseName
        }

        val stem = baseName.substringBeforeLast('.')
        val extension = baseName.substringAfterLast('.', "")
        val suffix = if (extension.isEmpty()) "" else ".$extension"
        for (index in 1..999) {
            val candidate = "$stem ($index)$suffix"
            if (!exists(candidate)) {
                return candidate
            }
        }

        throw IOException("同名文件过多")
    }

    private fun uniqueFileName(directory: File, baseName: String): String {
        if (!File(directory, baseName).exists()) {
            return baseName
        }

        val stem = baseName.substringBeforeLast('.')
        val extension = baseName.substringAfterLast('.', "")
        val suffix = if (extension.isEmpty()) "" else ".$extension"
        for (index in 1..999) {
            val candidate = "$stem ($index)$suffix"
            if (!File(directory, candidate).exists()) {
                return candidate
            }
        }

        throw IOException("同名文件过多")
    }

    companion object {
        fun mimeType(format: AudioFormat): String = when (format) {
            AudioFormat.FLAC -> "audio/flac"
            AudioFormat.MP3 -> "audio/mpeg"
            AudioFormat.M4A -> "audio/mp4"
            AudioFormat.OGG -> "audio/ogg"
            AudioFormat.WAV -> "audio/wav"
            AudioFormat.UNKNOWN -> "application/octet-stream"
        }
    }
}
