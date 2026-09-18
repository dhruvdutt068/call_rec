package com.example.callog.presentation.viewmodel

import com.example.callog.domain.model.ContactDirectoryItem
import com.example.callog.domain.model.ContactSource

data class ContactsUiState(
    val source: ContactSource = ContactSource.CLOUD,
    val query: String = "",
    val contacts: List<ContactDirectoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasContactsPermission: Boolean = true,
    val syncStatus: String? = null,
    val error: String? = null
)

sealed interface ContactsEvent {
    data class SelectSource(val source: ContactSource) : ContactsEvent
    data class SearchChanged(val query: String) : ContactsEvent
    data object Refresh : ContactsEvent
    data class ContactClicked(val contact: ContactDirectoryItem) : ContactsEvent
    data class PermissionChanged(val granted: Boolean) : ContactsEvent
    data class CreateGlobalContact(
        val name: String,
        val phone: String,
        val company: String?,
        val notes: String?,
        val onComplete: (Boolean) -> Unit
    ) : ContactsEvent
}
