package com.example.callog.data.repository

import com.example.callog.data.local.dao.CallDao
import com.example.callog.data.local.entity.CallEntity
import com.example.callog.domain.repository.SyncRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val callDao: CallDao
) : SyncRepository {

    override suspend fun getPendingCalls(): List<CallEntity> {
        return callDao.getPendingCalls()
    }

    override suspend fun markUploading(callId: Long) {
        callDao.getCallById(callId)?.let { call ->
            callDao.updateCall(
                call.copy(
                    syncStatus = "UPLOADING",
                    recordingUploadStatus = if (call.recordingPath != null) "UPLOADING" else "PENDING"
                )
            )
        }
    }

    override suspend fun markSynced(
        callId: Long,
        cloudPath: String?,
        recordingUrl: String?,
        uploadedAt: Long
    ) {
        callDao.getCallById(callId)?.let { call ->
            callDao.updateCall(
                call.copy(
                    syncStatus = "SYNCED",
                    recordingCloudPath = cloudPath ?: call.recordingCloudPath,
                    recordingUrl = recordingUrl ?: call.recordingUrl,
                    recordingUploadStatus = if (call.recordingPath != null || cloudPath != null) "SUCCESS" else "PENDING",
                    uploadedAt = uploadedAt,
                    syncError = null,
                    retryCount = 0
                )
            )
        }
    }

    override suspend fun markFailed(
        callId: Long,
        error: String,
        retryCount: Int,
        lastAttempt: Long
    ) {
        callDao.getCallById(callId)?.let { call ->
            callDao.updateCall(
                call.copy(
                    syncStatus = "FAILED",
                    recordingUploadStatus = if (call.recordingPath != null) "FAILED" else "PENDING",
                    syncError = error,
                    retryCount = retryCount,
                    lastAttempt = lastAttempt
                )
            )
        }
    }

    override suspend fun resetSyncStatus(callId: Long) {
        callDao.getCallById(callId)?.let { call ->
            callDao.updateCall(
                call.copy(
                    syncStatus = "PENDING",
                    recordingUploadStatus = if (call.recordingPath != null) "PENDING" else "PENDING",
                    syncError = null,
                    retryCount = 0,
                    lastAttempt = null
                )
            )
        }
    }

    override suspend fun getCallById(callId: Long): CallEntity? {
        return callDao.getCallById(callId)
    }
}
