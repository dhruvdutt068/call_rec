
package com.example.callog.data.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.callog.data.local.entity.CallEntity
import com.example.callog.data.provider.ContactDto
import com.example.callog.data.provider.ContactsProvider
import com.example.callog.data.remote.FirestoreService
import com.example.callog.data.repository.FirestoreRepositoryImpl
import com.example.callog.domain.service.UploadProgressState
import com.example.callog.domain.service.UploadService
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.UploadTask
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.example.callog.data.local.dao.TracebackDao
import com.example.callog.data.local.entity.TracebackEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.domain.service.StorageErrorCategory
import com.example.callog.domain.service.StorageUploadException
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

@Singleton
class UploadServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestoreService: FirestoreService,
    private val contactsProvider: ContactsProvider,
    private val tracebackDao: TracebackDao
) : UploadService {

    private val TAG = "UploadServiceImpl"
    private val activeTasks = ConcurrentHashMap<Long, UploadTask>()

    private fun getStorage(): FirebaseStorage? {
        return firestoreService.getStorageInstance()
    }

    private fun buildStorageUploadException(
        raw: Throwable,
        remotePath: String,
        bucketName: String,
        hasAuth: Boolean,
        callId: Long
    ): StorageUploadException {
        val category = StorageErrorCategory.fromException(raw)
        val storageEx = raw as? com.google.firebase.storage.StorageException
        val errorCode = storageEx?.errorCode
        val httpResultCode = storageEx?.httpResultCode

        val diagnosticMsg = "Firebase Storage upload error [$category] for call $callId: HTTP $httpResultCode, ErrorCode $errorCode, Path: $remotePath, Bucket: $bucketName, Authenticated: $hasAuth - ${raw.message}"
        Log.e(TAG, diagnosticMsg, raw)

        // Persist structured failure information through DeveloperLogger (sync_logs) and TracebackDao
        DeveloperLogger.error(
            "STORAGE_UPLOAD_${category.name}",
            diagnosticMsg,
            exception = raw
        )

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val ownerPhone = firestoreService.getDevicePhoneNumber()
                val ownerName = firestoreService.getDeviceOwnerName()
                tracebackDao.insertTraceback(
                    TracebackEntity(
                        id = 0,
                        ownerPhone = ownerPhone,
                        ownerName = ownerName,
                        callerType = "STORAGE_ERROR",
                        phoneNumber = "",
                        callerName = "Category: ${category.name}, HTTP: $httpResultCode, Code: $errorCode",
                        hasRecording = true,
                        callLogId = callId,
                        recordingUrl = "Bucket: $bucketName, Path: $remotePath",
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (tbEx: Exception) {
                Log.w(TAG, "Failed to insert error traceback", tbEx)
            }
        }

        return StorageUploadException(
            category = category,
            errorCode = errorCode,
            httpResultCode = httpResultCode,
            uploadPath = remotePath,
            bucketName = bucketName,
            hasFirebaseAuth = hasAuth,
            message = diagnosticMsg,
            cause = raw
        )
    }

    override fun uploadRecording(call: CallEntity): Flow<UploadProgressState> = callbackFlow {
        val localPath = call.recordingLocalPath ?: call.recordingPath
        if (localPath.isNullOrEmpty()) {
            val ex = StorageUploadException(
                category = StorageErrorCategory.UNKNOWN_ERROR,
                message = "No local recording path found for call ${call.id}"
            )
            trySend(UploadProgressState.Error(ex))
            close()
            return@callbackFlow
        }
        val file = File(localPath)
        if (!file.exists()) {
            val ex = StorageUploadException(
                category = StorageErrorCategory.UNKNOWN_ERROR,
                message = "Local recording file does not exist: $localPath"
            )
            trySend(UploadProgressState.Error(ex))
            close()
            return@callbackFlow
        }

        val storageInstance = getStorage()
        if (storageInstance == null) {
            val ex = StorageUploadException(
                category = StorageErrorCategory.UNKNOWN_ERROR,
                message = "Firebase Storage not initialized"
            )
            trySend(UploadProgressState.Error(ex))
            close()
            return@callbackFlow
        }

        // Check if recording already exists in Firestore metadata to avoid duplicate upload
        val devicePhone = firestoreService.getDevicePhoneNumber().ifEmpty { "unknown_device" }
        val normalizedDevicePhone = FirestoreRepositoryImpl.normalizePhoneNumber(devicePhone)
        val normalizedNumber = FirestoreRepositoryImpl.normalizePhoneNumber(call.number)
        val firestoreType = FirestoreRepositoryImpl.getFirestoreCallType(call.callType)
        
        try {
            val remoteMetadata = firestoreService.getCallMetadata(normalizedNumber, firestoreType, call.id.toString())
            if (remoteMetadata != null) {
                val hasRec = remoteMetadata["hasRecording"] as? Boolean ?: false
                val remoteRecUrl = remoteMetadata["recordingUrl"] as? String ?: ""
                val remoteRecPath = remoteMetadata["recordingPath"] as? String ?: ""
                
                if (hasRec && remoteRecUrl.isNotEmpty() && remoteRecPath.isNotEmpty()) {
                    Log.i(TAG, "Recording already exists in Firestore for call ${call.id}. Skipping upload and reusing URL: $remoteRecUrl")
                    trySend(UploadProgressState.Success(remoteRecUrl, remoteRecPath))
                    close()
                    return@callbackFlow
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking remote metadata in uploadRecording", e)
        }

        val date = Date(call.timestamp)
        val year = SimpleDateFormat("yyyy", Locale.US).format(date)
        val month = SimpleDateFormat("MM", Locale.US).format(date)
        val day = SimpleDateFormat("dd", Locale.US).format(date)

        val extension = file.extension.ifEmpty { "mp3" }
        val remotePath = "users/$normalizedDevicePhone/recordings/$year/$month/$day/call_${call.id}.$extension"

        // Inspect and ensure Firebase Authentication state before upload
        var hasAuth = false
        try {
            val app = storageInstance.app
            val auth = FirebaseAuth.getInstance(app)
            val initialUser = auth.currentUser
            Log.i(TAG, "FirebaseAuth currentUser presence before upload: ${initialUser != null} (uid: ${initialUser?.uid})")
            if (initialUser == null) {
                Log.i(TAG, "No Firebase user authenticated. Attempting anonymous authentication...")
                try {
                    val authResult = auth.signInAnonymously().await()
                    val user = authResult.user
                    Log.i(TAG, "Firebase anonymous authentication completed. User present: ${user != null} (uid: ${user?.uid})")
                    hasAuth = (user != null)
                } catch (authEx: Exception) {
                    Log.w(TAG, "Firebase anonymous authentication failed: ${authEx.message}. Proceeding with unauthenticated request.", authEx)
                    hasAuth = false
                }
            } else {
                hasAuth = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed checking Firebase auth state: ${e.message}", e)
            hasAuth = false
        }

        val bucketName = try { storageInstance.reference.bucket } catch (e: Exception) { "unknown" }
        Log.i(TAG, "Starting Cloud Storage upload to bucket: $bucketName, path: $remotePath, hasAuth: $hasAuth")

        val ref = storageInstance.reference.child(remotePath)
        val fileUri = Uri.fromFile(file)
        
        // Add metadata to recording upload
        val metadataBuilder = com.google.firebase.storage.StorageMetadata.Builder()
            .setCustomMetadata("callId", call.id.toString())
            .setCustomMetadata("phoneNumber", call.number)
            .setCustomMetadata("timestamp", call.timestamp.toString())
            .build()

        val uploadTask: UploadTask
        try {
            uploadTask = ref.putFile(fileUri, metadataBuilder)
            activeTasks[call.id] = uploadTask
        } catch (e: Throwable) {
            val storageEx = buildStorageUploadException(e, remotePath, bucketName, hasAuth, call.id)
            trySend(UploadProgressState.Error(storageEx))
            close()
            return@callbackFlow
        }

        val progressListener = { snapshot: UploadTask.TaskSnapshot ->
            trySend(UploadProgressState.Progress(snapshot.bytesTransferred, snapshot.totalByteCount))
            Unit
        }

        uploadTask.addOnProgressListener(progressListener)
        
        uploadTask.continueWithTask { task ->
            if (!task.isSuccessful) {
                task.exception?.let { throw it }
            }
            ref.downloadUrl
        }.addOnCompleteListener { task ->
            activeTasks.remove(call.id)
            if (task.isSuccessful) {
                val downloadUrl = task.result.toString()
                Log.i(TAG, "Cloud Storage upload successful: $remotePath (bucket: $bucketName)")
                trySend(UploadProgressState.Success(downloadUrl, remotePath))
                close()
            } else {
                val rawException = task.exception ?: Exception("Upload failed with unknown error")
                val storageEx = buildStorageUploadException(rawException, remotePath, bucketName, hasAuth, call.id)
                trySend(UploadProgressState.Error(storageEx))
                close()
            }
        }

        awaitClose {
            // Task is managed externally via cancelUpload
        }
    }

    override suspend fun uploadMetadata(call: CallEntity, recordingUrl: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val contacts = contactsProvider.fetchContacts()
            val matchedContact = matchContact(call.number, contacts)
            val contactName = matchedContact?.name ?: call.name ?: ""

            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date())
            val callTimeIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date(call.timestamp))
            val hasRec = call.recordingPath != null || !recordingUrl.isNullOrEmpty()
            val recUrl = recordingUrl ?: call.recordingUrl ?: ""

            val metadata = mutableMapOf<String, Any>(
                "callId" to call.id.toString(),
                "contactName" to contactName,
                "phoneNumber" to call.number,
                "callType" to call.callType,
                "duration" to call.duration.toLong(),
                "timestamp" to call.timestamp,
                "deviceModel" to android.os.Build.MODEL,
                "manufacturer" to android.os.Build.MANUFACTURER,
                "hasRecording" to hasRec,
                "recordingUrl" to recUrl,
                "syncStatus" to "SYNCED",
                "createdAt" to nowIso,
                "updatedAt" to nowIso
            )

            if (call.callType.equals("OUTGOING", ignoreCase = true)) {
                metadata["callMadeAt"] = callTimeIso
            } else {
                metadata["callReceivedAt"] = callTimeIso
            }

            if (hasRec) {
                val date = Date(call.timestamp)
                val year = SimpleDateFormat("yyyy", Locale.US).format(date)
                val month = SimpleDateFormat("MM", Locale.US).format(date)
                val day = SimpleDateFormat("dd", Locale.US).format(date)
                val devicePhone = firestoreService.getDevicePhoneNumber().ifEmpty { "unknown_device" }
                val normalizedDevicePhone = FirestoreRepositoryImpl.normalizePhoneNumber(devicePhone)
                
                val localPath = call.recordingLocalPath ?: call.recordingPath ?: ""
                val extension = if (localPath.isNotEmpty()) File(localPath).extension.ifEmpty { "mp3" } else "mp3"
                val remotePath = "users/$normalizedDevicePhone/recordings/$year/$month/$day/call_${call.id}.$extension"
                
                metadata["recordingPath"] = remotePath
                metadata["uploadedAt"] = nowIso
            }

            val normalizedNumber = FirestoreRepositoryImpl.normalizePhoneNumber(call.number)
            val firestoreType = FirestoreRepositoryImpl.getFirestoreCallType(call.callType)

            val exists = firestoreService.checkIfExists(normalizedNumber, firestoreType, call.id.toString())
            val success = if (exists) {
                val remote = firestoreService.getCallMetadata(normalizedNumber, firestoreType, call.id.toString())
                if (remote != null) {
                    val remoteCreatedAt = remote["createdAt"] as? String
                    if (remoteCreatedAt != null) {
                        metadata["createdAt"] = remoteCreatedAt
                    }
                }
                firestoreService.updateCallMetadata(normalizedNumber, firestoreType, call.id.toString(), metadata)
            } else {
                firestoreService.uploadCallMetadata(normalizedNumber, firestoreType, call.id.toString(), metadata)
            }

            if (success) {
                insertTraceback(call, contactName, recUrl)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Firestore write metadata failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun cancelUpload(callId: Long) {
        activeTasks[callId]?.cancel()
        activeTasks.remove(callId)
    }

    private fun matchContact(callNumber: String, contacts: List<ContactDto>): ContactDto? {
        val cleanCallNum = callNumber.replace(Regex("[^0-9]"), "")
        if (cleanCallNum.isEmpty()) return null
        
        return contacts.firstOrNull { contact ->
            contact.phoneNumbers.any { phone ->
                val cleanPhone = phone.replace(Regex("[^0-9]"), "")
                cleanPhone.isNotEmpty() && (
                    cleanPhone == cleanCallNum ||
                    (cleanCallNum.length >= 7 && cleanPhone.endsWith(cleanCallNum.takeLast(7))) ||
                    (cleanPhone.length >= 7 && cleanCallNum.endsWith(cleanPhone.takeLast(7)))
                )
            }
        }
    }

    private suspend fun insertTraceback(call: CallEntity, contactName: String, recordingUrl: String) {
        try {
            val ownerPhone = firestoreService.getDevicePhoneNumber()
            val ownerName = firestoreService.getDeviceOwnerName()
            val traceback = TracebackEntity(
                ownerPhone = ownerPhone,
                ownerName = ownerName,
                callerType = call.callType,
                phoneNumber = call.number,
                callerName = contactName,
                hasRecording = (call.recordingPath != null || recordingUrl.isNotEmpty()),
                callLogId = call.id,
                recordingUrl = recordingUrl
            )
            tracebackDao.insertTraceback(traceback)
            Log.d(TAG, "Successfully logged traceback entry for call ${call.id} in SQL database.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log traceback for call ${call.id}", e)
        }
    }
}
