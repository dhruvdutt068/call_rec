package com.example.callog.domain.usecase

import com.example.callog.domain.model.ContactDirectoryItem
import com.example.callog.domain.repository.DeviceContactsRepository
import com.example.callog.domain.repository.PersonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case to observe only local Android ContactsContract contacts.
 * Invariant: Never displays Supabase-only contacts.
 * Checks local Person cache to enrich device contacts with CRM link status if phone matches.
 */
class GetDeviceContactsUseCase @Inject constructor(
    private val deviceContactsRepository: DeviceContactsRepository,
    private val personRepository: PersonRepository
) {
    operator fun invoke(): Flow<List<ContactDirectoryItem.Device>> {
        return deviceContactsRepository.observeContacts().map { deviceContacts ->
            deviceContacts.map { deviceContact ->
                val primaryPhone = deviceContact.phoneNumbers.firstOrNull()?.number
                val normalized = deviceContact.phoneNumbers.firstOrNull()?.normalizedNumber
                val linkedPerson = normalized?.let { norm ->
                    personRepository.findPersonByNormalizedPhone(norm)
                }

                ContactDirectoryItem.Device(
                    contactId = deviceContact.androidContactId,
                    displayName = deviceContact.displayName,
                    primaryPhone = primaryPhone,
                    photoUri = deviceContact.photoUri,
                    deviceContact = deviceContact,
                    linkedPersonId = linkedPerson?.id,
                    linkedPersonName = linkedPerson?.displayName
                )
            }
        }
    }
}
