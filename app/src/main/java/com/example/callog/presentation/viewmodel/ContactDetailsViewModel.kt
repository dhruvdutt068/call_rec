package com.example.callog.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callog.core.utils.PhoneNumberNormalizer
import com.example.callog.data.provider.ContactDto
import com.example.callog.domain.model.CallLogEntry
import com.example.callog.domain.model.ContactAlias
import com.example.callog.domain.model.Person
import com.example.callog.domain.repository.CallRepository
import com.example.callog.domain.repository.PersonRepository
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
        val person: Person? = null,
        val aliases: List<ContactAlias> = emptyList(),
        val interactionHistory: List<CallLogEntry> = emptyList(),
        val lastFeedbackRating: Int? = null,
        val feedbackNotes: String? = null
    ) : ContactDetailUiState
    data class Error(val message: String) : ContactDetailUiState
}

/**
 * ViewModel for Contact Details screen.
 * Receives the canonical [contactId] / Person ID (String) as its navigation identity,
 * resolves the canonical Person model, and queries data via repositories.
 */
@HiltViewModel
class ContactDetailsViewModel @Inject constructor(
    private val callRepository: CallRepository,
    private val personRepository: PersonRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ContactDetailUiState>(ContactDetailUiState.Loading)
    val uiState: StateFlow<ContactDetailUiState> = _uiState.asStateFlow()

    fun loadContact(contactId: String) {
        viewModelScope.launch {
            _uiState.value = ContactDetailUiState.Loading
            try {
                // 1. Try finding by canonical Person ID in Room
                var person = personRepository.getPersonById(contactId)
                
                // 2. If not found by Person ID, search if contactId is an Android Contact ID or phone number
                if (person == null) {
                    val rawContacts = callRepository.getContacts()
                    val matchedContact = rawContacts.find { it.contactId == contactId }
                    if (matchedContact != null) {
                        val firstPhone = matchedContact.phoneNumbers.firstOrNull() ?: ""
                        person = personRepository.findPersonByNormalizedPhone(firstPhone)
                        if (person == null) {
                            personRepository.syncContactsFromDevice()
                            person = personRepository.findPersonByNormalizedPhone(firstPhone)
                        }
                    }
                }

                val allLogs = callRepository.getCallLogsFlow().first()

                if (person != null) {
                    val phoneList = person.phoneNumbers.map { it.phoneNumber }.ifEmpty { listOf("+91 98765 43210") }
                    val normalizedSet = person.phoneNumbers.map { it.normalizedNumber }.toSet()
                    
                    // Canonical Person identity: load calls directly by personId
                    val personCalls = callRepository.getCallsForPerson(person.id)
                    val matchedLogs = personCalls.ifEmpty {
                        // Fallback for unlinked legacy call records
                        allLogs.filter { log ->
                            log.personId == person.id || normalizedSet.contains(PhoneNumberNormalizer.normalize(log.number))
                        }
                    }

                    val dto = ContactDto(
                        contactId = person.id,
                        name = person.displayName,
                        phoneNumbers = phoneList,
                        emails = emptyList(),
                        photoUri = null,
                        isFavorite = false
                    )

                    _uiState.value = ContactDetailUiState.Success(
                        contact = dto,
                        person = person,
                        aliases = person.aliases,
                        interactionHistory = matchedLogs
                    )
                } else {
                    // Fallback to legacy contact lookup
                    val contacts = callRepository.getContacts()
                    val contact = contacts.find { it.contactId == contactId }
                    if (contact == null) {
                        _uiState.value = ContactDetailUiState.Error("Contact with ID $contactId not found.")
                    } else {
                        val contactNumbers = contact.phoneNumbers.map { PhoneNumberNormalizer.normalize(it) }
                        val matchedLogs = allLogs.filter { log ->
                            val logNorm = PhoneNumberNormalizer.normalize(log.number)
                            contactNumbers.any { it.isNotEmpty() && it == logNorm }
                        }
                        _uiState.value = ContactDetailUiState.Success(
                            contact = contact,
                            interactionHistory = matchedLogs
                        )
                    }
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
