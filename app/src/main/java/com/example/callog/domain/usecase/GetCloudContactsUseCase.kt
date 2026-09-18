package com.example.callog.domain.usecase

import com.example.callog.domain.model.ContactDirectoryItem
import com.example.callog.domain.repository.PersonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case to observe only Cloud / Supabase canonical CRM contacts from local Room cache.
 * Invariant: Never falls back to or mixes with Android ContactsContract.
 */
class GetCloudContactsUseCase @Inject constructor(
    private val personRepository: PersonRepository
) {
    operator fun invoke(): Flow<List<ContactDirectoryItem.Cloud>> {
        return personRepository.getAllPeopleFlow().map { people ->
            people.map { person ->
                val primaryPhone = person.phoneNumbers.firstOrNull { it.isPrimary }?.phoneNumber
                    ?: person.phoneNumbers.firstOrNull()?.phoneNumber
                ContactDirectoryItem.Cloud(
                    person = person,
                    displayName = person.displayName,
                    primaryPhone = primaryPhone
                )
            }
        }
    }
}
