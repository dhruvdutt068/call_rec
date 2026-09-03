package com.example.callog.data.repository

import android.os.Build
import com.example.callog.core.utils.PhoneNumberNormalizer
import com.example.callog.data.local.dao.PersonDao
import com.example.callog.data.local.entity.*
import com.example.callog.data.provider.ContactsProvider
import com.example.callog.data.remote.FirestoreService
import com.example.callog.data.remote.SupabaseService
import com.example.callog.data.remote.model.PersonResolutionRequest
import com.example.callog.domain.model.*
import com.example.callog.domain.repository.PersonRepository
import com.example.callog.domain.usecase.ResolveAndAttachContactUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonRepositoryImpl @Inject constructor(
    private val personDao: PersonDao,
    private val contactsProvider: ContactsProvider,
    private val firestoreService: FirestoreService,
    private val supabaseService: SupabaseService,
    private val resolveAndAttachContactUseCase: ResolveAndAttachContactUseCase
) : PersonRepository {

    override fun getPersonByIdFlow(id: String): Flow<Person?> {
        return personDao.getPersonWithDetailsByIdFlow(id).map { it?.toDomain() }
    }

    override suspend fun getPersonById(id: String): Person? = withContext(Dispatchers.IO) {
        personDao.getPersonWithDetailsById(id)?.toDomain()
    }

    override fun getAllPeopleFlow(): Flow<List<Person>> {
        return personDao.getAllPeopleWithDetailsFlow().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getAllPeople(): List<Person> = withContext(Dispatchers.IO) {
        personDao.getAllPeopleWithDetails().map { it.toDomain() }
    }

    override fun searchPeopleFlow(query: String): Flow<List<Person>> {
        return personDao.searchPeopleWithDetailsFlow(query).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun searchPeople(query: String): List<Person> = withContext(Dispatchers.IO) {
        personDao.searchPeopleWithDetails(query).map { it.toDomain() }
    }

    override suspend fun findPersonByNormalizedPhone(normalizedPhone: String): Person? = withContext(Dispatchers.IO) {
        val norm = PhoneNumberNormalizer.normalize(normalizedPhone)
        val phoneMatch = personDao.findPhoneNumberByNormalized(norm) ?: return@withContext null
        personDao.getPersonWithDetailsById(phoneMatch.personId)?.toDomain()
    }

    override suspend fun insertOrUpdatePerson(person: Person) = withContext(Dispatchers.IO) {
        val entity = PersonEntity(
            id = person.id,
            displayName = person.displayName,
            companyName = person.companyName,
            notes = person.notes,
            createdAt = person.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        personDao.insertPerson(entity)
    }

    override suspend fun savePhoneNumber(phoneNumber: PhoneNumber) = withContext(Dispatchers.IO) {
        val entity = PhoneNumberEntity(
            id = phoneNumber.id,
            personId = phoneNumber.personId,
            phoneNumber = phoneNumber.phoneNumber,
            normalizedNumber = phoneNumber.normalizedNumber,
            phoneType = phoneNumber.phoneType,
            isPrimary = phoneNumber.isPrimary
        )
        personDao.insertPhoneNumber(entity)
    }

    override suspend fun saveAlias(alias: ContactAlias) = withContext(Dispatchers.IO) {
        val entity = ContactAliasEntity(
            id = alias.id,
            personId = alias.personId,
            deviceId = alias.deviceId,
            androidContactId = alias.androidContactId,
            aliasName = alias.aliasName,
            phoneNumber = alias.phoneNumber,
            normalizedNumber = alias.normalizedNumber
        )
        personDao.insertAlias(entity)
    }

    override suspend fun getOrCreateCurrentDevice(): Device = withContext(Dispatchers.IO) {
        val phone = firestoreService.getDevicePhoneNumber().ifBlank { "Unknown Phone" }
        val ownerName = firestoreService.getDeviceOwnerName().ifBlank { Build.MODEL ?: "Unknown Device" }
        val identifier = "${Build.MANUFACTURER}_${Build.MODEL}_${PhoneNumberNormalizer.normalize(phone)}".ifBlank { "device_${Build.FINGERPRINT.hashCode()}" }

        val existing = personDao.getDeviceByIdentifier(identifier)
        if (existing != null) {
            return@withContext existing.toDomain()
        }

        val newDevice = DeviceEntity(
            id = "DEV_" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase(),
            deviceName = ownerName,
            devicePhone = phone,
            deviceIdentifier = identifier,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        personDao.insertDevice(newDevice)
        newDevice.toDomain()
    }

    override suspend fun resolveAndAttachContact(
        deviceId: String,
        contact: com.example.callog.data.provider.ContactDto
    ): PersonResolutionResult = withContext(Dispatchers.IO) {
        val primaryPhone = contact.phoneNumbers.firstOrNull() ?: ""
        val normalizedNumber = PhoneNumberNormalizer.normalize(primaryPhone)

        if (normalizedNumber.isBlank()) {
            val localEntity = resolveAndAttachContactUseCase(contact, deviceId)
            return@withContext PersonResolutionResult(
                personId = localEntity.id,
                isFromRemote = false
            )
        }

        val device = getOrCreateCurrentDevice()
        val request = PersonResolutionRequest(
            deviceId = deviceId,
            androidContactId = contact.contactId,
            aliasName = contact.name.ifBlank { "Unknown Contact" },
            phoneNumber = primaryPhone,
            normalizedNumber = normalizedNumber,
            deviceName = device.deviceName,
            devicePhone = device.devicePhone,
            deviceIdentifier = device.deviceIdentifier
        )

        val remoteResult = supabaseService.resolveAndAttachContactRemote(request)
        if (remoteResult.isSuccess) {
            val res = remoteResult.getOrThrow()
            // 1. Authoritative server Person: upsert into local Room
            val existing = personDao.getPersonById(res.personId)
            val personEntity = (existing ?: PersonEntity(
                id = res.personId,
                displayName = contact.name.ifBlank { "Unknown Contact" },
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                syncStatus = "SYNCED"
            )).copy(updatedAt = System.currentTimeMillis(), syncStatus = "SYNCED")
            personDao.insertPerson(personEntity)

            // 2. Attach phone numbers
            contact.phoneNumbers.forEachIndexed { index, rawPhone ->
                val norm = PhoneNumberNormalizer.normalize(rawPhone)
                if (norm.isNotEmpty()) {
                    val phoneEntity = PhoneNumberEntity(
                        id = if (index == 0 && res.phoneNumberId != null) res.phoneNumberId else UUID.randomUUID().toString(),
                        personId = res.personId,
                        phoneNumber = rawPhone,
                        normalizedNumber = norm,
                        phoneType = if (index == 0) "PRIMARY" else "OTHER",
                        isPrimary = index == 0,
                        syncStatus = "SYNCED"
                    )
                    personDao.insertPhoneNumber(phoneEntity)
                }
            }

            // 3. Attach alias
            val aliasEntity = ContactAliasEntity(
                id = res.aliasId ?: UUID.randomUUID().toString(),
                personId = res.personId,
                deviceId = deviceId,
                androidContactId = contact.contactId,
                aliasName = contact.name.ifBlank { "Unknown Contact" },
                phoneNumber = primaryPhone,
                normalizedNumber = normalizedNumber,
                syncStatus = "SYNCED"
            )
            personDao.insertAlias(aliasEntity)

            PersonResolutionResult(
                personId = res.personId,
                phoneNumberId = res.phoneNumberId,
                aliasId = res.aliasId,
                isFromRemote = true
            )
        } else {
            // Offline or network error: fallback to local use case & mark sync as PENDING
            val localEntity = resolveAndAttachContactUseCase(contact, deviceId)
            PersonResolutionResult(
                personId = localEntity.id,
                isFromRemote = false
            )
        }
    }

    override suspend fun syncContactsFromDevice(): List<Person> = withContext(Dispatchers.IO) {
        val device = getOrCreateCurrentDevice()
        val contacts = contactsProvider.fetchContacts()
        val resolvedPeople = mutableListOf<Person>()

        for (contact in contacts) {
            val res = resolveAndAttachContact(device.id, contact)
            val fullPerson = personDao.getPersonWithDetailsById(res.personId)
            if (fullPerson != null) {
                resolvedPeople.add(fullPerson.toDomain())
            }
        }
        resolvedPeople
    }
}

private fun PersonWithDetails.toDomain(): Person {
    return Person(
        id = person.id,
        displayName = person.displayName,
        companyName = person.companyName,
        notes = person.notes,
        phoneNumbers = phoneNumbers.map { it.toDomain() },
        aliases = aliases.map { it.toDomain() },
        createdAt = person.createdAt,
        updatedAt = person.updatedAt
    )
}

private fun PhoneNumberEntity.toDomain(): PhoneNumber {
    return PhoneNumber(
        id = id,
        personId = personId,
        phoneNumber = phoneNumber,
        normalizedNumber = normalizedNumber,
        phoneType = phoneType,
        isPrimary = isPrimary
    )
}

private fun ContactAliasEntity.toDomain(): ContactAlias {
    return ContactAlias(
        id = id,
        personId = personId,
        deviceId = deviceId,
        androidContactId = androidContactId,
        aliasName = aliasName,
        phoneNumber = phoneNumber,
        normalizedNumber = normalizedNumber
    )
}

private fun DeviceEntity.toDomain(): Device {
    return Device(
        id = id,
        deviceName = deviceName,
        devicePhone = devicePhone,
        deviceIdentifier = deviceIdentifier,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastSyncAt = lastSyncAt
    )
}
