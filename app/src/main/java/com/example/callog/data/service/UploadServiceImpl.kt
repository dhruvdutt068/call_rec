
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
                    FirebaseApp.initializeApp(context)
                }
                FirebaseStorage.getInstance()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase Storage", e)
            null
        }
    }

    override fun uploadRecording(call: CallEntity): Flow<UploadProgressState> = callbackFlow {
        val localPath = call.recordingLocalPath ?: call.recordingPath
        if (localPath.isNullOrEmpty()) {
            trySend(UploadProgressState.Error(Exception("No local recording path found")))
            close()
            return@callbackFlow
        }
        val file = File(localPath)
        if (!file.exists()) {
            trySend(UploadProgressState.Error(Exception("Local recording file does not exist: $localPath")))
            close()
            return@callbackFlow
        }

        val storageInstance = getStorage()
        if (storageInstance == null) {
            trySend(UploadProgressState.Error(Exception("Firebase Storage not initialized")))
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

        Log.i(TAG, "Starting Cloud Storage upload to path: $remotePath")
        val ref = storageInstance.reference.child(remotePath)
        val fileUri = Uri.fromFile(file)
        
        // Add metadata to recording upload
        val metadataBuilder = com.google.firebase.storage.StorageMetadata.Builder()
            .setCustomMetadata("callId", call.id.toString())
            .setCustomMetadata("phoneNumber", call.number)
            .setCustomMetadata("timestamp", call.timestamp.toString())
            .build()

        val uploadTask = ref.putFile(fileUri, metadataBuilder)
        activeTasks[call.id] = uploadTask

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
                Log.i(TAG, "Cloud Storage upload successful: $remotePath")
                trySend(UploadProgressState.Success(downloadUrl, remotePath))
                close()
            } else {
                val exception = task.exception ?: Exception("Upload failed with unknown error")
                Log.e(TAG, "Cloud Storage upload failed for call ${call.id}", exception)
                trySend(UploadProgressState.Error(exception))
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
