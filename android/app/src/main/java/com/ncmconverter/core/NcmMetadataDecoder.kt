package com.ncmconverter.core

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.nio.charset.StandardCharsets
import java.util.Base64

object NcmMetadataDecoder {
    private const val METADATA_PREFIX = "163 key(Don't modify):"

    private val MODIFY_KEY = byteArrayOf(
        0x23, 0x31, 0x34, 0x6C, 0x6A, 0x6B, 0x5F, 0x21,
        0x5C.toByte(), 0x5D, 0x26, 0x30, 0x55, 0x3C, 0x27, 0x28
    )

    fun decode(metaBlob: ByteArray): NcmMetadata {
        try {
            val raw = CryptoUtil.xor(metaBlob, 0x63)
            if (raw.size < 22) {
                throw NcmException(NcmErrorCode.METADATA_DECRYPT_FAILED, "元数据长度异常")
            }

            val prefix = String(raw, 0, 22, StandardCharsets.US_ASCII)
            if (prefix != METADATA_PREFIX) {
                throw NcmException(NcmErrorCode.METADATA_DECRYPT_FAILED, "元数据前缀不匹配")
            }

            val base64 = String(raw, 22, raw.size - 22, StandardCharsets.US_ASCII).trim()
            val cipher = Base64.getDecoder().decode(base64)
            val plain = CryptoUtil.unpadPkcs7(CryptoUtil.aesEcbDecrypt(cipher, MODIFY_KEY))
            val text = String(plain, StandardCharsets.UTF_8)

            if (!text.startsWith("music:")) {
                throw NcmException(NcmErrorCode.METADATA_DECRYPT_FAILED, "元数据明文前缀不匹配")
            }

            return parse(text.substring(6))
        } catch (exception: NcmException) {
            throw exception
        } catch (exception: Exception) {
            throw NcmException(NcmErrorCode.METADATA_DECRYPT_FAILED, "无法解析 NCM 元数据", exception)
        }
    }

    private fun parse(json: String): NcmMetadata {
        val root = JsonParser.parseString(json).asJsonObject

        val artists = mutableListOf<String>()
        val artistElement = root.get("artist")
        if (artistElement != null && artistElement.isJsonArray) {
            artistElement.asJsonArray.forEach { item ->
                when {
                item.isJsonArray -> {
                    val values = item.asJsonArray.mapNotNull { value ->
                        if (value.isJsonPrimitive) value.asString else null
                    }.filter { it.isNotBlank() }

                    if (values.size >= 2) {
                        val first = values[0]
                        val second = values[1]
                        artists += if (first.all(Char::isDigit) && !second.all(Char::isDigit)) {
                            second
                        } else {
                            first
                        }
                    } else if (values.size == 1) {
                        artists += values[0]
                    }
                }

                item.isJsonPrimitive -> {
                    val value = item.asString
                    if (value.isNotBlank()) {
                        artists += value
                    }
                }
            }
        }
        }

        return NcmMetadata(
            musicId = root.stringOrNull("musicId"),
            title = root.stringOrNull("musicName"),
            artists = artists,
            album = root.stringOrNull("album"),
            bitrate = root.intOrNull("bitrate"),
            durationMs = root.longOrNull("duration"),
            declaredFormat = root.stringOrNull("format"),
            coverUrl = root.stringOrNull("albumPic") ?: root.stringOrNull("cover")
        )
    }

    private fun JsonObject.stringOrNull(name: String): String? {
        val value = get(name) ?: return null
        if (!value.isJsonPrimitive) {
            return null
        }

        val primitive = value.asJsonPrimitive
        return if (primitive.isString) primitive.asString else primitive.toString()
    }

    private fun JsonObject.intOrNull(name: String): Int? {
        val value = get(name) ?: return null
        if (!value.isJsonPrimitive) {
            return null
        }

        val primitive = value.asJsonPrimitive
        return if (primitive.isNumber) primitive.asInt else primitive.asString.toIntOrNull()
    }

    private fun JsonObject.longOrNull(name: String): Long? {
        val value = get(name) ?: return null
        if (!value.isJsonPrimitive) {
            return null
        }

        val primitive = value.asJsonPrimitive
        return if (primitive.isNumber) primitive.asLong else primitive.asString.toLongOrNull()
    }
}

