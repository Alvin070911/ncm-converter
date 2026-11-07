package com.ncmconverter.core

import java.io.File

object NamingPolicy {
    private const val MAX_NAME_LENGTH = 180
    private val INVALID_CHARS = Regex("[<>:\"/\\\\|?*\\u0000-\\u001F]")

    fun buildFileName(
        metadata: NcmMetadata,
        sourceFile: File,
        extension: String,
        namingMode: NamingMode = NamingMode.METADATA
    ): String {
        val title = metadata.title?.trim()
        val artist = metadata.artistText.trim()

        val stem = when {
            namingMode == NamingMode.SOURCE_FILE_NAME -> sourceFile.nameWithoutExtension
            !title.isNullOrBlank() && artist.isNotBlank() -> "$title - $artist"
            !title.isNullOrBlank() -> title
            else -> sourceFile.nameWithoutExtension
        }

        var sanitized = sanitize(stem)
        if (sanitized.length > MAX_NAME_LENGTH) {
            sanitized = sanitized.take(MAX_NAME_LENGTH).trimEnd()
        }
        if (sanitized.isBlank()) {
            sanitized = "未命名"
        }

        return sanitized + extension
    }

    fun resolveOutputPath(directory: File, fileName: String, policy: ConflictPolicy): File {
        val target = File(directory, fileName)
        if (!target.exists()) {
            return target
        }

        return when (policy) {
            ConflictPolicy.SKIP -> throw NcmException(
                NcmErrorCode.CONFLICT_SKIPPED,
                "目标文件已存在，已跳过"
            )

            ConflictPolicy.OVERWRITE -> target

            ConflictPolicy.RENAME -> {
                val stem = fileName.substringBeforeLast('.')
                val extension = fileName.substringAfterLast('.', "").let {
                    if (it.isEmpty()) "" else ".$it"
                }

                var candidate: File? = null
                for (index in 1..999) {
                    val file = File(directory, "$stem ($index)$extension")
                    if (!file.exists()) {
                        candidate = file
                        break
                    }
                }

                candidate ?: throw NcmException(
                    NcmErrorCode.CONFLICT_SKIPPED,
                    "同名文件过多，无法生成唯一文件名"
                )
            }
        }
    }

    fun sanitize(name: String): String =
        INVALID_CHARS.replace(name, "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.', ' ')
}
