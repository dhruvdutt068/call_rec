package com.example.callog.data.repository

import android.content.Context
import android.util.Log
import com.example.callog.data.local.dao.CallDao
import com.example.callog.data.local.dao.RecordingLogDao
import com.example.callog.data.local.entity.CallEntity
import com.example.callog.data.local.entity.RecordingLogEntity
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

import com.example.callog.data.local.entity.RecordingEntity
import com.example.callog.data.local.entity.MatchStatus
import com.example.callog.data.local.entity.UploadStatus
import kotlinx.coroutines.flow.Flow

@Singleton
class RecordingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callDao: CallDao,
    private val firestoreService: FirestoreService,
    private val recordingScanner: RecordingScanner,
    private val contactsProvider: com.example.callog.data.provider.ContactsProvider,
    private val recordingLogDao: com.example.callog.data.local.dao.RecordingLogDao,
    private val recordingDao: com.example.callog.data.local.dao.RecordingDao
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

    private fun normalizePhoneNumber(num: String): String {
        val clean = num.replace(Regex("[^0-9]"), "")
        return when {
            clean.length == 10 -> "91$clean"
            clean.length > 10 && clean.startsWith("0") -> "91${clean.substring(1)}"
            else -> clean
        }
    }

    override suspend fun scanRecordings() = withContext(Dispatchers.IO) {
        try {
            val scanId = java.util.UUID.randomUUID().toString()
            val recordings = recordingScanner.scanRecordings().toMutableList()
            Log.d(TAG, "RecordingRepository: Starting scan session $scanId of ${recordings.size} discovered recording(s)")
            if (recordings.isEmpty()) return@withContext

            val dbCalls = callDao.getAllCalls()
            val contacts = try {
                contactsProvider.fetchContacts()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch contacts in RecordingRepositoryImpl", e)
                emptyList()
            }

            recordings.forEach { rec ->
                val fileName = File(rec.filePath).name
                val existingRecording = recordingDao.getRecordingByPath(rec.filePath)
                
                // If it is already matched or uploaded, make sure the CallEntity is synced, and don't re-run match
                if (existingRecording != null && (existingRecording.matchStatus == MatchStatus.MATCHED || existingRecording.uploadStatus == UploadStatus.UPLOADED)) {
                    existingRecording.matchedCallId?.let { cid ->
                        val call = callDao.getCallById(cid)
                        if (call != null && (call.recordingLocalPath == null || call.recordingPath == null)) {
                            callDao.updateCall(
                                call.copy(
                                    recordingPath = rec.filePath,
                                    recordingLocalPath = rec.filePath,
                                    recordingUploadStatus = if (existingRecording.uploadStatus == UploadStatus.UPLOADED) "SUCCESS" else "PENDING"
                                )
                            )
                        }
                    }
                    return@forEach
                }

                val durationSec = rec.durationMs / 1000L

                // 1. Check if parser failed
                if (rec.phoneNumber == null && rec.contactName == null) {
                    val recordingEntity = RecordingEntity(
                        id = existingRecording?.id ?: 0,
                        filePath = rec.filePath,
                        fileName = fileName,
                        fileSize = rec.fileSize,
                        duration = durationSec,
                        lastModified = rec.lastModified,
                        phoneExtracted = null,
                        contactExtracted = null,
                        timestampExtracted = rec.timestamp,
                        matchedCallId = null,
                        matchStatus = MatchStatus.PARSER_FAILED,
                        uploadStatus = UploadStatus.PENDING,
                        parser = rec.parserName,
                        reason = "Unknown filename pattern"
                    )
                    recordingDao.insertRecording(recordingEntity)
                    recordingLogDao.insertLog(
                        RecordingLogEntity(
                            scanId = scanId,
                            fileName = fileName,
                            path = rec.filePath,
                            parser = rec.parserName,
                            phoneExtracted = null,
                            timestampExtracted = rec.timestamp,
                            candidateCount = 0,
                            matchedCallId = null,
                            status = "PARSER_FAILED",
                            reason = "Unknown filename pattern"
                        )
                    )
                    return@forEach
                }

                // 2. Find call logs within ±60 seconds
                val candidates = dbCalls.filter { call ->
                    rec.timestamp >= (call.timestamp - 60000L) && 
                    rec.timestamp <= (call.timestamp + 60000L)
                }

                if (candidates.isEmpty()) {
                    val recordingEntity = RecordingEntity(
                        id = existingRecording?.id ?: 0,
                        filePath = rec.filePath,
                        fileName = fileName,
                        fileSize = rec.fileSize,
                        duration = durationSec,
                        lastModified = rec.lastModified,
                        phoneExtracted = rec.phoneNumber,
                        contactExtracted = rec.contactName,
                        timestampExtracted = rec.timestamp,
                        matchedCallId = null,
                        matchStatus = MatchStatus.UNMATCHED,
                        uploadStatus = UploadStatus.PENDING,
                        parser = rec.parserName,
                        reason = "No call log within ±60 seconds"
                    )
                    recordingDao.insertRecording(recordingEntity)
                    recordingLogDao.insertLog(
                        RecordingLogEntity(
                            scanId = scanId,
                            fileName = fileName,
                            path = rec.filePath,
                            parser = rec.parserName,
                            phoneExtracted = rec.phoneNumber ?: rec.contactName,
                            timestampExtracted = rec.timestamp,
                            candidateCount = 0,
                            matchedCallId = null,
                            status = "UNMATCHED",
                            reason = "No call log within ±60 seconds"
                        )
                    )
                    return@forEach
                }

                // 3. If only one candidate -> Match
                if (candidates.size == 1) {
                    val bestCall = candidates[0]
                    val recordingEntity = RecordingEntity(
                        id = existingRecording?.id ?: 0,
                        filePath = rec.filePath,
                        fileName = fileName,
                        fileSize = rec.fileSize,
                        duration = durationSec,
                        lastModified = rec.lastModified,
                        phoneExtracted = rec.phoneNumber,
                        contactExtracted = rec.contactName,
                        timestampExtracted = rec.timestamp,
                        matchedCallId = bestCall.id,
                        matchStatus = MatchStatus.MATCHED,
                        uploadStatus = UploadStatus.PENDING,
                        parser = rec.parserName,
                        reason = "Matched to unique call within ±60 seconds"
                    )
                    recordingDao.insertRecording(recordingEntity)
                    callDao.updateCall(
                        bestCall.copy(
                            recordingPath = rec.filePath,
                            recordingLocalPath = rec.filePath,
                            recordingUploadStatus = "PENDING"
                        )
                    )
                    Log.i(TAG, "Matched unique candidate: ${rec.filePath} -> Call ID ${bestCall.id}")
                    enqueueUploadWork(bestCall.id)

                    recordingLogDao.insertLog(
                        RecordingLogEntity(
                            scanId = scanId,
                            fileName = fileName,
                            path = rec.filePath,
                            parser = rec.parserName,
                            phoneExtracted = rec.phoneNumber ?: rec.contactName,
                            timestampExtracted = rec.timestamp,
                            candidateCount = 1,
                            matchedCallId = bestCall.id,
                            status = "MATCHED",
                            reason = null
                        )
                    )
                    return@forEach
                }

                // 4. If multiple candidates -> Use phone number (if available)
                var matchedByPhone: List<CallEntity> = emptyList()
                if (rec.phoneNumber != null) {
                    val normRecNum = normalizePhoneNumber(rec.phoneNumber)
                    matchedByPhone = candidates.filter { call ->
                        val normCallNum = normalizePhoneNumber(call.number)
                        normCallNum.isNotEmpty() && normRecNum.isNotEmpty() && (
                            normCallNum == normRecNum ||
                            (normCallNum.length >= 7 && normRecNum.endsWith(normCallNum.takeLast(7))) ||
                            (normRecNum.length >= 7 && normCallNum.endsWith(normRecNum.takeLast(7)))
                        )
                    }
                } else if (rec.contactName != null) {
                    val recNameClean = rec.contactName.trim().lowercase()
                    matchedByPhone = candidates.filter { call ->
                        val callNameClean = call.name?.trim()?.lowercase() ?: ""
                        if (callNameClean.isNotEmpty() && (
                            callNameClean == recNameClean ||
                            callNameClean.contains(recNameClean) ||
                            recNameClean.contains(callNameClean)
                        )) {
                            true
                        } else {
                            val matchedContacts = contacts.filter { contact ->
                                val cName = contact.name.trim().lowercase()
                                cName == recNameClean || cName.contains(recNameClean) || recNameClean.contains(cName)
                            }
                            val contactPhones = matchedContacts.flatMap { it.phoneNumbers }.map { normalizePhoneNumber(it) }
                            val normCallNum = normalizePhoneNumber(call.number)
                            
                            contactPhones.any { cPhone ->
                                cPhone.isNotEmpty() && normCallNum.isNotEmpty() && (
                                    cPhone == normCallNum ||
                                    (cPhone.length >= 7 && normCallNum.endsWith(cPhone.takeLast(7))) ||
                                    (normCallNum.length >= 7 && cPhone.endsWith(normCallNum.takeLast(7)))
                                )
                            }
                        }
                    }
                }

                if (matchedByPhone.size == 1) {
                    val bestCall = matchedByPhone[0]
                    val recordingEntity = RecordingEntity(
                        id = existingRecording?.id ?: 0,
                        filePath = rec.filePath,
                        fileName = fileName,
                        fileSize = rec.fileSize,
                        duration = durationSec,
                        lastModified = rec.lastModified,
                        phoneExtracted = rec.phoneNumber,
                        contactExtracted = rec.contactName,
                        timestampExtracted = rec.timestamp,
                        matchedCallId = bestCall.id,
                        matchStatus = MatchStatus.MATCHED,
                        uploadStatus = UploadStatus.PENDING,
                        parser = rec.parserName,
                        reason = "Matched by phone number from multiple candidates"
                    )
                    recordingDao.insertRecording(recordingEntity)
                    callDao.updateCall(
                        bestCall.copy(
                            recordingPath = rec.filePath,
                            recordingLocalPath = rec.filePath,
                            recordingUploadStatus = "PENDING"
                        )
                    )
                    Log.i(TAG, "Matched by phone: ${rec.filePath} -> Call ID ${bestCall.id}")
                    enqueueUploadWork(bestCall.id)

                    recordingLogDao.insertLog(
                        RecordingLogEntity(
                            scanId = scanId,
                            fileName = fileName,
                            path = rec.filePath,
                            parser = rec.parserName,
                            phoneExtracted = rec.phoneNumber ?: rec.contactName,
                            timestampExtracted = rec.timestamp,
                            candidateCount = candidates.size,
                            matchedCallId = bestCall.id,
                            status = "MATCHED",
                            reason = null
                        )
                    )
                    return@forEach
                }

                // 5. If still tied -> Use call duration
                val baseCandidatesForDuration = if (matchedByPhone.isNotEmpty()) matchedByPhone else candidates
                val durationTolerance = 5
                val durationMatched = baseCandidatesForDuration.filter { call ->
                    Math.abs(call.duration - durationSec) <= durationTolerance
                }

                if (durationMatched.size == 1) {
                    val bestCall = durationMatched[0]
                    val recordingEntity = RecordingEntity(
                        id = existingRecording?.id ?: 0,
                        filePath = rec.filePath,
                        fileName = fileName,
                        fileSize = rec.fileSize,
                        duration = durationSec,
                        lastModified = rec.lastModified,
                        phoneExtracted = rec.phoneNumber,
                        contactExtracted = rec.contactName,
                        timestampExtracted = rec.timestamp,
                        matchedCallId = bestCall.id,
                        matchStatus = MatchStatus.MATCHED,
                        uploadStatus = UploadStatus.PENDING,
                        parser = rec.parserName,
                        reason = "Matched by duration difference within 5s"
                    )
                    recordingDao.insertRecording(recordingEntity)
                    callDao.updateCall(
                        bestCall.copy(
                            recordingPath = rec.filePath,
                            recordingLocalPath = rec.filePath,
                            recordingUploadStatus = "PENDING"
                        )
                    )
                    Log.i(TAG, "Matched by duration: ${rec.filePath} -> Call ID ${bestCall.id}")
                    enqueueUploadWork(bestCall.id)

                    recordingLogDao.insertLog(
                        RecordingLogEntity(
                            scanId = scanId,
                            fileName = fileName,
                            path = rec.filePath,
                            parser = rec.parserName,
                            phoneExtracted = rec.phoneNumber ?: rec.contactName,
                            timestampExtracted = rec.timestamp,
                            candidateCount = candidates.size,
                            matchedCallId = bestCall.id,
                            status = "MATCHED",
                            reason = null
                        )
                    )
                    return@forEach
                }

                // 6. If still tied -> Manual review
                val reason = "Tied between ${baseCandidatesForDuration.size} candidates after timestamp, phone and duration checks"
                val recordingEntity = RecordingEntity(
                    id = existingRecording?.id ?: 0,
                    filePath = rec.filePath,
                    fileName = fileName,
                    fileSize = rec.fileSize,
                    duration = durationSec,
                    lastModified = rec.lastModified,
                    phoneExtracted = rec.phoneNumber,
                    contactExtracted = rec.contactName,
                    timestampExtracted = rec.timestamp,
                    matchedCallId = null,
                    matchStatus = MatchStatus.UNMATCHED,
                    uploadStatus = UploadStatus.PENDING,
                    parser = rec.parserName,
                    reason = reason
                )
                recordingDao.insertRecording(recordingEntity)

                recordingLogDao.insertLog(
                    RecordingLogEntity(
                        scanId = scanId,
                        fileName = fileName,
                        path = rec.filePath,
                        parser = rec.parserName,
                        phoneExtracted = rec.phoneNumber ?: rec.contactName,
                        timestampExtracted = rec.timestamp,
                        candidateCount = candidates.size,
                        matchedCallId = null,
                        status = "UNMATCHED",
                        reason = reason
                    )
                )
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
                updateRecordingUploadStatus(callId, UploadStatus.UPLOADED, downloadUrl)
                Result.success(remotePath)
            } else {
                callDao.updateCall(call.copy(recordingUploadStatus = "FAILED"))
                updateRecordingUploadStatus(callId, UploadStatus.FAILED, reason = "Failed to update Firestore metadata")
                Result.failure(Exception("Failed to update Firestore metadata after upload"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading call recording $callId to GCS", e)
            callDao.updateCall(call.copy(recordingUploadStatus = "FAILED"))
            updateRecordingUploadStatus(callId, UploadStatus.FAILED, reason = e.message)
            Result.failure(e)
        }
    }

    override suspend fun updateRecordingUploadStatus(callId: Long, status: UploadStatus, cloudUrl: String?, reason: String?) {
        try {
            val list = recordingDao.getAllRecordings().filter { it.matchedCallId == callId }
            for (rec in list) {
                recordingDao.updateRecording(
                    rec.copy(
                        uploadStatus = status,
                        cloudUrl = cloudUrl ?: rec.cloudUrl,
                        reason = reason ?: rec.reason
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync recording upload status", e)
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
            
            // Sync recordings table
            val recList = recordingDao.getAllRecordings().filter { it.matchedCallId == callId }
            for (rec in recList) {
                recordingDao.updateRecording(
                    rec.copy(
                        uploadStatus = UploadStatus.PENDING,
                        cloudUrl = null
                    )
                )
            }
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

    override fun getAllRecordingsFlow(): Flow<List<RecordingEntity>> {
        return recordingDao.getAllRecordingsFlow()
    }

    override suspend fun getAllRecordings(): List<RecordingEntity> {
        return recordingDao.getAllRecordings()
    }

    override suspend fun getRecordingById(id: Long): RecordingEntity? {
        return recordingDao.getRecordingById(id)
    }

    override suspend fun clearAllRecordings() {
        recordingDao.clearAllRecordings()
    }

    override suspend fun manualMatchRecording(recordingId: Long, callId: Long) {
        withContext(Dispatchers.IO) {
            val recording = recordingDao.getRecordingById(recordingId)
            val call = callDao.getCallById(callId)
            if (recording != null && call != null) {
                recordingDao.updateRecording(
                    recording.copy(
                        matchedCallId = callId,
                        matchStatus = MatchStatus.MATCHED,
                        reason = "Manually matched to call"
                    )
                )
                callDao.updateCall(
                    call.copy(
                        recordingPath = recording.filePath,
                        recordingLocalPath = recording.filePath,
                        recordingUploadStatus = "PENDING"
                    )
                )
                Log.i(TAG, "Manually matched recording ${recording.filePath} to call ${call.number} ($callId)")
                enqueueUploadWork(callId)
            }
        }
    }

    override suspend fun uploadRecordingDirect(recordingId: Long): Result<String> = withContext(Dispatchers.IO) {
        val recording = recordingDao.getRecordingById(recordingId)
            ?: return@withContext Result.failure(Exception("Recording not found in database: $recordingId"))
        
        val callId = recording.matchedCallId
            ?: return@withContext Result.failure(Exception("Recording is not matched to any call: $recordingId"))
        
        recordingDao.updateRecording(recording.copy(uploadStatus = UploadStatus.PENDING))
        
        val result = uploadRecording(callId)
        if (result.isSuccess) {
            val updatedCall = callDao.getCallById(callId)
            recordingDao.updateRecording(
                recording.copy(
                    uploadStatus = UploadStatus.UPLOADED,
                    cloudUrl = updatedCall?.recordingUrl ?: result.getOrNull()
                )
            )
        } else {
            recordingDao.updateRecording(
                recording.copy(
                    uploadStatus = UploadStatus.FAILED,
                    reason = "Upload failed: ${result.exceptionOrNull()?.message}"
                )
            )
        }
        result
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
