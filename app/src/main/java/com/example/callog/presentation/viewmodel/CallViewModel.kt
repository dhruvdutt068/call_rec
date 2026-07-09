package com.example.callog.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callog.data.local.dao.ReminderWithCall
import com.example.callog.data.local.entity.ReminderEntity
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
    private val recordingRepository: com.example.callog.domain.repository.RecordingRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _callTypeFilter = MutableStateFlow<String?>("ALL") // "ALL", "INCOMING", "OUTGOING", "MISSED", "REJECTED", "RECORDED"
    val callTypeFilter = _callTypeFilter.asStateFlow()

    private val _sortBy = MutableStateFlow("NEWEST") // "NEWEST", "OLDEST", "LONGEST", "SHORTEST"
    val sortBy = _sortBy.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _contacts = MutableStateFlow<List<ContactDto>>(emptyList())
    val contacts = _contacts.asStateFlow()

    private val _firebaseConfig = MutableStateFlow<FirebaseConfig?>(null)
    val firebaseConfig = _firebaseConfig.asStateFlow()

    private val _devicePhoneNumber = MutableStateFlow("")
    val devicePhoneNumber = _devicePhoneNumber.asStateFlow()

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

    init {
        loadContacts()
        loadFirebaseConfig()
        syncLogs()
    }

    private fun loadFirebaseConfig() {
        _firebaseConfig.value = firestoreRepository.getFirebaseConfig()
        _devicePhoneNumber.value = firestoreRepository.getDevicePhoneNumber()
    }

    fun saveFirebaseConfig(config: FirebaseConfig?) {
        firestoreRepository.saveFirebaseConfig(config)
        _firebaseConfig.value = config
        _connectionStatus.value = null
    }

    fun saveDevicePhoneNumber(number: String) {
        firestoreRepository.saveDevicePhoneNumber(number)
        _devicePhoneNumber.value = number
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
            _isSyncing.value = true
            loadContacts()
            syncCallLogsUseCase()
            syncPendingCallsUseCase()
            recordingRepository.retryFailedUploads()
            _isSyncing.value = false
        }
    }

    fun forceReSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            firestoreRepository.resetAllSyncStatus()
            loadContacts()
            syncCallLogsUseCase()
            syncPendingCallsUseCase()
            _isSyncing.value = false
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
}
