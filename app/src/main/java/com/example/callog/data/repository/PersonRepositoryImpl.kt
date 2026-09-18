package com.example.callog.data.repository

import android.os.Build
import com.example.callog.core.diagnostics.DeveloperLogger
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

    override suspend fun syncGlobalContactsFromSupabase(): Result<List<Person>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val remotePeopleResult = supabaseService.fetchGlobalPeople()
            if (remotePeopleResult.isFailure) {
                return@withContext Result.failure(remotePeopleResult.exceptionOrNull() ?: Exception("Failed to fetch people from Supabase"))
            }
            val remotePeople = remotePeopleResult.getOrNull() ?: emptyList()

            val remoteDevices = supabaseService.fetchGlobalDevices().getOrNull() ?: emptyList()
            val remotePhones = supabaseService.fetchGlobalPhoneNumbers().getOrNull() ?: emptyList()
            val remoteAliases = supabaseService.fetchGlobalContactAliases().getOrNull() ?: emptyList()

            // 1. Upsert Devices into Room (avoids Foreign Key constraint failure on ContactAliasEntity)
            for (d in remoteDevices) {
                val entity = DeviceEntity(
                    id = d.id,
                    deviceName = d.deviceName,
                    devicePhone = d.devicePhone,
                    deviceIdentifier = d.deviceIdentifier,
                    createdAt = parseTimestamp(d.createdAt),
                    updatedAt = parseTimestamp(d.updatedAt),
                    lastSyncAt = d.lastSyncAt?.let { parseTimestamp(it) }
                )
                personDao.insertDevice(entity)
            }

            // 2. Upsert People into Room
            for (p in remotePeople) {
                val cleanName = p.displayName.trim().let { if (it == "." || it.isBlank()) "Unknown Contact" else it }
                val entity = PersonEntity(
                    id = p.id,
                    displayName = cleanName,
                    companyName = p.companyName?.trim()?.ifBlank { null },
                    notes = p.notes?.trim()?.ifBlank { null },
                    createdAt = parseTimestamp(p.createdAt),
                    updatedAt = parseTimestamp(p.updatedAt),
                    syncStatus = "SYNCED"
                )
                personDao.insertPerson(entity)
            }

            // 3. Upsert Phone Numbers (ensuring parent Person exists to satisfy foreign key)
            for (ph in remotePhones) {
                if (personDao.getPersonById(ph.personId) == null) {
                    val fallbackPerson = PersonEntity(
                        id = ph.personId,
                        displayName = "Unknown Contact",
                        createdAt = parseTimestamp(ph.createdAt),
                        updatedAt = parseTimestamp(ph.createdAt),
                        syncStatus = "SYNCED"
                    )
                    personDao.insertPerson(fallbackPerson)
                }
                val entity = PhoneNumberEntity(
                    id = ph.id,
                    personId = ph.personId,
                    phoneNumber = ph.phoneNumber,
                    normalizedNumber = ph.normalizedNumber,
                    phoneType = ph.phoneType,
                    isPrimary = ph.isPrimary,
                    createdAt = parseTimestamp(ph.createdAt),
                    syncStatus = "SYNCED"
                )
                personDao.insertPhoneNumber(entity)
            }

            // 4. Upsert Aliases (ensuring parent Person and Device exist to satisfy foreign keys)
            for (al in remoteAliases) {
                if (personDao.getPersonById(al.personId) == null) {
                    val cleanName = al.aliasName.trim().let { if (it == "." || it.isBlank()) "Unknown Contact" else it }
                    val fallbackPerson = PersonEntity(
                        id = al.personId,
                        displayName = cleanName,
                        createdAt = parseTimestamp(al.createdAt),
                        updatedAt = parseTimestamp(al.createdAt),
                        syncStatus = "SYNCED"
                    )
                    personDao.insertPerson(fallbackPerson)
                }
                if (personDao.getDeviceById(al.deviceId) == null) {
                    val fallbackDevice = DeviceEntity(
                        id = al.deviceId,
                        deviceName = "Synced Device",
                        devicePhone = al.phoneNumber,
                        deviceIdentifier = al.deviceId,
                        createdAt = parseTimestamp(al.createdAt),
                        updatedAt = parseTimestamp(al.createdAt)
                    )
                    personDao.insertDevice(fallbackDevice)
                }
                val cleanAliasName = al.aliasName.trim().let { if (it == "." || it.isBlank()) "Unknown Contact" else it }
                val entity = ContactAliasEntity(
                    id = al.id,
                    personId = al.personId,
                    deviceId = al.deviceId,
                    androidContactId = al.androidContactId,
                    aliasName = cleanAliasName,
                    phoneNumber = al.phoneNumber,
                    normalizedNumber = al.normalizedNumber,
                    createdAt = parseTimestamp(al.createdAt),
                    syncStatus = "SYNCED"
                )
                personDao.insertAlias(entity)
            }

            val allPeople = getAllPeople()
            Result.success(allPeople)
        } catch (e: Exception) {
            DeveloperLogger.error("PersonRepositoryImpl", "Error syncing global contacts from Supabase: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun createGlobalContact(
        name: String,
        phone: String,
        company: String?,
        notes: String?
    ): Result<Person> = withContext(Dispatchers.IO) {
        return@withContext try {
            val normalized = PhoneNumberNormalizer.normalize(phone)
            val remoteRes = supabaseService.createGlobalPerson(
                name = name,
                phone = phone,
                normalizedPhone = normalized,
                company = company,
                notes = notes
            )
            val personId = if (remoteRes.isSuccess) {
                remoteRes.getOrThrow().id
            } else {
                "P" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
            }

            val now = System.currentTimeMillis()
            val entity = PersonEntity(
                id = personId,
                displayName = name.trim().ifBlank { "New Contact" },
                companyName = company?.trim()?.ifBlank { null },
                notes = notes?.trim()?.ifBlank { null },
                createdAt = now,
                updatedAt = now,
                syncStatus = if (remoteRes.isSuccess) "SYNCED" else "PENDING"
            )
            personDao.insertPerson(entity)

            if (normalized.isNotBlank()) {
                val phoneEntity = PhoneNumberEntity(
                    id = UUID.randomUUID().toString(),
                    personId = personId,
                    phoneNumber = phone.trim(),
                    normalizedNumber = normalized,
                    phoneType = "PRIMARY",
                    isPrimary = true,
                    createdAt = now,
                    syncStatus = if (remoteRes.isSuccess) "SYNCED" else "PENDING"
                )
                personDao.insertPhoneNumber(phoneEntity)
            }

            val created = personDao.getPersonWithDetailsById(personId)?.toDomain()
                ?: Person(
                    id = personId,
                    displayName = name,
                    companyName = company,
                    notes = notes,
                    phoneNumbers = if (normalized.isNotBlank()) listOf(PhoneNumber(id = UUID.randomUUID().toString(), personId = personId, phoneNumber = phone, normalizedNumber = normalized, isPrimary = true)) else emptyList(),
                    aliases = emptyList(),
                    createdAt = now,
                    updatedAt = now
                )

            Result.success(created)
        } catch (e: Exception) {
            DeveloperLogger.error("PersonRepositoryImpl", "Failed to create global contact: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseTimestamp(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            // Try ISO format
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            sdf.parse(isoString)?.time ?: isoString.toLongOrNull() ?: System.currentTimeMillis()
        } catch (e: Exception) {
            try {
                val altSdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                altSdf.parse(isoString)?.time ?: isoString.toLongOrNull() ?: System.currentTimeMillis()
            } catch (e2: Exception) {
                isoString.toLongOrNull() ?: System.currentTimeMillis()
            }
        }
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
