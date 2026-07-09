package com.example.callog.domain.repository

import com.example.callog.data.local.entity.CallEntity

interface SyncRepository {
    suspend fun getPendingCalls(): List<CallEntity>
    suspend fun markUploading(callId: Long)
    suspend fun markSynced(callId: Long, cloudPath: String?, recordingUrl: String?, uploadedAt: Long)
    suspend fun markFailed(callId: Long, error: String, retryCount: Int, lastAttempt: Long)
    suspend fun resetSyncStatus(callId: Long)
    suspend fun getCallById(callId: Long): CallEntity?
}
