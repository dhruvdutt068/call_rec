package com.example.callog

import com.example.callog.data.local.entity.CallEntity
import com.example.callog.data.local.entity.MatchStatus
import com.example.callog.data.local.entity.RecordingEntity
import com.example.callog.data.local.entity.UploadStatus
import com.example.callog.data.service.UploadServiceImpl
import com.example.callog.domain.service.StorageErrorCategory
import com.example.callog.domain.service.StorageUploadException
import com.google.firebase.storage.StorageException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class StorageUploadTest {

    @Test
    fun testStorageErrorClassification_permissionDenied403() {
        val storageException = StorageException.fromExceptionAndHttpCode(null, 403)!!
        val category = StorageErrorCategory.fromException(storageException)
        assertEquals(StorageErrorCategory.PERMISSION_DENIED, category)
        assertFalse("HTTP 403 Permission Denied must NOT be retryable", category.isRetryable)
    }

    @Test
    fun testStorageErrorClassification_authenticationRequired401() {
        val storageException = StorageException.fromExceptionAndHttpCode(null, 401)!!
        val category = StorageErrorCategory.fromException(storageException)
        assertEquals(StorageErrorCategory.AUTHENTICATION_REQUIRED, category)
        assertFalse("HTTP 401 Auth Required must NOT be retryable", category.isRetryable)
    }

    @Test
    fun testStorageErrorClassification_notFound404() {
        val storageException = StorageException.fromExceptionAndHttpCode(null, 404)!!
        val category = StorageErrorCategory.fromException(storageException)
        assertEquals(StorageErrorCategory.BUCKET_OR_OBJECT_NOT_FOUND, category)
        assertFalse("HTTP 404 must NOT be retryable", category.isRetryable)
    }

    @Test
    fun testStorageErrorClassification_serverError5xx() {
        val storageException = StorageException.fromExceptionAndHttpCode(null, 503)!!
        val category = StorageErrorCategory.fromException(storageException)
        assertEquals(StorageErrorCategory.RETRYABLE_SERVER_ERROR, category)
        assertTrue("HTTP 5xx Server Error must be retryable", category.isRetryable)
    }

    @Test
    fun testStorageErrorClassification_networkFailures() {
        val timeoutEx = SocketTimeoutException("Connection timed out to Firebase Storage")
        val connectEx = ConnectException("Failed to connect to storage.googleapis.com")
        val dnsEx = UnknownHostException("Unable to resolve host storage.googleapis.com")

        assertTrue("Timeout must be retryable", StorageErrorCategory.fromException(timeoutEx).isRetryable)
        assertTrue("Connect exception must be retryable", StorageErrorCategory.fromException(connectEx).isRetryable)
        assertTrue("DNS failure must be retryable", StorageErrorCategory.fromException(dnsEx).isRetryable)
        assertEquals(StorageErrorCategory.RETRYABLE_NETWORK_ERROR, StorageErrorCategory.fromException(timeoutEx))
    }

    @Test
    fun testStorageUploadException_diagnosticsFieldsWithoutCredentials() {
        val storageEx = StorageException.fromExceptionAndHttpCode(null, 403)!!
        val ex = StorageUploadException(
            category = StorageErrorCategory.PERMISSION_DENIED,
            errorCode = storageEx.errorCode,
            httpResultCode = storageEx.httpResultCode,
            uploadPath = "users/919876543210/recordings/2026/09/26/call_17010.m4a",
            bucketName = "allset-491218.firebasestorage.app",
            hasFirebaseAuth = false,
            message = "Permission denied for call 17010"
        )

        assertEquals(StorageErrorCategory.PERMISSION_DENIED, ex.category)
        assertEquals(403, ex.httpResultCode)
        assertFalse("Must be non-retryable", ex.isRetryable)
        assertFalse("Should correctly track that Firebase Auth was not present", ex.hasFirebaseAuth)
        assertEquals("users/919876543210/recordings/2026/09/26/call_17010.m4a", ex.uploadPath)
        assertEquals("allset-491218.firebasestorage.app", ex.bucketName)
        
        // Ensure no security tokens or passwords leaked
        val message = ex.message ?: ""
        assertFalse(message.contains("password", ignoreCase = true))
        assertFalse(message.contains("token", ignoreCase = true))
        assertFalse(message.contains("bearer", ignoreCase = true))
    }

    @Test
    fun testStorageUploadException_authenticatedUserAllowedPath() {
        val ex = StorageUploadException(
            category = StorageErrorCategory.UNKNOWN_ERROR,
            errorCode = null,
            httpResultCode = 200,
            uploadPath = "users/919876543210/recordings/2026/09/26/call_17010.m4a",
            bucketName = "allset-491218.firebasestorage.app",
            hasFirebaseAuth = true,
            message = "Success"
        )
        assertTrue("Authenticated user flag must be true", ex.hasFirebaseAuth)
        assertEquals("allset-491218.firebasestorage.app", ex.bucketName)
    }

    @Test
    fun testUploadStateTransitions_successfulUpload() {
        // Initial state
        var recording = RecordingEntity(
            id = 101,
            filePath = "/sdcard/recordings/call_17010.m4a",
            fileName = "call_17010.m4a",
            fileSize = 1024L,
            duration = 30L,
            lastModified = System.currentTimeMillis(),
            phoneExtracted = "9876543210",
            timestampExtracted = System.currentTimeMillis(),
            matchedCallId = 17010L,
            matchStatus = MatchStatus.MATCHED,
            uploadStatus = UploadStatus.PENDING
        )
        assertEquals(UploadStatus.PENDING, recording.uploadStatus)

        // Transition to UPLOADING
        recording = recording.copy(uploadStatus = UploadStatus.UPLOADING)
        assertEquals(UploadStatus.UPLOADING, recording.uploadStatus)

        // Transition to UPLOADED
        recording = recording.copy(
            uploadStatus = UploadStatus.UPLOADED,
            cloudUrl = "https://firebasestorage.googleapis.com/v0/b/allset-491218.firebasestorage.app/o/call_17010.m4a"
        )
        assertEquals(UploadStatus.UPLOADED, recording.uploadStatus)
        assertNotNull(recording.cloudUrl)
    }

    @Test
    fun testUploadStateTransitions_permissionDeniedFailureRetainsLocalFile() {
        val localFilePath = "/data/user/0/com.example.callog/files/recordings/call_17010.m4a"
        
        // Initial state
        var call = CallEntity(
            id = 17010L,
            name = "Test Caller",
            number = "+919876543210",
            timestamp = System.currentTimeMillis(),
            duration = 45,
            callType = "INCOMING",
            recordingPath = localFilePath,
            recordingLocalPath = localFilePath,
            recordingUploadStatus = "PENDING"
        )
        var recording = RecordingEntity(
            id = 101,
            filePath = localFilePath,
            fileName = "call_17010.m4a",
            fileSize = 1024L,
            duration = 45L,
            lastModified = System.currentTimeMillis(),
            phoneExtracted = "919876543210",
            timestampExtracted = System.currentTimeMillis(),
            matchedCallId = 17010L,
            matchStatus = MatchStatus.MATCHED,
            uploadStatus = UploadStatus.PENDING
        )

        // Transition: PENDING -> UPLOADING
        call = call.copy(recordingUploadStatus = "UPLOADING")
        recording = recording.copy(uploadStatus = UploadStatus.UPLOADING)
        assertEquals("UPLOADING", call.recordingUploadStatus)
        assertEquals(UploadStatus.UPLOADING, recording.uploadStatus)

        // When 403 Permission Denied occurs:
        val category = StorageErrorCategory.PERMISSION_DENIED
        assertFalse(category.isRetryable)

        // Transition: UPLOADING -> FAILED
        call = call.copy(recordingUploadStatus = "FAILED")
        recording = recording.copy(
            uploadStatus = UploadStatus.FAILED,
            reason = "Category: PERMISSION_DENIED, HTTP 403: User does not have permission"
        )

        assertEquals("FAILED", call.recordingUploadStatus)
        assertEquals(UploadStatus.FAILED, recording.uploadStatus)
        assertEquals(localFilePath, recording.filePath)
        assertEquals(localFilePath, call.recordingLocalPath)
        // Local recording path must remain populated and not deleted
        assertTrue("Local path must be preserved for offline-first resilience", recording.filePath.isNotEmpty())
    }

    @Test
    fun testGenerateRemoteRecordingPath_includesCallerNameAndNumber() {
        val call = CallEntity(
            id = 17010L,
            name = "John Doe",
            number = "+91 98765 43210",
            duration = 60,
            timestamp = 1760000000000L,
            callType = "INCOMING",
            recordingPath = "/sdcard/rec.m4a"
        )
        val path = UploadServiceImpl.generateRemoteRecordingPath(
            normalizedDevicePhone = "919876543210",
            call = call,
            extension = "m4a"
        )

        assertTrue("Path should contain caller name", path.contains("John_Doe"))
        assertTrue("Path should contain normalized number", path.contains("919876543210"))
        assertTrue("Path should contain call id", path.contains("call_17010_"))
        assertTrue("Path should end with extension", path.endsWith(".m4a"))
        assertTrue("Path should follow date hierarchy", path.startsWith("users/919876543210/recordings/"))
    }

    @Test
    fun testGenerateRemoteRecordingPath_sanitizesSpecialCharactersInCallerName() {
        val call = CallEntity(
            id = 20500L,
            name = "Alice / Office (Client #1)!",
            number = "+1 (555) 123-4567",
            duration = 120,
            timestamp = 1760000000000L,
            callType = "OUTGOING",
            recordingPath = "/sdcard/rec.mp3"
        )
        val path = UploadServiceImpl.generateRemoteRecordingPath(
            normalizedDevicePhone = "15551234567",
            call = call,
            extension = "mp3"
        )

        assertFalse("Should not contain spaces", path.contains(" "))
        assertFalse("Should not contain slashes in filename component", path.substringAfterLast("/").contains("/"))
        assertFalse("Should not contain parens", path.contains("(") || path.contains(")"))
        assertTrue("Should clean name with underscores", path.contains("Alice_Office_Client_1"))
        assertTrue("Path should end with mp3", path.endsWith(".mp3"))
    }

    @Test
    fun testGenerateRemoteRecordingPath_handlesNullOrBlankCallerName() {
        val call = CallEntity(
            id = 30000L,
            name = null,
            number = "+44 7911 123456",
            duration = 30,
            timestamp = 1760000000000L,
            callType = "MISSED",
            recordingPath = "/sdcard/rec.wav"
        )
        val path = UploadServiceImpl.generateRemoteRecordingPath(
            normalizedDevicePhone = "447911123456",
            call = call,
            extension = "wav"
        )

        assertTrue("Should fallback to Unknown for null caller name", path.contains("Unknown"))
        assertTrue("Should contain call id", path.contains("call_30000_Unknown_"))
        assertTrue("Should end with wav", path.endsWith(".wav"))
    }
}
