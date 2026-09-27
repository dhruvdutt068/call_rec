package com.example.callog.domain.service

import com.example.callog.data.local.entity.CallEntity
import com.example.callog.domain.model.RecordingCandidate
import com.example.callog.domain.model.RecordingMatchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecordingCorrelationMatcherTest {

    private lateinit var matcher: RecordingCorrelationMatcher

    @Before
    fun setUp() {
        matcher = RecordingCorrelationMatcher()
    }

    private fun createCall(
        id: Long = 101L,
        number: String = "+15551234567",
        duration: Int = 45, // seconds
        timestamp: Long = 1_700_000_000_000L,
        callType: String = "INCOMING",
        recordingPath: String? = null
    ): CallEntity {
        return CallEntity(
            id = id,
            name = "Test Lead",
            number = number,
            duration = duration,
            timestamp = timestamp,
            callType = callType,
            recordingPath = recordingPath
        )
    }

    private fun createCandidate(
        fileName: String = "Call_15551234567_20231114.m4a",
        filePath: String = "/storage/emulated/0/Recordings/Call_15551234567_20231114.m4a",
        phoneNumber: String? = "+15551234567",
        contactName: String? = "Test Lead",
        durationSeconds: Long = 45L,
        timestamp: Long = 1_700_000_045_000L, // At disconnect
        lastModified: Long = 1_700_000_046_000L,
        fileSize: Long = 250_000L,
        parserName: String = "SamsungParser"
    ): RecordingCandidate {
        return RecordingCandidate(
            filePath = filePath,
            fileName = fileName,
            phoneNumber = phoneNumber,
            contactName = contactName,
            timestamp = timestamp,
            durationSeconds = durationSeconds,
            fileSize = fileSize,
            lastModified = lastModified,
            parserName = parserName
        )
    }

    @Test
    fun `exact phone, disconnect proximity and duration match yields high confidence match`() {
        val call = createCall(id = 200L, duration = 60, timestamp = 1_700_000_000_000L)
        // Disconnect time is 1_700_000_060_000L
        val candidate = createCandidate(
            phoneNumber = "+15551234567",
            durationSeconds = 60L,
            timestamp = 1_700_000_062_000L, // 2s after disconnect (<= 15s)
            lastModified = 1_700_000_063_000L
        )

        val result = matcher.match(candidate, listOf(call))
        assertTrue("Expected Matched result but was $result", result is RecordingMatchResult.Matched)
        val matched = result as RecordingMatchResult.Matched
        assertEquals(200L, matched.callId)
        assertTrue(matched.confidence >= 0.90) // 0.40 (phone) + 0.30 (time) + 0.20 (duration) + 0.10 (mod) = 1.00
    }

    @Test
    fun `contradictory phone number is rejected by hard safety gate`() {
        val call = createCall(id = 201L, number = "+15551234567")
        val candidate = createCandidate(phoneNumber = "+15559998877") // Contradictory number

        val result = matcher.match(candidate, listOf(call))
        assertTrue("Expected NoMatch due to phone contradiction", result is RecordingMatchResult.NoMatch)
    }

    @Test
    fun `already associated call is disqualified by safety gate`() {
        val call = createCall(id = 202L, recordingPath = "/other/existing/recording.m4a")
        val candidate = createCandidate()

        val result = matcher.match(candidate, listOf(call))
        assertTrue("Expected NoMatch because call already has recording", result is RecordingMatchResult.NoMatch)
    }

    @Test
    fun `missed or rejected calls without audio are disqualified`() {
        val missedCall = createCall(id = 203L, callType = "MISSED")
        val candidate = createCandidate()

        val result = matcher.match(candidate, listOf(missedCall))
        assertTrue("Expected NoMatch for missed call", result is RecordingMatchResult.NoMatch)
    }

    @Test
    fun `large duration conflict is disqualified by safety gate`() {
        val call = createCall(id = 204L, duration = 30)
        val candidate = createCandidate(durationSeconds = 90L) // Delta 60s > 15s gate

        val result = matcher.match(candidate, listOf(call))
        assertTrue("Expected NoMatch for duration conflict", result is RecordingMatchResult.NoMatch)
    }

    @Test
    fun `competing equally plausible calls trigger Ambiguous result`() {
        val baseTime = 1_700_000_000_000L
        val call1 = createCall(id = 301L, number = "+15551234567", duration = 40, timestamp = baseTime)
        val call2 = createCall(id = 302L, number = "+15551234567", duration = 40, timestamp = baseTime + 1000L)

        val candidate = createCandidate(
            phoneNumber = "+15551234567",
            durationSeconds = 40L,
            timestamp = baseTime + 41_000L,
            lastModified = baseTime + 42_000L
        )

        val result = matcher.match(candidate, listOf(call1, call2))
        assertTrue("Expected Ambiguous match for close competing calls, but got $result", result is RecordingMatchResult.Ambiguous)
        val ambiguous = result as RecordingMatchResult.Ambiguous
        assertTrue(ambiguous.candidates.contains(301L))
        assertTrue(ambiguous.candidates.contains(302L))
    }
}
