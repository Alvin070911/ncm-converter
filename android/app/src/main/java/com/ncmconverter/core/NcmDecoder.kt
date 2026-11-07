package com.ncmconverter.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

class NcmDecoder {
    fun inspect(file: File): NcmFileInfo {
        FileInputStream(file).use { input ->
            return inspect(input, file.length(), file.absolutePath)
        }
    }

    fun inspect(input: InputStream, fileSize: Long, filePath: String): NcmFileInfo {
        try {
            val header = NcmContainerReader.read(input, fileSize)
            val sniffLength = minOf(32L, header.audioLength).toInt()
            val cipher = ByteArray(sniffLength)
            readFully(input, cipher)
            val actualFormat = AudioFormatSniffer.detect(NcmKeyBox.create(header.key).decrypt(cipher, 0))

            return NcmFileInfo(
                filePath = filePath,
                actualFormat = actualFormat,
                fileSize = header.fileSize,
                keyLength = header.keyLength,
                keyBytes = header.keyBytes,
                metadataLength = header.metadataLength,
                crc32 = header.crc32,
                imageVersion = header.imageVersion,
                coverFrameLength = header.coverFrameLength,
                coverImageLength = header.coverImageLength,
                coverMimeType = header.coverMimeType,
                coverBytes = header.coverBytes,
                audioOffset = header.audioOffset,
                audioLength = header.audioLength,
                metadata = header.metadata,
                warnings = header.warnings
            )
        } catch (exception: NcmException) {
            throw exception
        } catch (exception: IOException) {
            throw NcmException(NcmErrorCode.IO_ERROR, "读取文件失败", exception)
        } catch (exception: Exception) {
            throw NcmException(NcmErrorCode.UNKNOWN, "解析文件失败", exception)
        }
    }

    fun decode(
        file: File,
        outputDirectory: File,
        options: DecodeOptions = DecodeOptions(),
        progress: (Long, Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): DecodeResult {
        try {
            FileInputStream(file).use { input ->
                val header = NcmContainerReader.read(input, file.length())
                if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
                    throw NcmException(NcmErrorCode.OUTPUT_PERMISSION_DENIED, "无法创建输出目录")
                }

                val tempFile = File(outputDirectory, ".ncm-${UUID.randomUUID()}.tmp")
                try {
                    progress(0, header.audioLength)
                    var format = AudioFormat.UNKNOWN
                    var processed = 0L
                    val keyBox = NcmKeyBox.create(header.key)
                    val cipherBuffer = ByteArray(BUFFER_SIZE)
                    val plainBuffer = ByteArray(BUFFER_SIZE)
                    val digest = MessageDigest.getInstance("SHA-256")

                    FileOutputStream(tempFile).use { output ->
                        while (true) {
                            if (isCancelled()) {
                                throw NcmException(NcmErrorCode.CANCELLED, "转换已取消")
                            }

                            val read = input.read(cipherBuffer)
                            if (read <= 0) {
                                break
                            }

                            keyBox.decrypt(cipherBuffer, plainBuffer, read, processed)

                            if (format == AudioFormat.UNKNOWN) {
                                format = AudioFormatSniffer.detect(
                                    plainBuffer.copyOf(minOf(read, 32))
                                )
                                if (format == AudioFormat.UNKNOWN) {
                                    throw NcmException(
                                        NcmErrorCode.UNSUPPORTED_AUDIO,
                                        "内部音频格式暂不支持"
                                    )
                                }
                            }

                            output.write(plainBuffer, 0, read)
                            digest.update(plainBuffer, 0, read)
                            processed += read
                            progress(processed, header.audioLength)
                        }
                    }

                    if (format == AudioFormat.UNKNOWN) {
                        throw NcmException(NcmErrorCode.UNSUPPORTED_AUDIO, "内部音频格式暂不支持")
                    }

                    val sha256 = digest.digest().toHex()
                    val extension = format.extension
                    val fileName = buildFileName(options, header, file, extension)
                    val finalFile = NamingPolicy.resolveOutputPath(
                        outputDirectory, fileName, options.conflictPolicy
                    )

                    if (finalFile.exists() && options.conflictPolicy == ConflictPolicy.OVERWRITE) {
                        finalFile.delete()
                    }

                    if (!tempFile.renameTo(finalFile)) {
                        tempFile.copyTo(finalFile, overwrite = true)
                        tempFile.delete()
                    }

                    val warnings = header.warnings.toMutableList()
                    if (options.writeTags && format.supportsTags) {
                        warnings += TagWriter.write(
                            finalFile, format, header.metadata, header.coverBytes, header.coverMimeType
                        )
                    }

                    if (options.saveCover && header.coverBytes.isNotEmpty()) {
                        saveCover(outputDirectory, header, warnings)
                    }

                    return DecodeResult(
                        outputPath = finalFile.absolutePath,
                        format = format,
                        audioBytes = processed,
                        sha256 = sha256,
                        metadata = header.metadata,
                        warnings = warnings
                    )
                } finally {
                    tempFile.delete()
                }
            }
        } catch (exception: NcmException) {
            throw exception
        } catch (exception: SecurityException) {
            throw NcmException(NcmErrorCode.OUTPUT_PERMISSION_DENIED, "无法写入输出目录", exception)
        } catch (exception: IOException) {
            val code = if (exception.message?.contains("ENOSPC") == true) {
                NcmErrorCode.DISK_FULL
            } else {
                NcmErrorCode.IO_ERROR
            }
            throw NcmException(code, "读取或写入失败", exception)
        } catch (exception: Exception) {
            throw NcmException(NcmErrorCode.UNKNOWN, "转换失败", exception)
        }
    }

    fun computePayloadHash(
        file: File,
        progress: (Long, Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): PayloadHashResult {
        try {
            FileInputStream(file).use { input ->
                val header = NcmContainerReader.read(input, file.length())
                progress(0, header.audioLength)

                var format = AudioFormat.UNKNOWN
                var processed = 0L
                val keyBox = NcmKeyBox.create(header.key)
                val cipherBuffer = ByteArray(BUFFER_SIZE)
                val plainBuffer = ByteArray(BUFFER_SIZE)
                val digest = MessageDigest.getInstance("SHA-256")

                while (true) {
                    if (isCancelled()) {
                        throw NcmException(NcmErrorCode.CANCELLED, "转换已取消")
                    }

                    val read = input.read(cipherBuffer)
                    if (read <= 0) {
                        break
                    }

                    keyBox.decrypt(cipherBuffer, plainBuffer, read, processed)
                    if (format == AudioFormat.UNKNOWN) {
                        format = AudioFormatSniffer.detect(plainBuffer.copyOf(minOf(read, 32)))
                        if (format == AudioFormat.UNKNOWN) {
                            throw NcmException(NcmErrorCode.UNSUPPORTED_AUDIO, "内部音频格式暂不支持")
                        }
                    }

                    digest.update(plainBuffer, 0, read)
                    processed += read
                    progress(processed, header.audioLength)
                }

                return PayloadHashResult(
                    format = format,
                    audioBytes = processed,
                    sha256 = digest.digest().toHex(),
                    metadata = header.metadata,
                    warnings = header.warnings
                )
            }
        } catch (exception: NcmException) {
            throw exception
        } catch (exception: IOException) {
            throw NcmException(NcmErrorCode.IO_ERROR, "读取文件失败", exception)
        } catch (exception: Exception) {
            throw NcmException(NcmErrorCode.UNKNOWN, "计算音频哈希失败", exception)
        }
    }

    private fun buildFileName(
        options: DecodeOptions,
        header: NcmHeader,
        sourceFile: File,
        extension: String
    ): String {
        val requested = options.outputFileName?.trim()
        if (requested.isNullOrBlank()) {
            return NamingPolicy.buildFileName(
                header.metadata, sourceFile, extension, options.namingMode
            )
        }

        val requestedFile = File(requested).name
        val stem = NamingPolicy.sanitize(requestedFile.substringBeforeLast('.'))
        val requestedExtension = requestedFile.substringAfterLast('.', "")
        val finalStem = stem.ifBlank { "未命名" }
        return finalStem + if (requestedExtension.isBlank()) extension else ".$requestedExtension"
    }

    private fun saveCover(directory: File, header: NcmHeader, warnings: MutableList<String>) {
        try {
            val extension = if (header.coverMimeType == "image/png") ".png" else ".jpg"
            val coverFile = NamingPolicy.resolveOutputPath(
                directory, "cover$extension", ConflictPolicy.RENAME
            )
            coverFile.writeBytes(header.coverBytes)
        } catch (exception: Exception) {
            warnings += "封面单独导出失败"
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read <= 0) {
                break
            }
            offset += read
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
    }
}
