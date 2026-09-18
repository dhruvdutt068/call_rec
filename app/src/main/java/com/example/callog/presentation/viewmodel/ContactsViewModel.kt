package com.example.callog.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callog.domain.model.ContactDirectoryItem
import com.example.callog.domain.model.ContactSource
import com.example.callog.domain.repository.DeviceContactsRepository
import com.example.callog.domain.repository.PersonRepository
import com.example.callog.domain.usecase.GetCloudContactsUseCase
import com.example.callog.domain.usecase.GetDeviceContactsUseCase
import com.example.callog.domain.usecase.RefreshDeviceContactsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactsViewModel(
    private val personRepository: PersonRepository,
    private val deviceContactsRepository: DeviceContactsRepository,
    private val getCloudContactsUseCase: GetCloudContactsUseCase,
    private val getDeviceContactsUseCase: GetDeviceContactsUseCase,
    private val refreshDeviceContactsUseCase: RefreshDeviceContactsUseCase,
    sharingStarted: SharingStarted
) : ViewModel() {

    @Inject
    constructor(
        personRepository: PersonRepository,
        deviceContactsRepository: DeviceContactsRepository,
        getCloudContactsUseCase: GetCloudContactsUseCase,
        getDeviceContactsUseCase: GetDeviceContactsUseCase,
        refreshDeviceContactsUseCase: RefreshDeviceContactsUseCase
    ) : this(
        personRepository,
        deviceContactsRepository,
        getCloudContactsUseCase,
        getDeviceContactsUseCase,
        refreshDeviceContactsUseCase,
        SharingStarted.WhileSubscribed(5_000)
    )

    private val _selectedSource = MutableStateFlow(ContactSource.CLOUD)
    private val _searchQuery = MutableStateFlow("")
    private val _isRefreshing = MutableStateFlow(false)
    private val _hasContactsPermission = MutableStateFlow(true)
    private val _syncStatus = MutableStateFlow<String?>(null)
    private val _error = MutableStateFlow<String?>(null)

    private val _metadataFlow = combine(
        _selectedSource,
        _searchQuery,
        _isRefreshing,
        _hasContactsPermission,
        _syncStatus
    ) { source, query, isRefreshing, hasPermission, syncStatus ->
        FilterMeta(source, query, isRefreshing, hasPermission, syncStatus)
    }

    private val _activeContactsFlow: Flow<List<ContactDirectoryItem>> = combine(
        _selectedSource,
        _hasContactsPermission
    ) { source, hasPermission ->
        source to hasPermission
    }.flatMapLatest { (source, hasPermission) ->
        when (source) {
            ContactSource.CLOUD -> getCloudContactsUseCase()
            ContactSource.DEVICE -> {
                if (!hasPermission) flowOf(emptyList()) else getDeviceContactsUseCase()
            }
        }
    }

    val uiState: StateFlow<ContactsUiState> = combine(
        _activeContactsFlow,
        _metadataFlow,
        _error
    ) { rawList, meta, error ->
        val query = meta.query.trim().lowercase()
        val digitsQuery = query.filter { it.isDigit() }
        val filtered = if (query.isBlank()) {
            rawList
        } else {
            when (meta.source) {
                ContactSource.CLOUD -> {
                    rawList.filter { item ->
                        if (item !is ContactDirectoryItem.Cloud) return@filter false
                        item.displayName.lowercase().contains(query) ||
                        (item.companyName?.lowercase()?.contains(query) == true) ||
                        (item.primaryPhone?.lowercase()?.contains(query) == true) ||
                        (digitsQuery.length >= 2 && item.primaryPhone?.filter { it.isDigit() }?.contains(digitsQuery) == true) ||
                        item.person.phoneNumbers.any { 
                            it.phoneNumber.lowercase().contains(query) || 
                            it.normalizedNumber.lowercase().contains(query) ||
                            (digitsQuery.length >= 2 && it.normalizedNumber.contains(digitsQuery))
                        } ||
                        item.person.aliases.any { 
                            it.aliasName.lowercase().contains(query) ||
                            (digitsQuery.length >= 2 && it.normalizedNumber.contains(digitsQuery))
                        }
                    }
                }
                ContactSource.DEVICE -> {
                    rawList.filter { item ->
                        if (item !is ContactDirectoryItem.Device) return@filter false
                        item.displayName.lowercase().contains(query) ||
                        (item.primaryPhone?.lowercase()?.contains(query) == true) ||
                        (digitsQuery.length >= 2 && item.primaryPhone?.filter { it.isDigit() }?.contains(digitsQuery) == true) ||
                        (item.linkedPersonName?.lowercase()?.contains(query) == true) ||
                        (item.deviceContact?.phoneNumbers?.any { 
                            it.number.lowercase().contains(query) || 
                            it.normalizedNumber.lowercase().contains(query) ||
                            (digitsQuery.length >= 2 && it.normalizedNumber.contains(digitsQuery))
                        } == true)
                    }
                }
            }
        }
        ContactsUiState(
            source = meta.source,
            query = meta.query,
            contacts = filtered,
            isLoading = false,
            isRefreshing = meta.isRefreshing,
            hasContactsPermission = meta.hasPermission,
            syncStatus = meta.syncStatus,
            error = error
        )
    }.stateIn(
        viewModelScope,
        sharingStarted,
        ContactsUiState()
    )

    fun onEvent(event: ContactsEvent) {
        when (event) {
            is ContactsEvent.SelectSource -> selectSource(event.source)
            is ContactsEvent.SearchChanged -> onSearchQueryChanged(event.query)
            is ContactsEvent.Refresh -> refresh()
            is ContactsEvent.ContactClicked -> { /* Navigation handled in UI */ }
            is ContactsEvent.PermissionChanged -> setContactsPermission(event.granted)
            is ContactsEvent.CreateGlobalContact -> {
                createGlobalContact(event.name, event.phone, event.company, event.notes, event.onComplete)
            }
        }
    }

    fun selectSource(source: ContactSource) {
        _selectedSource.value = source
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun setContactsPermission(granted: Boolean) {
        _hasContactsPermission.value = granted
        if (granted) {
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            when (_selectedSource.value) {
                ContactSource.CLOUD -> {
                    _syncStatus.value = "SYNCING"
                    val result = personRepository.syncGlobalContactsFromSupabase()
                    _syncStatus.value = if (result.isSuccess) "SYNC_SUCCESS" else "ERROR: ${result.exceptionOrNull()?.message}"
                }
                ContactSource.DEVICE -> {
                    refreshDeviceContactsUseCase()
                }
            }
            _isRefreshing.value = false
        }
    }

    fun createGlobalContact(
        name: String,
        phone: String,
        company: String?,
        notes: String?,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val result = personRepository.createGlobalContact(name, phone, company, notes)
            onComplete(result.isSuccess)
            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to create contact"
            }
        }
    }

    private data class FilterMeta(
        val source: ContactSource,
        val query: String,
        val isRefreshing: Boolean,
        val hasPermission: Boolean,
        val syncStatus: String?
    )
}
