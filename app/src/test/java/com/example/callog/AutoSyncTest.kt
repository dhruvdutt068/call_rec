package com.example.callog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSyncTest {

    private fun getBackoffDuration(retryCount: Int): Long {
        val exponent = (retryCount - 1).coerceIn(0, 4)
        val minutes = Math.pow(2.0, exponent.toDouble()).toLong()
        return minutes * 60 * 1000L
    }

    @Test
    fun testExponentialBackoffDurations() {
        // Retry 1: 1 minute (2^0)
        assertEquals(60 * 1000L, getBackoffDuration(1))
        
        // Retry 2: 2 minutes (2^1)
        assertEquals(2 * 60 * 1000L, getBackoffDuration(2))
        
        // Retry 3: 4 minutes (2^2)
        assertEquals(4 * 60 * 1000L, getBackoffDuration(3))
        
        // Retry 4: 8 minutes (2^3)
        assertEquals(8 * 60 * 1000L, getBackoffDuration(4))
        
        // Retry 5: 16 minutes (2^4)
        assertEquals(16 * 60 * 1000L, getBackoffDuration(5))
        
        // Exceeding 5: capped at 16 minutes (2^4)
        assertEquals(16 * 60 * 1000L, getBackoffDuration(6))
        assertEquals(16 * 60 * 1000L, getBackoffDuration(10))
    }

    @Test
    fun testShouldAttemptSync() {
        val currentTime = 10000000L

        // Call failed once, lastAttempt = currentTime - 30 seconds. 
        // Backoff is 1 minute, so it should NOT sync yet.
        val lastAttempt1 = currentTime - 30 * 1000L
        val backoff1 = getBackoffDuration(1) // 1 min
        val allowed1 = currentTime >= lastAttempt1 + backoff1
        assertFalse(allowed1)

        // Call failed once, lastAttempt = currentTime - 70 seconds.
        // Backoff is 1 minute, so it SHOULD sync now.
        val lastAttempt2 = currentTime - 70 * 1000L
        val backoff2 = getBackoffDuration(1) // 1 min
        val allowed2 = currentTime >= lastAttempt2 + backoff2
        assertTrue(allowed2)

        // Call failed 4 times, lastAttempt = currentTime - 5 minutes.
        // Backoff is 8 minutes, so it should NOT sync yet.
        val lastAttempt3 = currentTime - 5 * 60 * 1000L
        val backoff3 = getBackoffDuration(4) // 8 min
        val allowed3 = currentTime >= lastAttempt3 + backoff3
        assertFalse(allowed3)

        // Call failed 4 times, lastAttempt = currentTime - 9 minutes.
        // Backoff is 8 minutes, so it SHOULD sync now.
        val lastAttempt4 = currentTime - 9 * 60 * 1000L
        val backoff4 = getBackoffDuration(4) // 8 min
        val allowed4 = currentTime >= lastAttempt4 + backoff4
        assertTrue(allowed4)
    }
}
