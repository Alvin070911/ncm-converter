package com.ncmconverter.core

data class NcmMetadata(
    val musicId: String? = null,
    val title: String? = null,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val bitrate: Int? = null,
    val durationMs: Long? = null,
    val declaredFormat: String? = null,
    val coverUrl: String? = null
) {
    val artistText: String get() = artists.joinToString(" / ")

    companion object {
        val EMPTY = NcmMetadata()
    }
}

class NcmFileInfo(
    val filePath: String,
    val actualFormat: AudioFormat?,
    val fileSize: Long,
    val keyLength: Int,
    val keyBytes: Int,
    val metadataLength: Int,
    val crc32: Long,
    val imageVersion: Int,
    val coverFrameLength: Long,
    val coverImageLength: Int,
    val coverMimeType: String?,
    val coverBytes: ByteArray,
    val audioOffset: Long,
    val audioLength: Long,
    val metadata: NcmMetadata,
    val warnings: List<String>
)

enum class NcmErrorCode {
    NOT_NCM,
    TRUNCATED,
    KEY_DECRYPT_FAILED,
    METADATA_DECRYPT_FAILED,
    COVER_INVALID,
    UNSUPPORTED_AUDIO,
    OUTPUT_PERMISSION_DENIED,
    DISK_FULL,
    TAG_WRITE_FAILED,
    IO_ERROR,
    CANCELLED,
    CONFLICT_SKIPPED,
    UNKNOWN
}

class NcmException(
    val code: NcmErrorCode,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

enum class ConflictPolicy {
    RENAME,
    SKIP,
    OVERWRITE
}

enum class NamingMode {
    METADATA,
    SOURCE_FILE_NAME
}

data class DecodeOptions(
    val outputFileName: String? = null,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.RENAME,
    val saveCover: Boolean = false,
    val writeTags: Boolean = true,
    val namingMode: NamingMode = NamingMode.METADATA
)

class DecodeResult(
    val outputPath: String,
    val format: AudioFormat,
    val audioBytes: Long,
    val sha256: String,
    val metadata: NcmMetadata,
    val warnings: List<String>
)

class PayloadHashResult(
    val format: AudioFormat,
    val audioBytes: Long,
    val sha256: String,
    val metadata: NcmMetadata,
    val warnings: List<String>
)
