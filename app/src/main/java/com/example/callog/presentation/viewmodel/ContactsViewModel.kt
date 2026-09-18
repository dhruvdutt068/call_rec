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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    val uiState: StateFlow<ContactsUiState> = combine(
        _metadataFlow,
        getCloudContactsUseCase(),
        getDeviceContactsUseCase()
    ) { meta, cloud, device ->
        val (source, query, isRefreshing, hasPermission, syncStatus) = meta
        val filtered = when (source) {
            ContactSource.CLOUD -> {
                if (query.isBlank()) {
                    cloud
                } else {
                    val q = query.trim().lowercase()
                    cloud.filter { item ->
                        item.displayName.lowercase().contains(q) ||
                        (item.companyName?.lowercase()?.contains(q) == true) ||
                        (item.primaryPhone?.contains(q) == true) ||
                        item.person.phoneNumbers.any { it.phoneNumber.contains(q) || it.normalizedNumber.contains(q) } ||
                        item.person.aliases.any { it.aliasName.lowercase().contains(q) }
                    }
                }
            }
            ContactSource.DEVICE -> {
                if (!hasPermission) {
                    emptyList()
                } else if (query.isBlank()) {
                    device
                } else {
                    val q = query.trim().lowercase()
                    device.filter { item ->
                        item.displayName.lowercase().contains(q) ||
                        (item.primaryPhone?.contains(q) == true) ||
                        (item.deviceContact?.phoneNumbers?.any { it.number.contains(q) || it.normalizedNumber.contains(q) } == true)
                    }
                }
            }
        }

        ContactsUiState(
            source = source,
            query = query,
            contacts = filtered,
            isLoading = false,
            isRefreshing = isRefreshing,
            hasContactsPermission = hasPermission,
            syncStatus = syncStatus,
            error = _error.value
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
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
