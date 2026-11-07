package com.ncmconverter.core

import java.io.InputStream
import java.nio.charset.StandardCharsets

class NcmHeader(
    val fileSize: Long,
    val keyLength: Int,
    val keyBytes: Int,
    val metadataLength: Int,
    val crc32: Long,
    val imageVersion: Int,
    val coverFrameLength: Long,
    val coverImageLength: Int,
    val audioOffset: Long,
    val audioLength: Long,
    val key: ByteArray,
    val metadata: NcmMetadata,
    val coverBytes: ByteArray,
    val coverMimeType: String?,
    val warnings: List<String>
)

object NcmContainerReader {
    private const val MAGIC = "CTENFDAM"
    private const val MAX_METADATA_LENGTH = 32 * 1024 * 1024
    private const val MAX_COVER_LENGTH = 20 * 1024 * 1024

    fun read(input: InputStream, fileSize: Long): NcmHeader {
        var position = 0L

        fun readBytes(count: Int): ByteArray {
            val buffer = ByteArray(count)
            var offset = 0
            while (offset < count) {
                val read = input.read(buffer, offset, count - offset)
                if (read <= 0) {
                    throw NcmException(NcmErrorCode.TRUNCATED, "文件不完整或已损坏")
                }
                offset += read
            }
            position += count
            return buffer
        }

        fun readUInt32(): Long {
            val bytes = readBytes(4)
            return (bytes[0].toLong() and 0xFF) or
                ((bytes[1].toLong() and 0xFF) shl 8) or
                ((bytes[2].toLong() and 0xFF) shl 16) or
                ((bytes[3].toLong() and 0xFF) shl 24)
        }

        fun skip(count: Long) {
            var remaining = count
            val buffer = ByteArray(8192)
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read <= 0) {
                    throw NcmException(NcmErrorCode.TRUNCATED, "文件不完整或已损坏")
                }
                remaining -= read
                position += read
            }
        }

        if (fileSize < 8) {
            throw NcmException(NcmErrorCode.TRUNCATED, "文件过小，不是完整的 NCM 文件")
        }

        val magic = String(readBytes(8), StandardCharsets.US_ASCII)
        if (magic != MAGIC) {
            throw NcmException(NcmErrorCode.NOT_NCM, "不是有效的 NCM 文件（文件头不匹配）")
        }

        readBytes(2)
        val keyLength = readUInt32()
        if (keyLength <= 0 || keyLength > 4096 || keyLength > fileSize - position) {
            throw NcmException(NcmErrorCode.TRUNCATED, "密钥段长度异常")
        }
        val keyBlob = readBytes(keyLength.toInt())

        val metadataLength = readUInt32()
        if (metadataLength <= 0 || metadataLength > MAX_METADATA_LENGTH ||
            metadataLength > fileSize - position
        ) {
            throw NcmException(NcmErrorCode.TRUNCATED, "元数据段长度异常")
        }
        val metadataBlob = readBytes(metadataLength.toInt())

        val crc32 = readUInt32()
        val imageVersion = readBytes(1)[0].toInt()
        val coverFrameLength = readUInt32()
        val coverImageLength = readUInt32()

        if (coverFrameLength < coverImageLength || coverFrameLength > MAX_COVER_LENGTH) {
            throw NcmException(NcmErrorCode.TRUNCATED, "封面长度字段异常")
        }
        if (coverImageLength > fileSize - position) {
            throw NcmException(NcmErrorCode.TRUNCATED, "封面数据超出文件范围")
        }

        val coverBytes = if (coverImageLength > 0) readBytes(coverImageLength.toInt()) else ByteArray(0)
        val padding = coverFrameLength - coverImageLength
        if (padding > 0) {
            if (position + padding > fileSize) {
                throw NcmException(NcmErrorCode.TRUNCATED, "封面填充长度异常")
            }
            skip(padding)
        }

        val audioOffset = position
        val audioLength = fileSize - audioOffset
        if (audioLength <= 0) {
            throw NcmException(NcmErrorCode.TRUNCATED, "缺少音频数据")
        }

        val warnings = mutableListOf<String>()
        val key = NcmKeyDecoder.decode(keyBlob)

        val metadata = try {
            NcmMetadataDecoder.decode(metadataBlob)
        } catch (exception: NcmException) {
            warnings += "元数据解析失败，将仅输出音频"
            NcmMetadata.EMPTY
        }

        val coverMimeType = AudioFormatSniffer.coverMimeType(coverBytes)
        if (coverBytes.isNotEmpty() && coverMimeType == null) {
            warnings += "封面格式无法识别"
        }

        return NcmHeader(
            fileSize = fileSize,
            keyLength = keyLength.toInt(),
            keyBytes = key.size,
            metadataLength = metadataLength.toInt(),
            crc32 = crc32,
            imageVersion = imageVersion,
            coverFrameLength = coverFrameLength,
            coverImageLength = coverImageLength.toInt(),
            audioOffset = audioOffset,
            audioLength = audioLength,
            key = key,
            metadata = metadata,
            coverBytes = coverBytes,
            coverMimeType = coverMimeType,
            warnings = warnings
        )
    }
}

