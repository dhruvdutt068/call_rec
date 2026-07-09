package com.example.callog.data.repository

import android.util.Log
import com.example.callog.data.local.dao.CallDao
import com.example.callog.data.local.entity.CallEntity
import com.example.callog.data.provider.ContactDto
import com.example.callog.data.provider.ContactsProvider
import com.example.callog.data.remote.FirestoreService
import com.example.callog.domain.model.FirebaseConfig
import com.example.callog.domain.repository.FirestoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreRepositoryImpl @Inject constructor(
    private val callDao: CallDao,
    private val firestoreService: FirestoreService,
    private val contactsProvider: ContactsProvider
) : FirestoreRepository {

    private val TAG = "FirestoreRepository"

    companion object {
        internal fun normalizePhoneNumber(number: String): String {
            val clean = number.replace(Regex("[\\s\\-()+]"), "")
            if (clean.length >= 10) {
                return "91" + clean.takeLast(10)
            }
            return clean.ifEmpty { "unknown" }
        }

        internal fun getFirestoreCallType(localType: String): String {
            return when (localType.uppercase()) {
                "INCOMING" -> "incoming"
                "OUTGOING" -> "outgoing"
                "MISSED" -> "missed call"
                else -> "not connected"
            }
        }
    }

    override suspend fun uploadCallMetadata(call: CallEntity) {
        val contacts = contactsProvider.fetchContacts()
        val matchedContact = matchContact(call.number, contacts)
        val contactName = matchedContact?.name ?: call.name ?: ""

        val metadata = buildMetadataMap(call, contactName)
        val normalizedNumber = normalizePhoneNumber(call.number)
        val firestoreType = getFirestoreCallType(call.callType)

        val success = firestoreService.uploadCallMetadata(normalizedNumber, firestoreType, call.id.toString(), metadata)
        if (success) {
            callDao.updateSyncStatus(call.id, "SYNCED")
        } else {
            callDao.updateSyncStatus(call.id, "FAILED")
        }
    }

    override suspend fun updateCallMetadata(call: CallEntity) {
        val contacts = contactsProvider.fetchContacts()
        val matchedContact = matchContact(call.number, contacts)
        val contactName = matchedContact?.name ?: call.name ?: ""

        val metadata = buildMetadataMap(call, contactName)
        val normalizedNumber = normalizePhoneNumber(call.number)
        val firestoreType = getFirestoreCallType(call.callType)

        val success = firestoreService.updateCallMetadata(normalizedNumber, firestoreType, call.id.toString(), metadata)
        if (success) {
            callDao.updateSyncStatus(call.id, "SYNCED")
        } else {
            callDao.updateSyncStatus(call.id, "FAILED")
        }
    }

    override suspend fun checkIfExists(callId: Long): Boolean {
        val call = callDao.getCallById(callId) ?: return false
        val normalizedNumber = normalizePhoneNumber(call.number)
        val firestoreType = getFirestoreCallType(call.callType)
        return firestoreService.checkIfExists(normalizedNumber, firestoreType, callId.toString())
    }

    override suspend fun syncPendingCalls(): Unit = withContext(Dispatchers.IO) {
        try {
            val pendingCalls = callDao.getPendingCalls()
            if (pendingCalls.isEmpty()) {
                Log.d(TAG, "No pending calls to synchronize.")
                return@withContext
            }

            Log.i(TAG, "Found ${pendingCalls.size} pending calls to sync.")
            val contacts = contactsProvider.fetchContacts()

            for (call in pendingCalls) {
                // Set status to UPLOADING in Room
                callDao.updateSyncStatus(call.id, "UPLOADING")

                val matchedContact = matchContact(call.number, contacts)
                val contactName = matchedContact?.name ?: call.name ?: ""

                val normalizedNumber = normalizePhoneNumber(call.number)
                val firestoreType = getFirestoreCallType(call.callType)

                val exists = firestoreService.checkIfExists(normalizedNumber, firestoreType, call.id.toString())
                if (exists) {
                    val remote = firestoreService.getCallMetadata(normalizedNumber, firestoreType, call.id.toString())
                    if (remote != null) {
                        val remoteHasRecording = remote["hasRecording"] as? Boolean ?: false
                        val remoteContactName = remote["contactName"] as? String ?: ""
                        val remotePhoneNumber = remote["phoneNumber"] as? String ?: ""
                        val remoteCallType = remote["callType"] as? String ?: ""
                        val remoteDuration = (remote["duration"] as? Number)?.toInt() ?: 0
                        val remoteTimestamp = (remote["timestamp"] as? Number)?.toLong() ?: 0L
                        val remoteRecordingUrl = remote["recordingUrl"] as? String ?: ""

                        val localHasRecording = call.recordingPath != null
                        val localContactName = contactName

                        val hasChanged = remoteHasRecording != localHasRecording ||
                                remoteContactName != localContactName ||
                                remotePhoneNumber != call.number ||
                                remoteCallType != call.callType ||
                                remoteDuration != call.duration ||
                                remoteTimestamp != call.timestamp ||
                                remoteRecordingUrl != (call.recordingUrl ?: "")

                        if (hasChanged) {
                            Log.i(TAG, "Metadata for call ${call.id} changed. Updating Firestore.")
                            val updatedMetadata = buildMetadataMap(call, contactName).toMutableMap()
                            val remoteCreatedAt = remote["createdAt"] as? String
                            if (remoteCreatedAt != null) {
                                updatedMetadata["createdAt"] = remoteCreatedAt
                            }
                            val success = firestoreService.uploadCallMetadata(normalizedNumber, firestoreType, call.id.toString(), updatedMetadata)
                            if (success) {
                                callDao.updateSyncStatus(call.id, "SYNCED")
                            } else {
                                callDao.updateSyncStatus(call.id, "FAILED")
                            }
                        } else {
                            Log.i(TAG, "Metadata for call ${call.id} is already identical in Firestore. Skipping upload.")
                            callDao.updateSyncStatus(call.id, "SYNCED")
                        }
                    } else {
                        // Error fetching metadata, retry upload
                        val success = firestoreService.uploadCallMetadata(normalizedNumber, firestoreType, call.id.toString(), buildMetadataMap(call, contactName))
                        if (success) {
                            callDao.updateSyncStatus(call.id, "SYNCED")
                        } else {
                            callDao.updateSyncStatus(call.id, "FAILED")
                        }
                    }
                } else {
                    Log.i(TAG, "Uploading new metadata for call: ${call.id}")
                    val success = firestoreService.uploadCallMetadata(normalizedNumber, firestoreType, call.id.toString(), buildMetadataMap(call, contactName))
                    if (success) {
                        callDao.updateSyncStatus(call.id, "SYNCED")
                    } else {
                        callDao.updateSyncStatus(call.id, "FAILED")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in syncPendingCalls execution", e)
        }
    }

    override suspend fun resetAllSyncStatus() {
        callDao.resetAllSyncStatus()
    }

    private fun buildMetadataMap(call: CallEntity, contactName: String): Map<String, Any> {
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date())
        return mapOf(
            "callId" to call.id.toString(),
            "contactName" to contactName,
            "phoneNumber" to call.number,
            "callType" to call.callType,
            "duration" to call.duration.toLong(),
            "timestamp" to call.timestamp,
            "deviceModel" to android.os.Build.MODEL,
            "manufacturer" to android.os.Build.MANUFACTURER,
            "hasRecording" to (call.recordingPath != null),
            "recordingUrl" to (call.recordingUrl ?: ""),
            "syncStatus" to "SYNCED",
            "createdAt" to nowIso,
            "updatedAt" to nowIso
        )   
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

    override fun getFirebaseConfig(): FirebaseConfig? {
        return firestoreService.getSavedConfig()
    }

    override fun saveFirebaseConfig(config: FirebaseConfig?) {
        firestoreService.saveConfig(config)
    }

    override suspend fun testFirebaseConnection(config: FirebaseConfig?): Result<Unit> {
        return firestoreService.testFirebaseConnection(config)
    }

    override fun getLastUploadError(): StateFlow<String?> {
        return firestoreService.lastUploadError
    }

    override fun getDevicePhoneNumber(): String {
        return firestoreService.getDevicePhoneNumber()
    }

    override fun saveDevicePhoneNumber(number: String) {
        firestoreService.saveDevicePhoneNumber(number)
    }
}
