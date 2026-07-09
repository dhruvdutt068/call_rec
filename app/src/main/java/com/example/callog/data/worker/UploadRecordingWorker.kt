package com.example.callog.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.callog.domain.repository.RecordingRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class UploadRecordingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "UploadRecordingWorker"

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun recordingRepository(): RecordingRepository
    }

    override suspend fun doWork(): Result {
        val callId = inputData.getLong("call_id", -1L)
        Log.i(TAG, "Executing background GCS upload worker for call: $callId")

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkerEntryPoint::class.java
        )
        val recordingRepository = entryPoint.recordingRepository()

        return try {
            if (callId != -1L) {
                val outcome = recordingRepository.uploadRecording(callId)
                if (outcome.isSuccess) {
                    Log.i(TAG, "Background upload successful for call: $callId")
                    Result.success()
                } else {
                    val error = outcome.exceptionOrNull()?.message ?: "Unknown error"
                    Log.e(TAG, "Background upload failed for call: $callId - $error")
                    Result.retry()
                }
            } else {
                Log.w(TAG, "No call_id provided, checking for general retry of failed uploads")
                recordingRepository.retryFailedUploads()
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in background worker execution", e)
            Result.retry()
        }
    }
}
