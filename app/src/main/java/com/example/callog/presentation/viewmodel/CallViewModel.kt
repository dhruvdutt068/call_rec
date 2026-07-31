package com.example.callog.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callog.data.local.dao.ReminderWithCall
import com.example.callog.data.local.dao.SalesCallDao
import com.example.callog.data.local.entity.ReminderEntity
import com.example.callog.data.local.entity.SalesCallEntity
import com.example.callog.data.provider.ContactDto
import com.example.callog.domain.model.CallLogEntry
import com.example.callog.domain.model.FirebaseConfig
import com.example.callog.domain.repository.CallRepository
import com.example.callog.domain.repository.FirestoreRepository
import com.example.callog.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.example.callog.data.local.entity.RecordingEntity
import com.example.callog.data.local.entity.RecordingLogEntity
import com.example.callog.data.local.dao.RecordingLogDao
import com.example.callog.data.local.entity.SyncLogEntity
import com.example.callog.data.local.dao.SyncLogDao
import com.example.callog.domain.service.SyncManager
import com.example.callog.core.diagnostics.DeveloperLogger

@HiltViewModel
class CallViewModel @Inject constructor(
    private val getCallLogsUseCase: GetCallLogsUseCase,
    private val syncCallLogsUseCase: SyncCallLogsUseCase,
    private val syncPendingCallsUseCase: SyncPendingCallsUseCase,
    private val updateCallNotesUseCase: UpdateCallNotesUseCase,
    private val updateCallTagsUseCase: UpdateCallTagsUseCase,
    private val toggleCallFavoriteUseCase: ToggleCallFavoriteUseCase,
    private val deleteCallUseCase: DeleteCallUseCase,
    private val getPendingRemindersUseCase: GetPendingRemindersUseCase,
    private val addReminderUseCase: AddReminderUseCase,
    private val completeReminderUseCase: CompleteReminderUseCase,
    private val deleteReminderUseCase: DeleteReminderUseCase,
    private val clearAllDataUseCase: ClearAllDataUseCase,
    private val repository: CallRepository, // For direct contacts lookup
    private val firestoreRepository: FirestoreRepository,
    private val recordingRepository: com.example.callog.domain.repository.RecordingRepository,
    private val syncManager: SyncManager,
    private val salesCallDao: SalesCallDao,
    private val syncLogDao: SyncLogDao,
    private val recordingLogDao: RecordingLogDao,
    private val simManager: com.example.callog.data.provider.SimManager
) : ViewModel() {

    val recordingLogs: StateFlow<List<RecordingLogEntity>> = recordingLogDao.getAllLogsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recordingsFlow: StateFlow<List<RecordingEntity>> = recordingRepository.getAllRecordingsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncLogs: StateFlow<List<SyncLogEntity>> = syncLogDao.getAllLogsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSalesCalls: StateFlow<List<SalesCallEntity>> = salesCallDao.getAllSalesCalls()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearSyncLogs() {
        viewModelScope.launch {
            syncLogDao.clearLogs()
        }
    }

    fun forceLogsCleanup() {
        viewModelScope.launch {
            val cutoff = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            syncLogDao.deleteLogsOlderThan(cutoff)
            syncLogDao.trimLogs(5000)
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _callTypeFilter = MutableStateFlow<String?>("ALL") // "ALL", "INCOMING", "OUTGOING", "MISSED", "REJECTED", "RECORDED"
    val callTypeFilter = _callTypeFilter.asStateFlow()

    private val _sortBy = MutableStateFlow("NEWEST") // "NEWEST", "OLDEST", "LONGEST", "SHORTEST"
    val sortBy = _sortBy.asStateFlow()

    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val syncProgress: StateFlow<String?> = syncManager.syncProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _contacts = MutableStateFlow<List<ContactDto>>(emptyList())
    val contacts = _contacts.asStateFlow()

    private val _firebaseConfig = MutableStateFlow<FirebaseConfig?>(null)
    val firebaseConfig = _firebaseConfig.asStateFlow()

    private val _devicePhoneNumber = MutableStateFlow("")
    val devicePhoneNumber = _devicePhoneNumber.asStateFlow()

    private val _deviceOwnerName = MutableStateFlow("")
    val deviceOwnerName = _deviceOwnerName.asStateFlow()

    private val _customRecordingPath = MutableStateFlow("")
    val customRecordingPath = _customRecordingPath.asStateFlow()

    private val _isDeveloperModeActive = MutableStateFlow(DeveloperLogger.isDeveloperModeEnabled)
    val isDeveloperModeActive = _isDeveloperModeActive.asStateFlow()

    fun setDeveloperModeActive(active: Boolean) {
        DeveloperLogger.isDeveloperModeEnabled = active
        _isDeveloperModeActive.value = active
    }

    private val _connectionStatus = MutableStateFlow<String?>(null) // null/idle, "TESTING", "SUCCESS", "FAILED:<error>"
    val connectionStatus = _connectionStatus.asStateFlow()

    val lastUploadError: StateFlow<String?> = firestoreRepository.getLastUploadError()

    // Combined reactive flow of calls matching current search, filters, and sort configurations
    @OptIn(ExperimentalCoroutinesApi::class)
    val callLogs: StateFlow<List<CallLogEntry>> = combine(
        _searchQuery,
        _callTypeFilter,
        _sortBy
    ) { query, type, sort ->
        val apiType = if (type == "ALL") null else type
        Triple(query, apiType, sort)
    }.flatMapLatest { (query, type, sort) ->
        getCallLogsUseCase(
            searchQuery = query,
            callTypeFilter = type,
            favoriteFilter = false,
            sortBy = sort
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Favorites logs
    val favoriteLogs: StateFlow<List<CallLogEntry>> = getCallLogsUseCase(favoriteFilter = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Upcoming / pending reminders
    val pendingReminders: StateFlow<List<ReminderWithCall>> = getPendingRemindersUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // SIM State Flows
    private val _activeSims = MutableStateFlow<List<com.example.callog.data.provider.SimInfo>>(emptyList())
    val activeSims = _activeSims.asStateFlow()

    private val _selectedSimId = MutableStateFlow(android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID)
    val selectedSimId = _selectedSimId.asStateFlow()

    private val _selectedSimSlot = MutableStateFlow(-1)
    val selectedSimSlot = _selectedSimSlot.asStateFlow()

    private val _selectedSimCarrier = MutableStateFlow("")
    val selectedSimCarrier = _selectedSimCarrier.asStateFlow()

    private val _selectedSimDisplayName = MutableStateFlow("")
    val selectedSimDisplayName = _selectedSimDisplayName.asStateFlow()

    private val _selectedSimPhoneNumber = MutableStateFlow("")
    val selectedSimPhoneNumber = _selectedSimPhoneNumber.asStateFlow()

    private val _detectionMethod = MutableStateFlow("MANUAL")
    val detectionMethod = _detectionMethod.asStateFlow()

    private val _isSimChangeRequired = MutableStateFlow(false)
    val isSimChangeRequired = _isSimChangeRequired.asStateFlow()

    private val _isMappingSupported = MutableStateFlow(false)
    val isMappingSupported = _isMappingSupported.asStateFlow()

    init {
        loadContacts()
        loadFirebaseConfig()
        syncLogs()
        populateSalesCalls()
        loadSimConfigurations()
    }

    fun loadSimConfigurations() {
        viewModelScope.launch {
            if (simManager.hasPermission()) {
                simManager.checkSimChanges()
                simManager.checkPhoneAccountMappingSupport()
                
                _activeSims.value = simManager.getActiveSims()
                _selectedSimId.value = simManager.getSelectedSubscriptionId()
                _selectedSimSlot.value = simManager.getSelectedSlotIndex()
                _selectedSimCarrier.value = simManager.getSelectedCarrierName()
                _selectedSimDisplayName.value = simManager.getSelectedDisplayName()
                _selectedSimPhoneNumber.value = simManager.getSelectedPhoneNumber()
                _detectionMethod.value = simManager.getDetectionMethod()
                _isMappingSupported.value = simManager.isMappingSupported()

                val hasSims = _activeSims.value.isNotEmpty()
                val isNotConfigured = _selectedSimId.value == android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID
                val isSuspended = simManager.isSyncSuspendedDueToSimChange()
                
                // The setup wizard is mandatory if there is any SIM card present and it is not configured yet
                _isSimChangeRequired.value = isSuspended || (hasSims && isNotConfigured)
            } else {
                _isSimChangeRequired.value = false
            }
        }
    }

    fun saveBusinessSim(sim: com.example.callog.data.provider.SimInfo) {
        simManager.saveSelectedSim(sim)
        _selectedSimId.value = sim.subscriptionId
        _selectedSimSlot.value = sim.slotIndex
        _selectedSimCarrier.value = sim.carrierName
        _selectedSimDisplayName.value = sim.displayName
        _selectedSimPhoneNumber.value = sim.phoneNumber
        _isSimChangeRequired.value = false
        // Trigger immediate sync
        syncLogs()
    }

    fun saveSyncAll() {
        simManager.setDetectionMethod("SYNC_ALL")
        simManager.clearSimChangeSuspension()
        _detectionMethod.value = "SYNC_ALL"
        _isSimChangeRequired.value = false
        syncLogs()
    }

    fun saveDetectionMethod(method: String) {
        simManager.setDetectionMethod(method)
        _detectionMethod.value = method
        loadSimConfigurations()
    }

    fun dismissSimChangeRequired() {
        simManager.clearSimChangeSuspension()
        _isSimChangeRequired.value = false
    }

    fun triggerSimCapabilityCheck() {
        viewModelScope.launch {
            val supported = simManager.checkPhoneAccountMappingSupport()
            _isMappingSupported.value = supported
        }
    }

    private fun loadFirebaseConfig() {
        _firebaseConfig.value = firestoreRepository.getFirebaseConfig()
        _devicePhoneNumber.value = firestoreRepository.getDevicePhoneNumber()
        _deviceOwnerName.value = firestoreRepository.getDeviceOwnerName()
        _customRecordingPath.value = firestoreRepository.getCustomRecordingPath()
    }

    /**
     * Populates the sales_calls table from the existing local calls DB.
     * Runs immediately on ViewModel init — no network required.
     * Only inserts rows that don't already exist (checked by callId uniqueness).
     */
    fun populateSalesCalls() {
        viewModelScope.launch {
            val salespersonPhoneRaw = firestoreRepository.getDevicePhoneNumber()
            val salespersonName  = firestoreRepository.getDeviceOwnerName()
            if (salespersonPhoneRaw.isEmpty()) return@launch  // device not configured yet

            val salespersonPhone = com.example.callog.data.repository.FirestoreRepositoryImpl.normalizePhoneNumber(salespersonPhoneRaw)
            val allCalls = repository.getAllCallsSnapshot()
            for (call in allCalls) {
                val existing = salesCallDao.getSalesCallByCallId(call.id)
                if (existing == null) {
                    salesCallDao.insertSalesCall(
                        SalesCallEntity(
                            salespersonPhone = salespersonPhone,
                            salespersonName  = salespersonName,
                            buyerPhone       = call.number,
                            buyerName        = call.name,
                            callType         = call.callType,
                            callId           = call.id,
                            duration         = call.duration,
                            createdAt        = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(call.timestamp))
                        )
                    )
                }
            }
        }
    }

    fun saveFirebaseConfig(config: FirebaseConfig?) {
        firestoreRepository.saveFirebaseConfig(config)
        _firebaseConfig.value = config
        _connectionStatus.value = null
    }

    fun saveDevicePhoneNumber(number: String) {
        firestoreRepository.saveDevicePhoneNumber(number)
        _devicePhoneNumber.value = number
        populateSalesCalls()  // backfill existing calls now that device is identified
    }

    fun saveDeviceOwnerName(name: String) {
        firestoreRepository.saveDeviceOwnerName(name)
        _deviceOwnerName.value = name
    }

    fun saveCustomRecordingPath(path: String) {
        firestoreRepository.saveCustomRecordingPath(path)
        _customRecordingPath.value = path
    }

    fun testAndSaveFirebaseConfig(config: FirebaseConfig) {
        viewModelScope.launch {
            _connectionStatus.value = "TESTING"
            val result = firestoreRepository.testFirebaseConnection(config)
            if (result.isSuccess) {
                firestoreRepository.saveFirebaseConfig(config)
                _firebaseConfig.value = config
                _connectionStatus.value = "SUCCESS"
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                _connectionStatus.value = "FAILED:$errorMsg"
            }
        }
    }

    fun testDefaultConnection() {
        viewModelScope.launch {
            _connectionStatus.value = "TESTING"
            val result = firestoreRepository.testFirebaseConnection(null)
            if (result.isSuccess) {
                _connectionStatus.value = "SUCCESS"
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                _connectionStatus.value = "FAILED:$errorMsg"
            }
        }
    }

    fun syncLogs() {
        viewModelScope.launch {
            loadContacts()
            syncManager.startSync()
        }
    }

    fun forceReSync() {
        viewModelScope.launch {
            loadContacts()
            salesCallDao.resetSyncStatus()
            syncManager.forceResetAndSync()
        }
    }

    private fun loadContacts() {
        viewModelScope.launch {
            _contacts.value = repository.getContacts()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCallTypeFilter(filter: String) {
        _callTypeFilter.value = filter
    }

    fun setSortBy(sort: String) {
        _sortBy.value = sort
    }

    // Call Intelligence Actions
    fun updateNotes(callId: Long, notes: String?) {
        viewModelScope.launch {
            updateCallNotesUseCase(callId, notes)
        }
    }

    fun updateTags(callId: Long, tags: List<String>) {
        viewModelScope.launch {
            updateCallTagsUseCase(callId, tags)
        }
    }

    fun toggleFavorite(callId: Long) {
        viewModelScope.launch {
            toggleCallFavoriteUseCase(callId)
        }
    }

    fun deleteCall(callId: Long) {
        viewModelScope.launch {
            deleteCallUseCase(callId)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            clearAllDataUseCase()
            syncLogs()
        }
    }

    fun getCallLogByIdFlow(id: Long): Flow<CallLogEntry?> {
        return repository.getCallLogByIdFlow(id)
    }

    // Reminders
    fun scheduleReminder(callId: Long, reminderTime: Long, notes: String?) {
        viewModelScope.launch {
            addReminderUseCase(
                ReminderEntity(
                    callId = callId,
                    reminderTime = reminderTime,
                    notes = notes
                )
            )
        }
    }

    fun markReminderCompleted(reminderId: Long) {
        viewModelScope.launch {
            completeReminderUseCase(reminderId)
        }
    }

    fun deleteReminder(reminderId: Long) {
        viewModelScope.launch {
            deleteReminderUseCase(reminderId)
        }
    }

    // GCS Recording Uploads
    fun associateAndUploadRecording(callId: Long, localPath: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            recordingRepository.associateRecording(callId, localPath)
            val result = recordingRepository.uploadRecording(callId)
            onResult(result)
        }
    }

    fun uploadRecording(callId: Long, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val result = recordingRepository.uploadRecording(callId)
            onResult(result)
        }
    }

    fun retryFailedRecordingUploads() {
        viewModelScope.launch {
            recordingRepository.retryFailedUploads()
        }
    }

    fun deleteRecording(callId: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = recordingRepository.deleteRecording(callId)
            onResult(success)
        }
    }

    fun rescanRecordings() {
        viewModelScope.launch {
            recordingRepository.scanRecordings()
        }
    }

    fun clearRecordingLogs() {
        viewModelScope.launch {
            recordingLogDao.clearAllLogs()
        }
    }

    fun exportRecordingDiagnostics(context: android.content.Context) {
        viewModelScope.launch {
            try {
                val logs = recordingLogs.value
                val sb = StringBuilder()
                sb.append("[\n")
                logs.forEachIndexed { index, log ->
                    sb.append("  {\n")
                    sb.append("    \"id\": ${log.id},\n")
                    sb.append("    \"scanId\": \"${log.scanId}\",\n")
                    sb.append("    \"fileName\": \"${log.fileName}\",\n")
                    sb.append("    \"path\": \"${log.path.replace("\\", "\\\\")}\",\n")
                    sb.append("    \"parser\": \"${log.parser}\",\n")
                    sb.append("    \"phoneExtracted\": ${if (log.phoneExtracted != null) "\"${log.phoneExtracted}\"" else "null"},\n")
                    sb.append("    \"timestampExtracted\": ${log.timestampExtracted ?: "null"},\n")
                    sb.append("    \"candidateCount\": ${log.candidateCount},\n")
                    sb.append("    \"matchedCallId\": ${log.matchedCallId ?: "null"},\n")
                    sb.append("    \"status\": \"${log.status}\",\n")
                    sb.append("    \"reason\": ${if (log.reason != null) "\"${log.reason}\"" else "null"},\n")
                    sb.append("    \"createdAt\": ${log.createdAt}\n")
                    sb.append("  }")
                    if (index < logs.size - 1) {
                        sb.append(",")
                    }
                    sb.append("\n")
                }
                sb.append("]")
                
                val dir = context.getExternalFilesDir(null) ?: context.filesDir
                val file = java.io.File(dir, "recording_diagnostics.json")
                file.writeText(sb.toString())
                
                android.widget.Toast.makeText(context, "Exported to: ${file.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.util.Log.e("CallViewModel", "Failed to export logs", e)
                android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun clearAllRecordings() {
        viewModelScope.launch {
            recordingRepository.clearAllRecordings()
        }
    }

    fun manualMatchRecording(recordingId: Long, callId: Long) {
        viewModelScope.launch {
            recordingRepository.manualMatchRecording(recordingId, callId)
        }
    }

    fun uploadRecordingDirect(recordingId: Long, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val result = recordingRepository.uploadRecordingDirect(recordingId)
            onResult(result)
        }
    }
}
