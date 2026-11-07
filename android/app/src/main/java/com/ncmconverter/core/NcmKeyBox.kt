package com.ncmconverter.core

class NcmKeyBox private constructor(private val box: ByteArray) {

    fun decrypt(input: ByteArray, output: ByteArray, length: Int, offset: Long) {
        for (index in 0 until length) {
            val position = offset + index
            val j = ((position + 1) and 0xFF).toInt()
            val t = (box[j].toInt() + j) and 0xFF
            output[index] = (input[index].toInt() xor
                box[(box[j].toInt() + box[t].toInt()) and 0xFF].toInt()).toByte()
        }
    }

    fun decrypt(input: ByteArray, offset: Long): ByteArray {
        val output = ByteArray(input.size)
        decrypt(input, output, input.size, offset)
        return output
    }

    companion object {
        fun create(key: ByteArray): NcmKeyBox {
            require(key.isNotEmpty()) { "key 不能为空" }
            val box = IntArray(256) { it }
            var last = 0
            var keyOffset = 0

            for (index in 0 until 256) {
                val swap = box[index]
                last = (swap + last + key[keyOffset].toInt()) and 0xFF
                keyOffset = (keyOffset + 1) % key.size
                box[index] = box[last]
                box[last] = swap
            }

            return NcmKeyBox(box.map { it.toByte() }.toByteArray())
        }
    }
}
