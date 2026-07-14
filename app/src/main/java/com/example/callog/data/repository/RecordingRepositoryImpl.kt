package com.example.callog.data.repository

import android.content.Context
import android.util.Log
import com.example.callog.data.local.dao.CallDao
import com.example.callog.data.local.entity.CallEntity
import com.example.callog.data.provider.RecordingScanner
import com.example.callog.data.remote.FirestoreService
import com.example.callog.domain.repository.RecordingRepository
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callDao: CallDao,
    private val firestoreService: FirestoreService,
    private val recordingScanner: RecordingScanner
) : RecordingRepository {
    private val TAG = "RecordingRepository"

    private fun getStorage(): FirebaseStorage? {
        return try {
            val savedConfig = firestoreService.getSavedConfig()
            if (savedConfig != null) {
                val customApp = try {
                    FirebaseApp.getInstance("customApp")
                } catch (e: Exception) {
                    val options = FirebaseOptions.Builder()
                        .setProjectId(savedConfig.projectId)
                        .setApiKey(savedConfig.apiKey)
                        .setApplicationId(savedConfig.appId)
                        .build()
                    FirebaseApp.initializeApp(context, options, "customApp")
                }
                FirebaseStorage.getInstance(customApp)
            } else {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    val options = FirebaseOptions.Builder()
                        .setApplicationId("1:799427430422:android:e0f5737f12b8b9cbb20d37")
                        .setProjectId("allset-491218")
                        .setApiKey("AIzaSyBtlY7EoO6PgPUCMjNR55K88H2v665qQgQ")
                        .build()
                    FirebaseApp.initializeApp(context, options)
                }
                FirebaseStorage.getInstance("gs://allset_calllogs_bucket")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase Storage", e)
            null
        }
    }

    override suspend fun scanRecordings() = withContext(Dispatchers.IO) {
        try {
            val recordings = recordingScanner.scanRecordings().toMutableList()
            if (recordings.isEmpty()) return@withContext

            val dbCalls = callDao.getAllCalls()
            dbCalls.forEach { call ->
                if (call.recordingLocalPath == null && call.recordingPath == null) {
                    val matchingRec = recordings.firstOrNull { rec ->
                        val cleanCallNum = call.number.replace(Regex("[^0-9]"), "")
                        val isNameBased = rec.phoneNumber.any { it.isLetter() }
                        val match: Boolean

                        if (isNameBased) {
                            val callName = call.name?.trim()?.lowercase()
                            val recName = rec.phoneNumber.trim().lowercase()
                            val nameMatches = !callName.isNullOrEmpty() && recName.isNotEmpty() && (
                                callName == recName ||
                                callName.contains(recName) ||
                                recName.contains(callName)
                            )
                            val digitsInRec = rec.phoneNumber.replace(Regex("[^0-9]"), "")
                            val numberMatches = digitsInRec.isNotEmpty() && cleanCallNum.isNotEmpty() && (
                                cleanCallNum == digitsInRec ||
                                (cleanCallNum.length >= 7 && digitsInRec.endsWith(cleanCallNum.takeLast(7))) ||
                                (digitsInRec.length >= 7 && cleanCallNum.endsWith(digitsInRec.takeLast(7)))
                            )
                            match = nameMatches || numberMatches
                        } else {
                            val cleanRecNum = rec.phoneNumber.replace(Regex("[^0-9]"), "")
                            match = cleanCallNum.isNotEmpty() && cleanRecNum.isNotEmpty() && (
                                cleanCallNum == cleanRecNum ||
                                (cleanCallNum.length >= 7 && cleanRecNum.endsWith(cleanCallNum.takeLast(7))) ||
                                (cleanRecNum.length >= 7 && cleanCallNum.endsWith(cleanRecNum.takeLast(7)))
                            )
                        }
                        val durationMs = call.duration * 1000L
                        val timeMatch = rec.timestamp >= (call.timestamp - 300000L) && 
                                        rec.timestamp <= (call.timestamp + durationMs + 300000L)
                        match && timeMatch
                    }

                    if (matchingRec != null) {
                        recordings.remove(matchingRec)
                        callDao.updateCall(
                            call.copy(
                                recordingPath = matchingRec.filePath,
                                recordingLocalPath = matchingRec.filePath,
                                recordingUploadStatus = "PENDING"
                            )
                        )
                        Log.i(TAG, "Matched recording file ${matchingRec.filePath} with call to ${call.number}")
                        enqueueUploadWork(call.id)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in scanRecordings", e)
        }
    }

    override suspend fun uploadRecording(callId: Long): Result<String> = withContext(Dispatchers.IO) {
        val call = callDao.getCallById(callId)
            ?: return@withContext Result.failure(Exception("Call record not found in database: $callId"))

        if (call.recordingUploadStatus == "SUCCESS" && !call.recordingCloudPath.isNullOrEmpty()) {
            return@withContext Result.success(call.recordingCloudPath)
        }

        // Check if recording already exists in Firestore metadata to avoid duplicate upload
        try {
            val cleanNumber = FirestoreRepositoryImpl.normalizePhoneNumber(call.number)
            val firestoreType = FirestoreRepositoryImpl.getFirestoreCallType(call.callType)
            val remoteMetadata = firestoreService.getCallMetadata(cleanNumber, firestoreType, callId.toString())
            if (remoteMetadata != null) {
                val hasRec = remoteMetadata["hasRecording"] as? Boolean ?: false
                val remoteRecUrl = remoteMetadata["recordingUrl"] as? String ?: ""
                val remoteRecPath = remoteMetadata["recordingPath"] as? String ?: ""
                
                if (hasRec && remoteRecUrl.isNotEmpty() && remoteRecPath.isNotEmpty()) {
                    Log.i(TAG, "Recording already exists in Firestore for call $callId. Skipping upload and reusing: $remoteRecPath")
                    callDao.updateCall(
                        call.copy(
                            recordingCloudPath = remoteRecPath,
                            recordingUrl = remoteRecUrl,
                            recordingUploadStatus = "SUCCESS",
                            recordingUploadedAt = System.currentTimeMillis()
                        )
                    )
                    return@withContext Result.success(remoteRecPath)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking remote metadata in uploadRecording", e)
        }

        val localPath = call.recordingLocalPath ?: call.recordingPath
        if (localPath.isNullOrEmpty()) {
            return@withContext Result.failure(Exception("No local recording path found for call: $callId"))
        }

        val file = File(localPath)
        if (!file.exists()) {
            callDao.updateCall(call.copy(recordingUploadStatus = "FAILED"))
            return@withContext Result.failure(Exception("Local recording file does not exist: $localPath"))
        }

        val storageInstance = getStorage()
            ?: return@withContext Result.failure(Exception("Firebase Storage not initialized"))

        try {
            callDao.updateCall(call.copy(recordingUploadStatus = "UPLOADING"))

            val date = Date(call.timestamp)
            val year = SimpleDateFormat("yyyy", Locale.US).format(date)
            val month = SimpleDateFormat("MM", Locale.US).format(date)
            val day = SimpleDateFormat("dd", Locale.US).format(date)

            val devicePhone = firestoreService.getDevicePhoneNumber().ifEmpty { "unknown_device" }
            val normalizedDevicePhone = FirestoreRepositoryImpl.normalizePhoneNumber(devicePhone)

            val extension = file.extension.ifEmpty { "mp3" }
            val remotePath = "users/$normalizedDevicePhone/recordings/$year/$month/$day/call_$callId.$extension"

            Log.i(TAG, "Starting GCS upload to path: $remotePath")
            val ref = storageInstance.reference.child(remotePath)
            
            val fileUri = android.net.Uri.fromFile(file)
            ref.putFile(fileUri).await()

            Log.i(TAG, "GCS upload successful: $remotePath")
            
            val downloadUrl = try {
                ref.downloadUrl.await().toString()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get GCS download URL: ${e.message}", e)
                ""
            }

            val firestoreSuccess = updateFirestore(callId, remotePath, downloadUrl)
            if (firestoreSuccess) {
                callDao.updateCall(
                    call.copy(
                        recordingCloudPath = remotePath,
                        recordingUrl = downloadUrl,
                        recordingUploadStatus = "SUCCESS",
                        recordingUploadedAt = System.currentTimeMillis()
                    )
                )
                Result.success(remotePath)
            } else {
                callDao.updateCall(call.copy(recordingUploadStatus = "FAILED"))
                Result.failure(Exception("Failed to update Firestore metadata after upload"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading call recording $callId to GCS", e)
            callDao.updateCall(call.copy(recordingUploadStatus = "FAILED"))
            Result.failure(e)
        }
    }

    override suspend fun updateFirestore(callId: Long, cloudPath: String, downloadUrl: String): Boolean {
        val call = callDao.getCallById(callId) ?: return false
        val cleanNumber = FirestoreRepositoryImpl.normalizePhoneNumber(call.number)
        val firestoreType = FirestoreRepositoryImpl.getFirestoreCallType(call.callType)
        
        val updates = mapOf(
            "hasRecording" to true,
            "recordingPath" to cloudPath,
            "recordingUrl" to downloadUrl,
            "uploadedAt" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date())
        )
        return firestoreService.updateCallMetadata(cleanNumber, firestoreType, callId.toString(), updates)
    }

    override suspend fun retryFailedUploads() {
        withContext(Dispatchers.IO) {
            try {
                val calls = callDao.getAllCalls()
                val pendingUploads = calls.filter { 
                    (it.recordingLocalPath != null || it.recordingPath != null) && 
                    (it.recordingUploadStatus == "FAILED" || it.recordingUploadStatus == "PENDING") 
                }
                
                Log.i(TAG, "Found ${pendingUploads.size} failed or pending GCS uploads to retry.")
                for (call in pendingUploads) {
                    enqueueUploadWork(call.id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error retrying failed GCS uploads", e)
            }
        }
    }

    override suspend fun deleteRecording(callId: Long): Boolean = withContext(Dispatchers.IO) {
        val call = callDao.getCallById(callId) ?: return@withContext false
        val cloudPath = call.recordingCloudPath ?: return@withContext false
        
        val storageInstance = getStorage() ?: return@withContext false
        try {
            val ref = storageInstance.reference.child(cloudPath)
            ref.delete().await()
            Log.i(TAG, "Successfully deleted GCS recording object: $cloudPath")
            
            val cleanNumber = FirestoreRepositoryImpl.normalizePhoneNumber(call.number)
            val firestoreType = FirestoreRepositoryImpl.getFirestoreCallType(call.callType)
            val updates = mapOf(
                "hasRecording" to false,
                "recordingPath" to "",
                "recordingUrl" to "",
                "updatedAt" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date())
            )
            firestoreService.updateCallMetadata(cleanNumber, firestoreType, callId.toString(), updates)

            callDao.updateCall(
                call.copy(
                    recordingCloudPath = null,
                    recordingUrl = null,
                    recordingUploadStatus = "PENDING",
                    recordingUploadedAt = null
                )
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting call recording $callId from GCS", e)
            false
        }
    }

    override suspend fun associateRecording(callId: Long, localPath: String) {
        withContext(Dispatchers.IO) {
            val call = callDao.getCallById(callId)
            if (call != null) {
                callDao.updateCall(
                    call.copy(
                        recordingPath = localPath,
                        recordingLocalPath = localPath,
                        recordingUploadStatus = "PENDING"
                    )
                )
                Log.i(TAG, "Manually associated recording file $localPath with call $callId")
            }
        }
    }

    private fun enqueueUploadWork(callId: Long) {
        val data = androidx.work.Data.Builder()
            .putLong("call_id", callId)
            .build()
            
        val uploadWorkRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.callog.data.worker.UploadRecordingWorker>()
            .setInputData(data)
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            )
            .build()
            
        androidx.work.WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "upload_recording_$callId",
                androidx.work.ExistingWorkPolicy.REPLACE,
                uploadWorkRequest
            )
    }
}
