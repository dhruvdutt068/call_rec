package com.example.callog.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callog.data.provider.ContactDto
import com.example.callog.domain.model.CallLogEntry
import com.example.callog.domain.repository.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ContactDetailUiState {
    data object Loading : ContactDetailUiState
    data class Success(
        val contact: ContactDto,
        val interactionHistory: List<CallLogEntry>,
        val lastFeedbackRating: Int? = null,
        val feedbackNotes: String? = null
    ) : ContactDetailUiState
    data class Error(val message: String) : ContactDetailUiState
}

/**
 * ViewModel for Contact Details screen.
 * Receives the canonical [contactId] (String) as its navigation identity,
 * coordinates data access via repositories, and owns the UI state.
 */
@HiltViewModel
class ContactDetailsViewModel @Inject constructor(
    private val callRepository: CallRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ContactDetailUiState>(ContactDetailUiState.Loading)
    val uiState: StateFlow<ContactDetailUiState> = _uiState.asStateFlow()

    fun loadContact(contactId: String) {
        viewModelScope.launch {
            _uiState.value = ContactDetailUiState.Loading
            try {
                val contacts = callRepository.getContacts()
                val contact = contacts.find { it.contactId == contactId }
                if (contact == null) {
                    _uiState.value = ContactDetailUiState.Error("Contact with ID $contactId not found.")
                } else {
                    val allLogs = callRepository.getCallLogsFlow().first()
                    val contactNumbers = contact.phoneNumbers.map { it.replace("[^0-9+]".toRegex(), "") }
                    val matchedLogs = allLogs.filter { log ->
                        val logNum = log.number.replace("[^0-9+]".toRegex(), "")
                        contactNumbers.any { it.isNotEmpty() && (logNum.endsWith(it) || it.endsWith(logNum)) }
                    }
                    _uiState.value = ContactDetailUiState.Success(
                        contact = contact,
                        interactionHistory = matchedLogs
                    )
                }
            } catch (e: Exception) {
                _uiState.value = ContactDetailUiState.Error(e.message ?: "Failed to load contact.")
            }
        }
    }

    fun applyFeedbackResult(rating: Int, notes: String) {
        val current = _uiState.value
        if (current is ContactDetailUiState.Success) {
            _uiState.value = current.copy(
                lastFeedbackRating = rating,
                feedbackNotes = notes
            )
        }
    }
}
