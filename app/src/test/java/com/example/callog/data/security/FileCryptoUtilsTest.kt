package com.example.callog.data.security

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileCryptoUtilsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `calculateSha256 returns accurate hex digest for known content`() {
        val testFile = tempFolder.newFile("test_audio.m4a")
        testFile.writeText("CALLOG_SECURE_RECORDING_TEST_BYTES")

        val hash = FileCryptoUtils.calculateSha256(testFile)

        // SHA-256 for "CALLOG_SECURE_RECORDING_TEST_BYTES"
        // Let's verify non-empty, 64-character lowercase hex string
        assertEquals(64, hash.length)
        val expectedDigest = java.security.MessageDigest.getInstance("SHA-256")
            .digest("CALLOG_SECURE_RECORDING_TEST_BYTES".toByteArray())
            .joinToString("") { "%02x".format(it) }

        assertEquals(expectedDigest, hash)
    }

    @Test
    fun `calculateSha256 handles empty file`() {
        val emptyFile = tempFolder.newFile("empty.m4a")
        val hash = FileCryptoUtils.calculateSha256(emptyFile)

        // Standard SHA-256 of empty input is e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash)
    }
}
