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
import com.example.callog.domain.model.SupabaseConfig
import com.example.callog.data.remote.SupabaseService
import com.example.callog.domain.repository.CallRepository
import com.example.callog.domain.repository.FirestoreRepository
import com.example.callog.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val simManager: com.example.callog.data.provider.SimManager,
    private val supabaseService: SupabaseService,
    private val personRepository: com.example.callog.domain.repository.PersonRepository,
    private val conversationRepository: com.example.callog.domain.repository.ConversationRepository,
    private val callSessionManager: com.example.callog.domain.service.CallSessionManager,
    private val environmentConfigManager: com.example.callog.core.config.EnvironmentConfigManager
) : ViewModel() {

    val activeEnvironment: StateFlow<com.example.callog.domain.model.AppEnvironment> = environmentConfigManager.activeEnvironment

    fun setEnvironment(env: com.example.callog.domain.model.AppEnvironment) {
        environmentConfigManager.setActiveEnvironment(env)
        loadFirebaseConfig()
        loadSupabaseConfig()
        DeveloperLogger.info("CallViewModel", "Active environment switched to: ${env.label}")
    }

    fun getSupabaseConfigForEnv(env: com.example.callog.domain.model.AppEnvironment): SupabaseConfig {
        return environmentConfigManager.getSupabaseConfig(env)
    }

    fun getFirebaseConfigForEnv(env: com.example.callog.domain.model.AppEnvironment): FirebaseConfig? {
        return environmentConfigManager.getFirebaseConfig(env)
    }

    fun startOutgoingCall(number: String, simSlot: Int = 0) {
        callSessionManager.startOutgoingCall(number, simSlot)
    }

    /**
     * Unified call initiation point for the entire application.
     * Pre-seeds the session, launches InCallActivity, and invokes TelecomManager.placeCall.
     */
    fun initiateCall(context: android.content.Context, number: String, simSlot: Int = 0) {
        if (number.isBlank()) return
        val cleanNumber = number.trim()
        val uri = android.net.Uri.parse("tel:${android.net.Uri.encode(cleanNumber)}")

        // 1. Initialize outgoing call session in CallSessionManager
        callSessionManager.startOutgoingCall(cleanNumber, simSlot)

        // 2. Launch Callog In-Call CRM UI directly in foreground
        try {
            val inCallIntent = android.content.Intent(context, com.example.callog.presentation.call.InCallActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            context.startActivity(inCallIntent)
        } catch (e: Exception) {
            DeveloperLogger.error("CallViewModel", "Failed to launch InCallActivity: ${e.message}")
        }

        // 3. Connect telephony line via TelecomManager if default dialer
        val telecomManager = context.getSystemService(android.content.Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
        val isDefault = com.example.callog.core.telecom.TelecomRoleHelper.isDefaultDialer(context)
        val selectedSim = _activeSims.value.getOrNull(simSlot)

        if (isDefault && telecomManager != null) {
            val extras = android.os.Bundle().apply {
                selectedSim?.let { sim ->
                    putInt("subscription_id", sim.subscriptionId)
                    putInt("android.telephony.extra.SUBSCRIPTION_INDEX", sim.subscriptionId)
                    putInt("com.android.phone.extra.slot", sim.slotIndex)
                    putInt("simSlot", sim.slotIndex)
                }
            }
            try {
                telecomManager.placeCall(uri, extras)
            } catch (se: SecurityException) {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_CALL, uri).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    DeveloperLogger.error("CallViewModel", "Failed to place call via fallback: ${e.message}")
                }
            }
        } else {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_CALL, uri).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    selectedSim?.let { sim ->
                        putExtra("subscription_id", sim.subscriptionId)
                        putExtra("android.telephony.extra.SUBSCRIPTION_INDEX", sim.subscriptionId)
                        putExtra("com.android.phone.extra.slot", sim.slotIndex)
                        putExtra("simSlot", sim.slotIndex)
                    }
                }
                context.startActivity(intent)
            } catch (e: SecurityException) {
                val dialIntent = android.content.Intent(android.content.Intent.ACTION_DIAL, uri).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
            }
        }
    }

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

    private val _contactsSyncStatus = MutableStateFlow<String?>("IDLE") // "IDLE", "SYNCING", "SUCCESS", "ERROR:<msg>"
    val contactsSyncStatus = _contactsSyncStatus.asStateFlow()

    private val _firebaseConfig = MutableStateFlow<FirebaseConfig?>(null)
    val firebaseConfig = _firebaseConfig.asStateFlow()

    private val _supabaseConfig = MutableStateFlow<SupabaseConfig?>(null)
    val supabaseConfig = _supabaseConfig.asStateFlow()

    private val _supabaseConnectionStatus = MutableStateFlow<String?>(null) // null/idle, "TESTING", "SUCCESS", "FAILED:<error>"
    val supabaseConnectionStatus = _supabaseConnectionStatus.asStateFlow()

    private val _devicePhoneNumber = MutableStateFlow("")
    val devicePhoneNumber = _devicePhoneNumber.asStateFlow()

    private val _deviceOwnerName = MutableStateFlow("")
    val deviceOwnerName = _deviceOwnerName.asStateFlow()

    private val _customRecordingPath = MutableStateFlow("")
    val customRecordingPath = _customRecordingPath.asStateFlow()

    private val _isDeveloperModeActive = MutableStateFlow(DeveloperLogger.isDeveloperModeEnabled)
    val isDeveloperModeActive = _isDeveloperModeActive.asStateFlow()

    private var autoLockJob: Job? = null
    private val _autoLockTimeoutMinutes = MutableStateFlow(5)
    val autoLockTimeoutMinutes = _autoLockTimeoutMinutes.asStateFlow()

    private val _lastActivityTimestamp = MutableStateFlow(System.currentTimeMillis())
    val lastActivityTimestamp = _lastActivityTimestamp.asStateFlow()

    fun setAutoLockTimeoutMinutes(minutes: Int) {
        _autoLockTimeoutMinutes.value = minutes.coerceIn(1, 60)
        if (_isDeveloperModeActive.value) {
            restartAutoLockTimer()
        }
    }

    fun recordDeveloperActivity() {
        _lastActivityTimestamp.value = System.currentTimeMillis()
        if (_isDeveloperModeActive.value) {
            restartAutoLockTimer()
        }
    }

    private fun restartAutoLockTimer() {
        autoLockJob?.cancel()
        val timeoutMs = _autoLockTimeoutMinutes.value * 60 * 1000L
        autoLockJob = viewModelScope.launch {
            delay(timeoutMs)
            setDeveloperModeActive(false)
            DeveloperLogger.info("AUTH", "Preferences and developer tools automatically locked after ${_autoLockTimeoutMinutes.value} minutes of inactivity.")
        }
    }

    fun setDeveloperModeActive(active: Boolean) {
        DeveloperLogger.isDeveloperModeEnabled = active
        _isDeveloperModeActive.value = active
        if (active) {
            _lastActivityTimestamp.value = System.currentTimeMillis()
            restartAutoLockTimer()
        } else {
            autoLockJob?.cancel()
            autoLockJob = null
        }
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
        loadSupabaseConfig()
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

    fun testAndSaveFirebaseConfig(config: FirebaseConfig, env: com.example.callog.domain.model.AppEnvironment = activeEnvironment.value) {
        viewModelScope.launch {
            _connectionStatus.value = "TESTING"
            val result = firestoreRepository.testFirebaseConnection(config)
            if (result.isSuccess) {
                firestoreRepository.saveFirebaseConfig(config)
                environmentConfigManager.saveFirebaseConfig(env, config)
                _firebaseConfig.value = config
                _connectionStatus.value = "SUCCESS"
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                _connectionStatus.value = "FAILED:$errorMsg"
            }
        }
    }

    fun resetFirebaseConfig(env: com.example.callog.domain.model.AppEnvironment = activeEnvironment.value) {
        environmentConfigManager.saveFirebaseConfig(env, null)
        firestoreRepository.saveFirebaseConfig(null)
        _firebaseConfig.value = null
        _connectionStatus.value = null
    }

    fun loadSupabaseConfig() {
        _supabaseConfig.value = supabaseService.getSavedConfig()
    }

    fun saveSupabaseConfig(config: SupabaseConfig?, env: com.example.callog.domain.model.AppEnvironment = activeEnvironment.value) {
        supabaseService.saveConfig(config, env)
        _supabaseConfig.value = supabaseService.getSavedConfig()
        _supabaseConnectionStatus.value = null
    }

    fun resetSupabaseConfig(env: com.example.callog.domain.model.AppEnvironment = activeEnvironment.value) {
        supabaseService.resetToDefaults(env)
        _supabaseConfig.value = supabaseService.getSavedConfig()
        _supabaseConnectionStatus.value = null
    }

    fun testAndSaveSupabaseConfig(url: String, key: String, env: com.example.callog.domain.model.AppEnvironment = activeEnvironment.value) {
        viewModelScope.launch {
            _supabaseConnectionStatus.value = "TESTING"
            val result = supabaseService.testSupabaseConnection(url, key)
            if (result.isSuccess) {
                val config = SupabaseConfig(url, key)
                supabaseService.saveConfig(config, env)
                _supabaseConfig.value = supabaseService.getSavedConfig()
                _supabaseConnectionStatus.value = "SUCCESS"
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                _supabaseConnectionStatus.value = "FAILED:$errorMsg"
            }
        }
    }

    fun testDefaultSupabaseConnection() {
        viewModelScope.launch {
            _supabaseConnectionStatus.value = "TESTING"
            val active = supabaseService.getActiveConfig()
            val result = supabaseService.testSupabaseConnection(active.url, active.apiKey)
            if (result.isSuccess) {
                _supabaseConnectionStatus.value = "SUCCESS"
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                _supabaseConnectionStatus.value = "FAILED:$errorMsg"
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
            // 1. Immediately load local Room cached People + device contacts for instant display
            updateCombinedContacts()

            // 2. Fetch latest from Supabase global repo in background
            refreshGlobalContacts()
        }
    }

    private suspend fun updateCombinedContacts() {
        val people = personRepository.getAllPeople()
        val deviceContacts = repository.getContacts()

        val peopleDtos = people.map { person ->
            val phoneList = person.phoneNumbers.map { it.phoneNumber }.ifEmpty {
                person.aliases.map { it.phoneNumber }.distinct()
            }
            val cleanName = person.displayName.trim().let {
                if (it.isBlank() || it == ".") {
                    person.companyName?.trim()?.ifBlank { null }
                        ?: phoneList.firstOrNull()
                        ?: "Cloud Contact ${person.id.takeLast(4)}"
                } else it
            }
            ContactDto(
                contactId = person.id,
                name = cleanName,
                phoneNumbers = phoneList,
                emails = emptyList(),
                photoUri = null,
                isFavorite = false
            )
        }

        // Merge Supabase People with device contacts (avoiding duplicates by normalized number)
        val existingNormNumbers = people.flatMap { p -> p.phoneNumbers.map { it.normalizedNumber } }.toSet()
        val uniqueDeviceContacts = deviceContacts.filter { dc ->
            val firstNorm = dc.phoneNumbers.firstOrNull()?.let { com.example.callog.core.utils.PhoneNumberNormalizer.normalize(it) } ?: ""
            firstNorm.isNotEmpty() && !existingNormNumbers.contains(firstNorm)
        }

        val combined = (peopleDtos + uniqueDeviceContacts).distinctBy { it.contactId }
        _contacts.value = combined
    }

    fun refreshGlobalContacts() {
        viewModelScope.launch {
            _contactsSyncStatus.value = "SYNCING"
            val result = personRepository.syncGlobalContactsFromSupabase()
            if (result.isSuccess) {
                updateCombinedContacts()
                _contactsSyncStatus.value = "SUCCESS"
            } else {
                val err = result.exceptionOrNull()?.message ?: "Sync failed"
                _contactsSyncStatus.value = "ERROR:$err"
                // Ensure at least local device contacts & cached room people are visible
                updateCombinedContacts()
            }
        }
    }

    fun createGlobalContact(
        name: String,
        phone: String,
        company: String? = null,
        notes: String? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _contactsSyncStatus.value = "SYNCING"
            val result = personRepository.createGlobalContact(name, phone, company, notes)
            if (result.isSuccess) {
                updateCombinedContacts()
                _contactsSyncStatus.value = "SUCCESS"
                onComplete?.invoke(true)
            } else {
                val err = result.exceptionOrNull()?.message ?: "Creation failed"
                _contactsSyncStatus.value = "ERROR:$err"
                onComplete?.invoke(false)
            }
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

    // ==========================================
    // PHASE 7: WHATSAPP & HUMAN HANDOVER
    // ==========================================

    fun getConversationForPersonFlow(personId: String): Flow<com.example.callog.domain.model.Conversation?> {
        return conversationRepository.getConversationByPersonIdFlow(personId)
    }

    fun getMessagesForConversationFlow(conversationId: String): Flow<List<com.example.callog.domain.model.ConversationMessage>> {
        return conversationRepository.getMessagesFlow(conversationId)
    }

    fun initializeConversation(personId: String, whatsappNumber: String, onComplete: (com.example.callog.domain.model.Conversation) -> Unit = {}) {
        viewModelScope.launch {
            val conv = conversationRepository.createOrGetConversation(personId, whatsappNumber)
            onComplete(conv)
        }
    }

    fun handoverConversationToHuman(
        conversationId: String,
        reason: com.example.callog.domain.model.HandoverReason,
        assignedUserId: String? = "USER_REP_1",
        assignedUserName: String? = "Sales Rep"
    ) {
        viewModelScope.launch {
            conversationRepository.handoverToHuman(conversationId, reason, assignedUserId, assignedUserName)
            DeveloperLogger.info("Handover", "Conversation $conversationId escalated to human: ${reason.name}")
        }
    }

    fun releaseConversationToAi(conversationId: String) {
        viewModelScope.launch {
            conversationRepository.releaseToAi(conversationId)
            DeveloperLogger.info("Handover", "Conversation $conversationId released back to AI")
        }
    }

    fun assignConversationUser(conversationId: String, userId: String, userName: String) {
        viewModelScope.launch {
            conversationRepository.assignToUser(conversationId, userId, userName)
        }
    }

    fun sendHumanWhatsAppMessage(conversationId: String, text: String, senderName: String = "Sales Rep") {
        viewModelScope.launch {
            if (text.isNotBlank()) {
                conversationRepository.sendHumanMessage(conversationId, text.trim(), senderName)
            }
        }
    }
}
