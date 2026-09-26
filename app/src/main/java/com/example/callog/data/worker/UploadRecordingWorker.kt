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

import com.example.callog.domain.service.StorageErrorCategory
import com.example.callog.domain.service.StorageUploadException

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
                    val ex = outcome.exceptionOrNull()
                    val category = (ex as? StorageUploadException)?.category
                        ?: StorageErrorCategory.fromException(ex ?: Exception("Unknown error"))
                    val error = ex?.message ?: "Unknown error"
                    Log.e(TAG, "Background upload failed for call: $callId [$category] (retryable=${category.isRetryable}) - $error")

                    if (category.isRetryable) {
                        Log.w(TAG, "Failure for call $callId is retryable ($category). Returning Result.retry().")
                        Result.retry()
                    } else {
                        Log.e(TAG, "Deterministic failure for call $callId ($category). Halting retries and returning Result.failure(). Local recording remains safely stored.")
                        Result.failure()
                    }
                }
            } else {
                Log.w(TAG, "No call_id provided, checking for general retry of failed uploads")
                recordingRepository.retryFailedUploads()
                Result.success()
            }
        } catch (e: Exception) {
            val category = (e as? StorageUploadException)?.category ?: StorageErrorCategory.fromException(e)
            Log.e(TAG, "Exception in background worker execution [$category] (retryable=${category.isRetryable})", e)
            if (category.isRetryable) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
