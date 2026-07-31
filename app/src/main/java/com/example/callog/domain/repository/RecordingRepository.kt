package com.example.callog.domain.repository

import com.example.callog.data.local.entity.CallEntity

import com.example.callog.data.local.entity.RecordingEntity
import com.example.callog.data.local.entity.UploadStatus
import kotlinx.coroutines.flow.Flow

interface RecordingRepository {
    suspend fun scanRecordings()
    suspend fun uploadRecording(callId: Long): Result<String>
    suspend fun uploadRecordingDirect(recordingId: Long): Result<String>
    suspend fun retryFailedUploads()
    suspend fun updateFirestore(callId: Long, cloudPath: String, downloadUrl: String = ""): Boolean
    suspend fun deleteRecording(callId: Long): Boolean
    suspend fun associateRecording(callId: Long, localPath: String)
    
    fun getAllRecordingsFlow(): Flow<List<RecordingEntity>>
    suspend fun getAllRecordings(): List<RecordingEntity>
    suspend fun getRecordingById(id: Long): RecordingEntity?
    suspend fun clearAllRecordings()
    suspend fun manualMatchRecording(recordingId: Long, callId: Long)
    suspend fun updateRecordingUploadStatus(callId: Long, status: UploadStatus, cloudUrl: String? = null, reason: String? = null)
}
