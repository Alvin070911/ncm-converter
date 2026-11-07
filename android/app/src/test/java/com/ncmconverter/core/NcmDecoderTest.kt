package com.ncmconverter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files

class NcmDecoderTest {
    private val decoder = NcmDecoder()

    @Test
    fun decodesAllSamplesWithExpectedPayload() {
        val testData = findTestData() ?: return
        val samples = listOf(
            Sample(
                "Lia - 鳥の詩.ncm", AudioFormat.FLAC, 275316396L,
                "d76a57ea2e50830240f08ed306ce2b0565b60cc5b32ef5e4592d1d4c8c94153b",
                "鳥の詩", "Lia"
            ),
            Sample(
                "Top Barry,INDEcompany - 隐藏相册.ncm", AudioFormat.MP3, 2507666L,
                "37d883ad558cfbdda09fde14b01e74862b442df2c002f45f3672cd4256d42296",
                "隐藏相册", "Top Barry / INDEcompany"
            ),
            Sample(
                "いきものがかり - SAKURA.ncm", AudioFormat.FLAC, 255726511L,
                "fc2356f29f020cbc7b2c193fa0bcb8806d1444820bc66877139f792aa99e41f7",
                "SAKURA", "いきものがかり"
            ),
            Sample(
                "胡夏 - 那些年.ncm", AudioFormat.FLAC, 245280801L,
                "40a37c882e2fb3f3b431ff2d7b5e13c14999badc23d53b962786423fd175a545",
                "那些年", "胡夏"
            )
        )

        samples.forEach { sample ->
            val file = File(testData, sample.fileName)
            if (!file.exists()) {
                return@forEach
            }

            val result = decoder.computePayloadHash(file)
            assertEquals(sample.format, result.format)
            assertEquals(sample.audioBytes, result.audioBytes)
            assertEquals(sample.sha256, result.sha256)
            assertEquals(sample.title, result.metadata.title)
            assertEquals(sample.artist, result.metadata.artistText)
        }
    }

    @Test
    fun inspectReadsMetadataAndOffsets() {
        val testData = findTestData() ?: return
        val file = File(testData, "Lia - 鳥の詩.ncm")
        if (!file.exists()) {
            return
        }

        val info = decoder.inspect(file)
        assertEquals(AudioFormat.FLAC, info.actualFormat)
        assertEquals(128, info.keyLength)
        assertEquals(726, info.metadataLength)
        assertEquals(180694L, info.audioOffset)
        assertEquals(179809, info.coverImageLength)
        assertEquals("image/jpeg", info.coverMimeType)
        assertEquals("鳥の詩", info.metadata.title)
        assertEquals("Lia", info.metadata.artistText)
        assertEquals(4999000, info.metadata.bitrate)
    }

    @Test
    fun decodeWritesMp3Tags() {
        val testData = findTestData() ?: return
        val file = File(testData, "Top Barry,INDEcompany - 隐藏相册.ncm")
        if (!file.exists()) {
            return
        }

        val outputDirectory = Files.createTempDirectory("ncm-mp3-test").toFile()
        try {
            val result = decoder.decode(file, outputDirectory, DecodeOptions(saveCover = true))
            assertTrue(result.outputPath.endsWith(".mp3"))
            assertEquals(AudioFormat.MP3, result.format)

            val bytes = File(result.outputPath).readBytes()
            assertTrue(bytes.size > 10)
            assertEquals("ID3", String(bytes, 0, 3, Charsets.US_ASCII))
            assertTrue(findAscii(bytes, "TIT2"))
            assertTrue(findAscii(bytes, "APIC"))
            assertTrue(File(outputDirectory, "cover.jpg").exists())
        } finally {
            outputDirectory.deleteRecursively()
        }
    }

    @Test
    fun decodeWritesFlacCommentAndPicture() {
        val testData = findTestData() ?: return
        val file = File(testData, "胡夏 - 那些年.ncm")
        if (!file.exists()) {
            return
        }

        val outputDirectory = Files.createTempDirectory("ncm-flac-test").toFile()
        try {
            val result = decoder.decode(file, outputDirectory)
            assertTrue(result.outputPath.endsWith(".flac"))
            assertEquals(AudioFormat.FLAC, result.format)

            val blockTypes = readFlacBlockTypes(File(result.outputPath))
            assertTrue("warnings=${result.warnings}, blocks=$blockTypes", blockTypes.contains(4))
            assertTrue("warnings=${result.warnings}, blocks=$blockTypes", blockTypes.contains(6))
        } finally {
            outputDirectory.deleteRecursively()
        }
    }

    @Test
    fun rejectsNonNcmFile() {
        val file = File.createTempFile("not-ncm", ".ncm")
        try {
            file.writeText("not an ncm file")
            val exception = try {
                decoder.inspect(file)
                null
            } catch (exception: NcmException) {
                exception
            }

            assertNotNull(exception)
            assertEquals(NcmErrorCode.NOT_NCM, exception!!.code)
        } finally {
            file.delete()
        }
    }

    private fun readFlacBlockTypes(file: File): List<Int> {
        val types = mutableListOf<Int>()
        FileInputStream(file).use { input ->
            val magic = ByteArray(4)
            input.read(magic)
            var last = false
            while (!last) {
                val header = ByteArray(4)
                var read = 0
                while (read < 4) {
                    val count = input.read(header, read, 4 - read)
                    if (count <= 0) {
                        return types
                    }
                    read += count
                }

                last = (header[0].toInt() and 0x80) != 0
                val type = header[0].toInt() and 0x7F
                val length = ((header[1].toInt() and 0xFF) shl 16) or
                    ((header[2].toInt() and 0xFF) shl 8) or
                    (header[3].toInt() and 0xFF)
                types += type

                var remaining = length
                val buffer = ByteArray(8192)
                while (remaining > 0) {
                    val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                    if (count <= 0) {
                        return types
                    }
                    remaining -= count
                }
            }
        }

        return types
    }

    private fun findAscii(bytes: ByteArray, text: String): Boolean {
        val target = text.toByteArray(Charsets.US_ASCII)
        for (index in 0..(bytes.size - target.size)) {
            var matched = true
            for (offset in target.indices) {
                if (bytes[index + offset] != target[offset]) {
                    matched = false
                    break
                }
            }
            if (matched) {
                return true
            }
        }
        return false
    }

    private fun findTestData(): File? {
        var directory: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(8) {
            val candidate = directory?.let { File(it, "testdata") }
            if (candidate?.isDirectory == true) {
                return candidate
            }
            directory = directory?.parentFile
        }
        return null
    }

    private data class Sample(
        val fileName: String,
        val format: AudioFormat,
        val audioBytes: Long,
        val sha256: String,
        val title: String,
        val artist: String
    )
}
