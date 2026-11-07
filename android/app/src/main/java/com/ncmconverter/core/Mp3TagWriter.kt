package com.ncmconverter.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object Mp3TagWriter {
    private const val MAX_TAG_SIZE = 0x0FFFFFFF

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

        val warnings = mutableListOf<String>()
        val tempFile = File(audioFile.parentFile, "${audioFile.name}.tagtmp")

        try {
            val (audioStart, audioEnd) = findAudioRange(audioFile)
            val tag = buildTag(metadata, coverBytes, coverMimeType)

            FileInputStream(audioFile).use { input ->
                input.channel.position(audioStart)
                FileOutputStream(tempFile).use { output ->
                    output.write(tag)

                    var remaining = audioEnd - audioStart
                    val buffer = ByteArray(64 * 1024)
                    while (remaining > 0) {
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read <= 0) {
                            break
                        }
                        output.write(buffer, 0, read)
                        remaining -= read
                    }
                }
            }

            Files.move(
                tempFile.toPath(),
                audioFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (exception: Exception) {
            warnings += "MP3 标签写入失败：${exception.message}"
            tempFile.delete()
        }

        return warnings
    }

    private fun findAudioRange(file: File): Pair<Long, Long> {
        var audioStart = 0L
        var audioEnd = file.length()

        RandomAccessFile(file, "r").use { raf ->
            while (true) {
                if (raf.length() - audioStart < 10) {
                    break
                }
                raf.seek(audioStart)
                val header = ByteArray(10)
                raf.readFully(header)
                if (!(header[0] == 0x49.toByte() && header[1] == 0x44.toByte() &&
                    header[2] == 0x33.toByte())
                ) {
                    break
                }

                val size = synchsafeToInt(header, 6)
                val footer = if ((header[5].toInt() and 0x10) != 0) 10L else 0L
                audioStart += 10L + size + footer
            }

            if (audioEnd >= 128) {
                raf.seek(audioEnd - 128)
                val tail = ByteArray(3)
                raf.readFully(tail)
                if (tail[0] == 0x54.toByte() && tail[1] == 0x41.toByte() &&
                    tail[2] == 0x47.toByte()
                ) {
                    audioEnd -= 128
                }
            }
        }

        return audioStart to audioEnd
    }

    private fun buildTag(
        metadata: NcmMetadata,
        coverBytes: ByteArray,
        coverMimeType: String?
    ): ByteArray {
        val frames = ByteArrayOutputStream()

        metadata.title?.takeIf { it.isNotBlank() }?.let {
            frames.write(textFrame("TIT2", it))
        }

        if (metadata.artists.isNotEmpty()) {
            frames.write(textFrame("TPE1", metadata.artists.joinToString(" / ")))
        }

        metadata.album?.takeIf { it.isNotBlank() }?.let {
            frames.write(textFrame("TALB", it))
        }

        if (coverBytes.isNotEmpty()) {
            frames.write(apicFrame(coverBytes, coverMimeType ?: "image/jpeg"))
        }

        val frameBytes = frames.toByteArray()
        if (frameBytes.size > MAX_TAG_SIZE) {
            throw IllegalArgumentException("ID3 标签过大")
        }

        val header = ByteArrayOutputStream()
        header.write("ID3".toByteArray(StandardCharsets.US_ASCII))
        header.write(3)
        header.write(0)
        header.write(0)
        writeSynchsafe(header, frameBytes.size)
        header.write(frameBytes)
        return header.toByteArray()
    }

    private fun textFrame(frameId: String, text: String): ByteArray {
        val payload = ByteArrayOutputStream()
        payload.write(1) // UTF-16 with BOM
        payload.write(0xFF)
        payload.write(0xFE)
        payload.write(text.toByteArray(StandardCharsets.UTF_16LE))
        return frame(frameId, payload.toByteArray())
    }

    private fun apicFrame(coverBytes: ByteArray, mimeType: String): ByteArray {
        val payload = ByteArrayOutputStream()
        payload.write(0) // ISO-8859-1
        payload.write(mimeType.toByteArray(StandardCharsets.US_ASCII))
        payload.write(0)
        payload.write(3) // front cover
        payload.write(0) // empty description
        payload.write(coverBytes)
        return frame("APIC", payload.toByteArray())
    }

    private fun frame(frameId: String, payload: ByteArray): ByteArray {
        val stream = ByteArrayOutputStream()
        stream.write(frameId.toByteArray(StandardCharsets.US_ASCII))
        writeBigEndian(stream, payload.size)
        stream.write(0)
        stream.write(0)
        stream.write(payload)
        return stream.toByteArray()
    }

    private fun writeSynchsafe(stream: ByteArrayOutputStream, value: Int) {
        stream.write(((value shr 21) and 0x7F))
        stream.write(((value shr 14) and 0x7F))
        stream.write(((value shr 7) and 0x7F))
        stream.write(value and 0x7F)
    }

    private fun synchsafeToInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)

    private fun writeBigEndian(stream: ByteArrayOutputStream, value: Int) {
        stream.write((value shr 24) and 0xFF)
        stream.write((value shr 16) and 0xFF)
        stream.write((value shr 8) and 0xFF)
        stream.write(value and 0xFF)
    }
}

