package com.example.callog.domain.service

import com.example.callog.data.local.entity.CallEntity
import kotlinx.coroutines.flow.Flow

sealed class UploadProgressState {
    data class Progress(val bytesTransferred: Long, val totalBytes: Long) : UploadProgressState()
    data class Success(val downloadUrl: String, val cloudPath: String) : UploadProgressState()
    data class Error(val exception: Throwable) : UploadProgressState()
}

interface UploadService {
    fun uploadRecording(call: CallEntity): Flow<UploadProgressState>
    suspend fun uploadMetadata(call: CallEntity, recordingUrl: String?): Result<Unit>
    fun cancelUpload(callId: Long)
}
