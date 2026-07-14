package com.example.callog.domain.repository

import com.example.callog.data.local.entity.CallEntity

interface RecordingRepository {
    suspend fun scanRecordings()
    suspend fun uploadRecording(callId: Long): Result<String>
    suspend fun retryFailedUploads()
    suspend fun updateFirestore(callId: Long, cloudPath: String, downloadUrl: String = ""): Boolean
    suspend fun deleteRecording(callId: Long): Boolean
    suspend fun associateRecording(callId: Long, localPath: String)
}
