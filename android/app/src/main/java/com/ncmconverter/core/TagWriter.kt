package com.ncmconverter.core

import java.io.File

object TagWriter {
    fun write(
        audioFile: File,
        format: AudioFormat,
        metadata: NcmMetadata,
        coverBytes: ByteArray,
        coverMimeType: String?
    ): List<String> {
        return try {
            when (format) {
                AudioFormat.FLAC -> FlacTagWriter.write(audioFile, metadata, coverBytes, coverMimeType)
                AudioFormat.MP3 -> Mp3TagWriter.write(audioFile, metadata, coverBytes, coverMimeType)
                else -> emptyList()
            }
        } catch (exception: Exception) {
            listOf("标签写入失败：${exception.message}")
        }
    }
}
