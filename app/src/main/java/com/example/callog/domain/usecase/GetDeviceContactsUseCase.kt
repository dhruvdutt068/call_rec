package com.example.callog.domain.usecase

import com.example.callog.domain.model.ContactDirectoryItem
import com.example.callog.domain.model.Person
import com.example.callog.domain.repository.DeviceContactsRepository
import com.example.callog.domain.repository.PersonRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * Use case to observe only local Android ContactsContract contacts.
 * Invariant: Never displays Supabase-only contacts.
 * Enriches device contacts with CRM link status using an in-memory O(1) phone lookup index.
 */
class GetDeviceContactsUseCase @Inject constructor(
    private val deviceContactsRepository: DeviceContactsRepository,
    private val personRepository: PersonRepository
) {
    operator fun invoke(): Flow<List<ContactDirectoryItem.Device>> {
        return combine(
            deviceContactsRepository.observeContacts(),
            personRepository.getAllPeopleFlow()
        ) { deviceContacts, people ->
            // Precompute an O(1) in-memory lookup map for all phone numbers and aliases
            val phoneToPersonMap = HashMap<String, Person>(people.size * 2)
            for (person in people) {
                for (phone in person.phoneNumbers) {
                    if (phone.normalizedNumber.isNotBlank()) {
                        phoneToPersonMap[phone.normalizedNumber] = person
                    }
                }
                for (alias in person.aliases) {
                    if (alias.normalizedNumber.isNotBlank()) {
                        phoneToPersonMap.putIfAbsent(alias.normalizedNumber, person)
                    }
                }
            }

            deviceContacts.map { deviceContact ->
                val primaryPhone = deviceContact.phoneNumbers.firstOrNull()?.number
                val normalized = deviceContact.phoneNumbers.firstOrNull()?.normalizedNumber
                val linkedPerson = normalized?.let { norm ->
                    phoneToPersonMap[norm]
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
