package com.ncmconverter.core

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object CryptoUtil {
    fun xor(data: ByteArray, mask: Int): ByteArray {
        val result = ByteArray(data.size)
        for (index in data.indices) {
            result[index] = (data[index].toInt() xor mask).toByte()
        }
        return result
    }

    fun aesEcbDecrypt(data: ByteArray, key: ByteArray): ByteArray {
        require(data.isNotEmpty() && data.size % 16 == 0) { "AES 密文长度必须是 16 的倍数" }
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(data)
    }

    fun unpadPkcs7(data: ByteArray): ByteArray {
        if (data.isEmpty()) {
            return data
        }

        val padding = data.last().toInt()
        if (padding !in 1..16 || padding > data.size) {
            return data
        }

        return data.copyOfRange(0, data.size - padding)
    }

    fun sha256(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
}

