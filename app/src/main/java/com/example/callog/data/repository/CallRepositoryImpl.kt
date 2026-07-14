package com.example.callog.data.repository

import android.content.Context
import android.util.Log
import com.example.callog.data.local.dao.CallDao
import com.example.callog.data.local.dao.ReminderDao
import com.example.callog.data.local.dao.ReminderWithCall
import com.example.callog.data.local.entity.CallEntity
import com.example.callog.data.local.entity.ReminderEntity
import com.example.callog.data.provider.CallLogProvider
import com.example.callog.data.provider.ContactDto
import com.example.callog.data.provider.ContactsProvider
import com.example.callog.data.provider.RecordingScanner
import com.example.callog.data.provider.SimManager
import com.example.callog.domain.model.CallLogEntry
import com.example.callog.domain.repository.CallRepository
import com.example.callog.domain.repository.FirestoreRepository
import com.example.callog.core.diagnostics.DeveloperLogger
import android.telephony.SubscriptionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class CallRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callDao: CallDao,
    private val reminderDao: ReminderDao,
    private val callLogProvider: CallLogProvider,
    private val contactsProvider: ContactsProvider,
    private val recordingScanner: RecordingScanner,
    private val simManager: SimManager,
    private val firestoreRepository: Provider<FirestoreRepository>,
    private val recordingRepository: Provider<com.example.callog.domain.repository.RecordingRepository>
) : CallRepository {

    override fun getCallLogsFlow(): Flow<List<CallLogEntry>> {
        val callsFlow = callDao.getAllCallsFlow()
        val contactsFlow = flow {
            emit(contactsProvider.fetchContacts())
        }.flowOn(Dispatchers.IO)

        return combine(callsFlow, contactsFlow) { calls, contacts ->
            val contactsMap = buildContactsMap(contacts)
            calls.map { call ->
                mapToCallLogEntry(call, contactsMap)
            }
        }.flowOn(Dispatchers.Default)
    }

    override fun getFavoriteLogsFlow(): Flow<List<CallLogEntry>> {
        val callsFlow = callDao.getFavoriteCallsFlow()
        val contactsFlow = flow {
            emit(contactsProvider.fetchContacts())
        }.flowOn(Dispatchers.IO)

        return combine(callsFlow, contactsFlow) { calls, contacts ->
            val contactsMap = buildContactsMap(contacts)
            calls.map { call ->
                mapToCallLogEntry(call, contactsMap)
            }
        }.flowOn(Dispatchers.Default)
    }

    override fun getRecordingLogsFlow(): Flow<List<CallLogEntry>> {
        val callsFlow = callDao.getCallsWithRecordingsFlow()
        val contactsFlow = flow {
            emit(contactsProvider.fetchContacts())
        }.flowOn(Dispatchers.IO)

        return combine(callsFlow, contactsFlow) { calls, contacts ->
            val contactsMap = buildContactsMap(contacts)
            calls.map { call ->
                mapToCallLogEntry(call, contactsMap)
            }
        }.flowOn(Dispatchers.Default)
    }

    override fun getCallLogByIdFlow(id: Long): Flow<CallLogEntry?> {
        val callFlow = callDao.getCallByIdFlow(id)
        val contactsFlow = flow {
            emit(contactsProvider.fetchContacts())
        }.flowOn(Dispatchers.IO)

        return combine(callFlow, contactsFlow) { call, contacts ->
            val contactsMap = buildContactsMap(contacts)
            call?.let { mapToCallLogEntry(it, contactsMap) }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun syncCallLogs() { withContext(Dispatchers.IO) {
        try {
            // Check SIM configuration states
            if (simManager.isSyncSuspendedDueToSimChange()) {
                DeveloperLogger.warning("SYNC_SUSPENDED", "Sync is suspended because SIM card configuration mismatch. Please reconfigure.")
                return@withContext
            }

            val activeSims = simManager.getActiveSims()
            if (activeSims.isEmpty() && simManager.hasPermission()) {
                DeveloperLogger.warning("SYNC_SUSPENDED", "No active SIM card found on the device. Sync suspended.")
                return@withContext
            }

            val systemCalls = callLogProvider.fetchCallLogs()
            if (systemCalls.isNotEmpty()) {
                val existingCalls = callDao.getAllCalls()
                val existingKeys = existingCalls.map { "${it.timestamp}_${it.number}" }.toSet()
                
                val selectedSubId = simManager.getSelectedSubscriptionId()
                if (selectedSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    DeveloperLogger.warning("SYNC_SUSPENDED", "No Business SIM card configured. Sync suspended.")
                    return@withContext
                }

                val newCalls = systemCalls.filter { call ->
                    val key = "${call.timestamp}_${call.number}"
                    if (existingKeys.contains(key)) {
                        return@filter false
                    }

                    // Perform strict SIM filtering (only allow selected SIM calls)
                    val callSubId = simManager.getSubscriptionIdFromHandle(call.phoneAccountId, call.phoneAccountComponentName)
                    if (callSubId == selectedSubId) {
                        DeveloperLogger.info(
                            "SIM_FILTER_MATCH",
                            "Call from ${call.number} matched Business SIM ($selectedSubId). Importing."
                        )
                        true
                    } else if (callSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        // Fallback: If platform doesn't associate SIM info with the call log, import it to prevent losing calls, but warn
                        DeveloperLogger.warning(
                            "SIM_FILTER_UNKNOWN",
                            "Call from ${call.number} has unknown SIM origin. Importing call to prevent data loss."
                        )
                        true
                    } else {
                        // Skip calls that explicitly belong to a different SIM
                        DeveloperLogger.info(
                            "SIM_FILTER_MISMATCH",
                            "Call from ${call.number} came from SIM $callSubId. Business SIM is $selectedSubId. Skipping."
                        )
                        false
                    }
                }

                if (newCalls.isNotEmpty()) {
                    callDao.insertCalls(newCalls)
                }
            }

            // Scan, match, and upload recordings to GCS
            recordingRepository.get().scanRecordings()

            // Trigger incremental synchronization of all pending call metadata to Firestore
            firestoreRepository.get().syncPendingCalls()
        } catch (e: Exception) {
            Log.e("CallRepositoryImpl", "Error syncing call logs", e)
        }
        }
    }

    override suspend fun getContacts(): List<ContactDto> = withContext(Dispatchers.IO) {
        return@withContext contactsProvider.fetchContacts()
    }

    override suspend fun getAllCallsSnapshot(): List<CallEntity> = withContext(Dispatchers.IO) {
        return@withContext callDao.getAllCalls()
    }

    override suspend fun updateNotes(callId: Long, notes: String?) {
        withContext(Dispatchers.IO) {
            callDao.getCallById(callId)?.let { call ->
                callDao.updateCall(call.copy(notes = notes))
            }
        }
    }

    override suspend fun updateTags(callId: Long, tags: List<String>) {
        withContext(Dispatchers.IO) {
            callDao.getCallById(callId)?.let { call ->
                val tagsString = if (tags.isEmpty()) null else tags.joinToString(",")
                callDao.updateCall(call.copy(tags = tagsString))
            }
        }
    }

    override suspend fun toggleFavorite(callId: Long) {
        withContext(Dispatchers.IO) {
            callDao.getCallById(callId)?.let { call ->
                callDao.updateCall(call.copy(isFavorite = !call.isFavorite))
            }
        }
    }

    override suspend fun deleteCall(callId: Long) {
        withContext(Dispatchers.IO) {
            callDao.getCallById(callId)?.let { call ->
                callDao.deleteCall(call)
            }
        }
    }

    override suspend fun clearAllData() {
        withContext(Dispatchers.IO) {
            callDao.deleteAllCalls()
        }
    }

    // Reminders
    override fun getPendingRemindersFlow(): Flow<List<ReminderWithCall>> {
        return reminderDao.getPendingRemindersFlow()
    }

    override suspend fun addReminder(reminder: ReminderEntity): Long = withContext(Dispatchers.IO) {
        return@withContext reminderDao.insertReminder(reminder)
    }

    override suspend fun completeReminder(reminderId: Long) {
        withContext(Dispatchers.IO) {
            reminderDao.getReminderById(reminderId)?.let { reminder ->
                reminderDao.updateReminder(reminder.copy(isCompleted = true))
            }
        }
    }

    override suspend fun deleteReminder(reminderId: Long) {
        withContext(Dispatchers.IO) {
            reminderDao.getReminderById(reminderId)?.let { reminder ->
                reminderDao.deleteReminder(reminder)
            }
        }
    }



    private fun buildContactsMap(contacts: List<ContactDto>): Map<String, ContactDto> {
        val contactsMap = mutableMapOf<String, ContactDto>()
        contacts.forEach { contact ->
            contact.phoneNumbers.forEach { phone ->
                val clean = phone.filter { it.isDigit() }
                if (clean.length >= 7) {
                    contactsMap[clean.takeLast(7)] = contact
                } else if (clean.isNotEmpty()) {
                    contactsMap[clean] = contact
                }
            }
        }
        return contactsMap
    }

    private fun mapToCallLogEntry(call: CallEntity, contactsMap: Map<String, ContactDto>): CallLogEntry {
        val cleanNumber = call.number.filter { it.isDigit() }
        val matchedContact = if (cleanNumber.length >= 7) {
            contactsMap[cleanNumber.takeLast(7)]
        } else {
            contactsMap[cleanNumber]
        }
        val tagsList = call.tags?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        
        return CallLogEntry(
            id = call.id,
            name = matchedContact?.name ?: call.name,
            number = call.number,
            duration = call.duration,
            timestamp = call.timestamp,
            callType = call.callType,
            recordingPath = call.recordingPath,
            isFavorite = call.isFavorite,
            notes = call.notes,
            tags = tagsList,
            contactPhotoUri = matchedContact?.photoUri,
            contactEmails = matchedContact?.emails ?: emptyList(),
            contactAllNumbers = matchedContact?.phoneNumbers ?: emptyList(),
            isContactFavorite = matchedContact?.isFavorite ?: false,
            syncStatus = call.syncStatus,
            recordingLocalPath = call.recordingLocalPath,
            recordingCloudPath = call.recordingCloudPath,
            recordingUploadStatus = call.recordingUploadStatus,
            recordingUploadedAt = call.recordingUploadedAt,
            retryCount = call.retryCount,
            uploadedAt = call.uploadedAt,
            syncError = call.syncError,
            lastAttempt = call.lastAttempt
        )
    }


}
