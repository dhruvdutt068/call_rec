package com.example.callog.domain.usecase

import com.example.callog.core.utils.PhoneNumberNormalizer
import com.example.callog.data.local.dao.PersonDao
import com.example.callog.data.local.entity.ContactAliasEntity
import com.example.callog.data.local.entity.PersonEntity
import com.example.callog.data.local.entity.PhoneNumberEntity
import com.example.callog.data.provider.ContactDto
import java.util.UUID
import javax.inject.Inject

/**
 * Domain Use Case responsible for resolving an Android contact to a canonical Person.
 *
 * Algorithm:
 * 1. Read Android contact.
 * 2. Normalize phone numbers.
 * 3. Search local Room phone_numbers by normalized number.
 * 4. If found: reuse existing personId.
 * 5. If not found: create a new canonical Person.
 * 6. Create or update the phone number records.
 * 7. Create or update the contact alias for the current device.
 * 8. Never create another Person merely because the contact name is different.
 */
class ResolveAndAttachContactUseCase @Inject constructor(
    private val personDao: PersonDao
) {

    suspend operator fun invoke(
        contact: ContactDto,
        deviceId: String
    ): PersonEntity {
        val normalizedNumbers = contact.phoneNumbers.map { PhoneNumberNormalizer.normalize(it) }.filter { it.isNotEmpty() }

        var existingPersonId: String? = null

        // 3. Search local Room phone_numbers by normalized number
        for (norm in normalizedNumbers) {
            val match = personDao.findPhoneNumberByNormalized(norm)
            if (match != null) {
                existingPersonId = match.personId
                break
            }
        }

        val person: PersonEntity
        if (existingPersonId != null) {
            // 4. If found: use existing personId
            val existing = personDao.getPersonById(existingPersonId)
            person = existing ?: PersonEntity(
                id = existingPersonId,
                displayName = contact.name.ifBlank { "Unknown Contact" },
                updatedAt = System.currentTimeMillis()
            ).also { personDao.insertPerson(it) }
        } else {
            // 5. If not found: create a new canonical Person
            val newPersonId = "P" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
            person = PersonEntity(
                id = newPersonId,
                displayName = contact.name.ifBlank { "Unknown Contact" },
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            personDao.insertPerson(person)
        }

        // 6. Create or update phone number records
        contact.phoneNumbers.forEachIndexed { index, rawPhone ->
            val norm = PhoneNumberNormalizer.normalize(rawPhone)
            if (norm.isNotEmpty()) {
                val existingPhone = personDao.findPhoneNumberByNormalized(norm)
                if (existingPhone == null) {
                    val phoneEntity = PhoneNumberEntity(
                        id = UUID.randomUUID().toString(),
                        personId = person.id,
                        phoneNumber = rawPhone,
                        normalizedNumber = norm,
                        phoneType = if (index == 0) "PRIMARY" else "OTHER",
                        isPrimary = index == 0
                    )
                    personDao.insertPhoneNumber(phoneEntity)
                }
            }
        }

        // 7. Create or update the contact alias for the current device
        val primaryPhone = contact.phoneNumbers.firstOrNull() ?: ""
        val primaryNormalized = PhoneNumberNormalizer.normalize(primaryPhone)
        val existingAlias = personDao.findAlias(person.id, deviceId, primaryNormalized)
        if (existingAlias == null) {
            val alias = ContactAliasEntity(
                id = UUID.randomUUID().toString(),
                personId = person.id,
                deviceId = deviceId,
                androidContactId = contact.contactId,
                aliasName = contact.name,
                phoneNumber = primaryPhone,
                normalizedNumber = primaryNormalized
            )
            personDao.insertAlias(alias)
        }

        return person
    }
}
