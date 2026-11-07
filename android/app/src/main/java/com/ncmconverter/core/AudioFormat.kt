package com.ncmconverter.core

enum class AudioFormat(
    val extension: String,
    val displayName: String,
    val supportsTags: Boolean
) {
    FLAC(".flac", "FLAC", true),
    MP3(".mp3", "MP3", true),
    M4A(".m4a", "M4A", false),
    OGG(".ogg", "OGG", false),
    WAV(".wav", "WAV", false),
    UNKNOWN("", "未知", false);

    companion object {
        fun fromExtension(extension: String): AudioFormat = when (extension.lowercase()) {
            ".flac" -> FLAC
            ".mp3" -> MP3
            ".m4a" -> M4A
            ".ogg" -> OGG
            ".wav" -> WAV
            else -> UNKNOWN
        }
    }
}

object AudioFormatSniffer {
    fun detect(head: ByteArray): AudioFormat {
        if (head.size >= 4 && head[0] == 0x66.toByte() && head[1] == 0x4C.toByte() &&
            head[2] == 0x61.toByte() && head[3] == 0x43.toByte()
        ) {
            return AudioFormat.FLAC
        }

        if (head.size >= 3 && head[0] == 0x49.toByte() && head[1] == 0x44.toByte() &&
            head[2] == 0x33.toByte()
        ) {
            return AudioFormat.MP3
        }

        if (head.size >= 2 && head[0] == 0xFF.toByte() && (head[1].toInt() and 0xE0) == 0xE0) {
            return AudioFormat.MP3
        }

        if (head.size >= 8 && head[4] == 0x66.toByte() && head[5] == 0x74.toByte() &&
            head[6] == 0x79.toByte() && head[7] == 0x70.toByte()
        ) {
            return AudioFormat.M4A
        }

        if (head.size >= 4 && head[0] == 0x4F.toByte() && head[1] == 0x67.toByte() &&
            head[2] == 0x67.toByte() && head[3] == 0x53.toByte()
        ) {
            return AudioFormat.OGG
        }

        if (head.size >= 12 && head[0] == 0x52.toByte() && head[1] == 0x49.toByte() &&
            head[2] == 0x46.toByte() && head[3] == 0x46.toByte() &&
            head[8] == 0x57.toByte() && head[9] == 0x41.toByte() &&
            head[10] == 0x56.toByte() && head[11] == 0x45.toByte()
        ) {
            return AudioFormat.WAV
        }

        return AudioFormat.UNKNOWN
    }

    fun coverMimeType(cover: ByteArray): String? {
        if (cover.size >= 3 && cover[0] == 0xFF.toByte() && cover[1] == 0xD8.toByte() &&
            cover[2] == 0xFF.toByte()
        ) {
            return "image/jpeg"
        }

        if (cover.size >= 8 && cover[0] == 0x89.toByte() && cover[1] == 0x50.toByte() &&
            cover[2] == 0x4E.toByte() && cover[3] == 0x47.toByte() &&
            cover[4] == 0x0D.toByte() && cover[5] == 0x0A.toByte() &&
            cover[6] == 0x1A.toByte() && cover[7] == 0x0A.toByte()
        ) {
            return "image/png"
        }

        return null
    }
}
