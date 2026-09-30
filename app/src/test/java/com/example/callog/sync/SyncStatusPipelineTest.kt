package com.example.callog.sync

import com.example.callog.data.local.entity.CallEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStatusPipelineTest {

    private fun determineCompletionStage(successCount: Int, failCount: Int): String {
        return if (failCount > 0 && successCount > 0) {
            "PARTIALLY_COMPLETED"
        } else if (failCount > 0) {
            "FAILED"
        } else {
            "COMPLETED"
        }
    }

    private fun isCallPendingSyncOrUpload(call: CallEntity): Boolean {
        return call.syncStatus != "SYNCED" || (call.recordingPath != null && call.recordingUploadStatus != "SUCCESS")
    }

    @Test
    fun testCompletionStageClassification() {
        assertEquals("COMPLETED", determineCompletionStage(successCount = 10, failCount = 0))
        assertEquals("PARTIALLY_COMPLETED", determineCompletionStage(successCount = 8, failCount = 2))
        assertEquals("FAILED", determineCompletionStage(successCount = 0, failCount = 5))
        assertEquals("COMPLETED", determineCompletionStage(successCount = 0, failCount = 0))
    }

    @Test
    fun testPendingCallsFilterIncludesUnuploadedRecordings() {
        // Completely synced call with no recording
        val syncedNoRec = CallEntity(
            id = 1L,
            name = "Alice",
            number = "+1234567890",
            duration = 30,
            timestamp = 1000L,
            callType = "INCOMING",
            recordingPath = null,
            syncStatus = "SYNCED",
            recordingUploadStatus = "SUCCESS"
        )
        assertFalse(isCallPendingSyncOrUpload(syncedNoRec))

        // Synced call with uploaded recording
        val syncedWithUploadedRec = CallEntity(
            id = 2L,
            name = "Bob",
            number = "+1234567891",
            duration = 45,
            timestamp = 2000L,
            callType = "OUTGOING",
            recordingPath = "/path/to/rec.mp3",
            syncStatus = "SYNCED",
            recordingUploadStatus = "SUCCESS"
        )
        assertFalse(isCallPendingSyncOrUpload(syncedWithUploadedRec))

        // Call metadata was synced earlier, but audio recording was matched later and is still PENDING
        val syncedMetadataPendingRec = CallEntity(
            id = 3L,
            name = "Charlie",
            number = "+1234567892",
            duration = 60,
            timestamp = 3000L,
            callType = "INCOMING",
            recordingPath = "/path/to/matched_rec.mp3",
            syncStatus = "SYNCED",
            recordingUploadStatus = "PENDING"
        )
        assertTrue("Call with matched pending recording must be picked up for sync", isCallPendingSyncOrUpload(syncedMetadataPendingRec))

        // Call metadata was synced, recording upload failed and needs retry
        val syncedMetadataFailedRec = CallEntity(
            id = 4L,
            name = "Diana",
            number = "+1234567893",
            duration = 15,
            timestamp = 4000L,
            callType = "OUTGOING",
            recordingPath = "/path/to/failed_rec.mp3",
            syncStatus = "SYNCED",
            recordingUploadStatus = "FAILED"
        )
        assertTrue("Call with failed recording upload must be retried", isCallPendingSyncOrUpload(syncedMetadataFailedRec))

        // Completely pending call
        val pendingCall = CallEntity(
            id = 5L,
            name = "Eve",
            number = "+1234567894",
            duration = 20,
            timestamp = 5000L,
            callType = "MISSED",
            recordingPath = null,
            syncStatus = "PENDING",
            recordingUploadStatus = "PENDING"
        )
        assertTrue(isCallPendingSyncOrUpload(pendingCall))
    }
}
