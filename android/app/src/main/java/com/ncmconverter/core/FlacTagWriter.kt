package com.ncmconverter.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object FlacTagWriter {
    private const val VORBIS_COMMENT = 4
    private const val PICTURE = 6
    private const val MAX_BLOCK_LENGTH = 0xFFFFFF

    fun write(
        audioFile: File,
        metadata: NcmMetadata,
        coverBytes: ByteArray,
        coverMimeType: String?
    ): List<String> {
        if (metadata.title.isNullOrBlank() && metadata.artists.isEmpty() &&
            metadata.album.isNullOrBlank() && coverBytes.isEmpty()
        ) {
            return emptyList()
        }

        val tempFile = File(audioFile.parentFile, "${audioFile.name}.tagtmp")
        val warnings = mutableListOf<String>()

        try {
            val blocks = mutableListOf<Pair<Int, ByteArray>>()
            var audioOffset: Long

            FileInputStream(audioFile).use { input ->
                val magic = readExactly(input, 4)
                if (!(magic[0] == 0x66.toByte() && magic[1] == 0x4C.toByte() &&
                    magic[2] == 0x61.toByte() && magic[3] == 0x43.toByte())
                ) {
                    throw IllegalArgumentException("不是有效的 FLAC 文件")
                }

                var last = false
                while (!last) {
                    val header = readExactly(input, 4)
                    last = (header[0].toInt() and 0x80) != 0
                    val type = header[0].toInt() and 0x7F
                    val length = ((header[1].toInt() and 0xFF) shl 16) or
                        ((header[2].toInt() and 0xFF) shl 8) or
                        (header[3].toInt() and 0xFF)
                    if (length > MAX_BLOCK_LENGTH) {
                        throw IllegalArgumentException("FLAC 元数据块长度异常")
                    }

                    val data = readExactly(input, length)
                    if (type != VORBIS_COMMENT && type != PICTURE) {
                        blocks += type to data
                    }
                }

                audioOffset = input.channel.position()

                blocks += VORBIS_COMMENT to buildVorbisComment(metadata)
                if (coverBytes.isNotEmpty()) {
                    blocks += PICTURE to buildPicture(coverBytes, coverMimeType)
                }

                if (blocks.isEmpty() || blocks[0].first != 0) {
                    throw IllegalArgumentException("FLAC 缺少 STREAMINFO")
                }

                FileOutputStream(tempFile).use { output ->
                    output.write("fLaC".toByteArray(StandardCharsets.US_ASCII))

                    blocks.forEachIndexed { index, (type, data) ->
                        if (data.size > MAX_BLOCK_LENGTH) {
                            throw IllegalArgumentException("FLAC 元数据块过大")
                        }

                        val header = byteArrayOf(
                            (type or if (index == blocks.lastIndex) 0x80 else 0).toByte(),
                            (data.size shr 16).toByte(),
                            (data.size shr 8).toByte(),
                            data.size.toByte()
                        )
                        output.write(header)
                        output.write(data)
                    }

                    input.channel.position(audioOffset)
                    input.copyTo(output, 64 * 1024)
                }
            }

            Files.move(
                tempFile.toPath(),
                audioFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (exception: Exception) {
            warnings += "FLAC 标签写入失败：${exception.message}"
            tempFile.delete()
            // 标签失败不阻断音频输出，由调用方以警告展示
        }

        return warnings
    }

    private fun buildVorbisComment(metadata: NcmMetadata): ByteArray {
        val comments = mutableListOf<String>()
        metadata.title?.takeIf { it.isNotBlank() }?.let { comments += "TITLE=$it" }
        metadata.artists.filter { it.isNotBlank() }.forEach { comments += "ARTIST=$it" }
        metadata.album?.takeIf { it.isNotBlank() }?.let { comments += "ALBUM=$it" }

        val stream = ByteArrayOutputStream()
        val vendor = "NcmConverter".toByteArray(StandardCharsets.UTF_8)
        writeLittleEndian(stream, vendor.size)
        stream.write(vendor)
        writeLittleEndian(stream, comments.size)
        comments.forEach { comment ->
            val bytes = comment.toByteArray(StandardCharsets.UTF_8)
            writeLittleEndian(stream, bytes.size)
            stream.write(bytes)
        }
        return stream.toByteArray()
    }

    private fun buildPicture(coverBytes: ByteArray, coverMimeType: String?): ByteArray {
        val stream = ByteArrayOutputStream()
        writeBigEndian(stream, 3)
        val mime = (coverMimeType ?: "image/jpeg").toByteArray(StandardCharsets.US_ASCII)
        writeBigEndian(stream, mime.size)
        stream.write(mime)
        writeBigEndian(stream, 0)
        writeBigEndian(stream, 0)
        writeBigEndian(stream, 0)
        writeBigEndian(stream, 0)
        writeBigEndian(stream, 0)
        writeBigEndian(stream, coverBytes.size)
        stream.write(coverBytes)
        return stream.toByteArray()
    }

    private fun writeLittleEndian(stream: ByteArrayOutputStream, value: Int) {
        stream.write(value and 0xFF)
        stream.write((value shr 8) and 0xFF)
        stream.write((value shr 16) and 0xFF)
        stream.write((value shr 24) and 0xFF)
    }

    private fun writeBigEndian(stream: ByteArrayOutputStream, value: Int) {
        stream.write((value shr 24) and 0xFF)
        stream.write((value shr 16) and 0xFF)
        stream.write((value shr 8) and 0xFF)
        stream.write(value and 0xFF)
    }

    private fun readExactly(input: FileInputStream, count: Int): ByteArray {
        val buffer = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(buffer, offset, count - offset)
            if (read <= 0) {
                throw IllegalArgumentException("FLAC 文件不完整")
            }
            offset += read
        }
        return buffer
    }
}

