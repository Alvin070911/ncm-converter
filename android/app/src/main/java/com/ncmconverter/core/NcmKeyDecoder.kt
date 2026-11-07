package com.ncmconverter.core

import java.nio.charset.StandardCharsets

object NcmKeyDecoder {
    private const val KEY_PREFIX = "neteasecloudmusic"
    private val CORE_KEY = "hzHRAmso5kInbaxW".toByteArray(StandardCharsets.US_ASCII)

    fun decode(keyBlob: ByteArray): ByteArray {
        try {
            val raw = CryptoUtil.xor(keyBlob, 0x64)
            val plain = CryptoUtil.unpadPkcs7(CryptoUtil.aesEcbDecrypt(raw, CORE_KEY))

            if (plain.size < 17) {
                throw NcmException(NcmErrorCode.KEY_DECRYPT_FAILED, "密钥长度异常")
            }

            val prefix = String(plain, 0, 17, StandardCharsets.US_ASCII)
            if (prefix != KEY_PREFIX) {
                throw NcmException(NcmErrorCode.KEY_DECRYPT_FAILED, "密钥前缀不匹配，可能是不兼容的客户端版本")
            }

            val key = plain.copyOfRange(17, plain.size)
            if (key.isEmpty()) {
                throw NcmException(NcmErrorCode.KEY_DECRYPT_FAILED, "解密后的密钥为空")
            }

            return key
        } catch (exception: NcmException) {
            throw exception
        } catch (exception: Exception) {
            throw NcmException(NcmErrorCode.KEY_DECRYPT_FAILED, "无法解析 NCM 密钥", exception)
        }
    }
}

