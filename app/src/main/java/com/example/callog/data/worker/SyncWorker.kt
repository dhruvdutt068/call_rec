package com.example.callog.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.callog.data.local.entity.CallEntity
import com.example.callog.core.utils.ConnectivityService
import com.example.callog.domain.repository.CallRepository
import com.example.callog.domain.repository.RecordingRepository
import com.example.callog.domain.repository.SyncRepository
import com.example.callog.domain.service.UploadProgressState
import com.example.callog.domain.service.UploadService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.collect

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "SyncWorker"

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun syncRepository(): SyncRepository
        fun uploadService(): UploadService
        fun callRepository(): CallRepository
        fun recordingRepository(): RecordingRepository
        fun connectivityService(): ConnectivityService
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Executing background SyncWorker")

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkerEntryPoint::class.java
        )

        val syncRepository = entryPoint.syncRepository()
        val uploadService = entryPoint.uploadService()
        val callRepository = entryPoint.callRepository()
        val recordingRepository = entryPoint.recordingRepository()
        val connectivityService = entryPoint.connectivityService()

        // Step 1: Ensure we are online before initiating sync
        if (!connectivityService.isConnected()) {
            Log.w(TAG, "Device is offline. Suspending sync run.")
            return Result.retry()
        }

        try {
            // Step 2: Fetch and import call logs from provider, match files, etc.
            Log.i(TAG, "Scanning system logs & local recordings to refresh DB")
            callRepository.syncCallLogs()

            // Step 3: Fetch all pending items from local database
            val pendingCalls = syncRepository.getPendingCalls()
            if (pendingCalls.isEmpty()) {
                Log.d(TAG, "No pending calls found to synchronize.")
                return Result.success()
            }

            Log.i(TAG, "Found ${pendingCalls.size} pending calls to sync.")
            val currentTime = System.currentTimeMillis()

            for (call in pendingCalls) {
                // Ensure we are still online before processing each call
                if (!connectivityService.isConnected()) {
                    Log.w(TAG, "Connection lost during sync loop. Suspending.")
                    return Result.retry()
                }

                // Check for max retries limit
                if (call.retryCount >= 5) {
                    Log.w(TAG, "Call ${call.id} exceeded maximum retries (${call.retryCount}). Skipping.")
                    continue
                }

                // Check for exponential backoff on retry-pending calls
                if (call.syncStatus == "FAILED" && call.lastAttempt != null) {
                    val backoffDuration = getBackoffDuration(call.retryCount)
                    if (currentTime < call.lastAttempt + backoffDuration) {
                        Log.d(TAG, "Skipping call ${call.id} due to exponential backoff constraint.")
                        continue
                    }
                }

                Log.i(TAG, "Processing sync for call: ${call.id} (attempt: ${call.retryCount + 1})")
                syncRepository.markUploading(call.id)

                var recordingUrl: String? = null
                var cloudPath: String? = null
                var uploadFailed = false

                // Step 4: If recording path exists and is not uploaded, upload it first
                if (call.recordingPath != null && call.recordingUploadStatus != "SUCCESS") {
                    var uploadResult: com.example.callog.domain.service.UploadProgressState? = null
                    
                    try {
                        uploadService.uploadRecording(call).collect { state ->
                            uploadResult = state
                            if (state is UploadProgressState.Progress) {
                                setProgress(workDataOf("progress" to "Uploading recording for ${call.id}: ${state.bytesTransferred}/${state.totalBytes}"))
                            }
                        }

                        when (val finalState = uploadResult) {
                            is UploadProgressState.Success -> {
                                recordingUrl = finalState.downloadUrl
                                cloudPath = finalState.cloudPath
                            }
                            is UploadProgressState.Error -> {
                                val errMessage = finalState.exception.message ?: "Unknown upload error"
                                Log.e(TAG, "Failed uploading recording for call ${call.id}: $errMessage")
                                syncRepository.markFailed(call.id, errMessage, call.retryCount + 1, currentTime)
                                uploadFailed = true
                            }
                            else -> {
                                Log.e(TAG, "Failed uploading recording for call ${call.id}: Completed with no result")
                                syncRepository.markFailed(call.id, "Completed with no result", call.retryCount + 1, currentTime)
                                uploadFailed = true
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in recording upload stream", e)
                        syncRepository.markFailed(call.id, "Upload stream exception: ${e.message}", call.retryCount + 1, currentTime)
                        uploadFailed = true
                    }
                }

                if (uploadFailed) continue

                // Step 5: Upload/Update metadata in Firestore
                try {
                    val result = uploadService.uploadMetadata(call, recordingUrl)
                    if (result.isSuccess) {
                        Log.i(TAG, "Successfully synced metadata for call: ${call.id}")
                        syncRepository.markSynced(call.id, cloudPath, recordingUrl, currentTime)
                    } else {
                        val errMsg = result.exceptionOrNull()?.message ?: "Metadata upload failure"
                        Log.e(TAG, "Failed metadata upload for call ${call.id}: $errMsg")
                        syncRepository.markFailed(call.id, errMsg, call.retryCount + 1, currentTime)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during metadata sync for call ${call.id}", e)
                    syncRepository.markFailed(call.id, "Metadata upload exception: ${e.message}", call.retryCount + 1, currentTime)
                }
            }

            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "SyncWorker run encountered critical exception", e)
            return Result.retry()
        }
    }

    private fun getBackoffDuration(retryCount: Int): Long {
        val exponent = (retryCount - 1).coerceIn(0, 4)
        val minutes = Math.pow(2.0, exponent.toDouble()).toLong()
        return minutes * 60 * 1000L
    }
}
