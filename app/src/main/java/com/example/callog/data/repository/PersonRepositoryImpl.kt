package com.example.callog.data.repository

import android.os.Build
import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.core.utils.PhoneNumberNormalizer
import com.example.callog.data.local.dao.LeadDao
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val personDao: PersonDao,
    private val leadDao: LeadDao,
    private val callDao: com.example.callog.data.local.dao.CallDao,
    private val salesCallDao: com.example.callog.data.local.dao.SalesCallDao,
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

    override fun getSyncedPeopleFlow(): Flow<List<Person>> {
        return personDao.getSyncedPeopleWithDetailsFlow().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getSyncedPeople(): List<Person> = withContext(Dispatchers.IO) {
        personDao.getSyncedPeopleWithDetails().map { it.toDomain() }
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
        val deviceModel = (Build.MODEL ?: "Unknown Device").trim().ifBlank { "Unknown Device" }
        val phone = firestoreService.getDevicePhoneNumber().ifBlank { "Unknown Phone" }
        val ownerName = firestoreService.getDeviceOwnerName().ifBlank { deviceModel }

        // 1. Check local Room first by deviceModel / identifier
        val existingLocal = personDao.getDeviceById(deviceModel) ?: personDao.getDeviceByIdentifier(deviceModel)
        if (existingLocal != null) {
            return@withContext existingLocal.toDomain()
        }

        // 2. Reinstall / Fresh Room: Check Supabase for existing device with this model number
        try {
            val remoteDeviceResult = supabaseService.fetchDeviceByIdentifier(deviceModel)
            val remoteDevice = remoteDeviceResult.getOrNull()
            if (remoteDevice != null) {
                val restoredDevice = DeviceEntity(
                    id = remoteDevice.id,
                    deviceName = remoteDevice.deviceName.ifBlank { ownerName },
                    devicePhone = remoteDevice.devicePhone.ifBlank { phone },
                    deviceIdentifier = remoteDevice.deviceIdentifier,
                    createdAt = parseTimestamp(remoteDevice.createdAt),
                    updatedAt = System.currentTimeMillis()
                )
                personDao.insertDevice(restoredDevice)
                DeveloperLogger.info("DEVICE_RESTORED", "Restored existing device ${remoteDevice.id} for model $deviceModel from Supabase.")
                return@withContext restoredDevice.toDomain()
            }
        } catch (e: Exception) {
            DeveloperLogger.warning("DEVICE_LOOKUP_FAILED", "Failed checking Supabase for model $deviceModel: ${e.message}")
        }

        // 3. New device: Use device model as unique ID and sync to both Supabase and Room
        val newDevice = DeviceEntity(
            id = deviceModel,
            deviceName = ownerName,
            devicePhone = phone,
            deviceIdentifier = deviceModel,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        personDao.insertDevice(newDevice)

        try {
            val supabaseDev = com.example.callog.data.remote.model.SupabaseDevice(
                id = deviceModel,
                deviceName = ownerName,
                devicePhone = phone,
                deviceIdentifier = deviceModel,
                createdAt = formatTimestamp(newDevice.createdAt),
                updatedAt = formatTimestamp(newDevice.updatedAt)
            )
            supabaseService.insertDevice(supabaseDev)
            DeveloperLogger.info("DEVICE_CREATED", "Registered new persistent device $deviceModel in Supabase & Room.")
        } catch (e: Exception) {
            DeveloperLogger.warning("DEVICE_SYNC_PENDING", "Failed to sync device $deviceModel to Supabase immediately: ${e.message}")
        }

        newDevice.toDomain()
    }

    override suspend fun promoteDeviceContactToCrm(contact: com.example.callog.domain.model.DeviceContact): Result<Person> = withContext(Dispatchers.IO) {
        try {
            val device = getOrCreateCurrentDevice()
            val contactDto = com.example.callog.data.provider.ContactDto(
                contactId = contact.androidContactId,
                name = contact.displayName,
                phoneNumbers = contact.phoneNumbers.map { it.number },
                emails = contact.emails,
                photoUri = contact.photoUri,
                isFavorite = contact.isFavorite
            )
            val resolution = resolveAndAttachContact(device.id, contactDto)
            val fullPerson = personDao.getPersonWithDetailsById(resolution.personId)?.toDomain()
            if (fullPerson != null) {
                DeveloperLogger.success("CONTACT_PROMOTED", "Promoted device contact '${contact.displayName}' to CRM Person ${fullPerson.id}")
                Result.success(fullPerson)
            } else {
                Result.failure(Exception("Failed to find resolved person ${resolution.personId} in local database"))
            }
        } catch (e: Exception) {
            DeveloperLogger.error("CONTACT_PROMOTE_FAILED", "Failed to promote device contact: ${e.message}", exception = e)
            Result.failure(e)
        }
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

        // Check local compound alias identity: (deviceId, androidContactId, normalizedNumber)
        val existingAlias = personDao.findAliasByDeviceContactAndPhone(deviceId, contact.contactId, normalizedNumber)
        if (existingAlias != null && existingAlias.syncStatus == "SYNCED") {
            val existingPerson = personDao.getPersonById(existingAlias.personId)
            if (existingPerson != null && existingPerson.syncStatus == "SYNCED") {
                // Already resolved and synced to Supabase - skip redundant remote RPC
                return@withContext PersonResolutionResult(
                    personId = existingAlias.personId,
                    aliasId = existingAlias.id,
                    isFromRemote = false
                )
            }
        }

        // Check if phone number is already canonical and synced locally
        val existingPhone = personDao.findPhoneNumberByNormalized(normalizedNumber)
        if (existingPhone != null && existingPhone.syncStatus == "SYNCED") {
            val existingPerson = personDao.getPersonById(existingPhone.personId)
            if (existingPerson != null && existingPerson.syncStatus == "SYNCED") {
                val aliasId = existingAlias?.id ?: ("ALIAS_" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase())
                if (existingAlias == null) {
                    val aliasEntity = ContactAliasEntity(
                        id = aliasId,
                        personId = existingPerson.id,
                        deviceId = deviceId,
                        androidContactId = contact.contactId,
                        aliasName = contact.name.ifBlank { "Unknown Contact" },
                        phoneNumber = primaryPhone,
                        normalizedNumber = normalizedNumber,
                        syncStatus = "PENDING"
                    )
                    personDao.insertAlias(aliasEntity)
                }
                return@withContext PersonResolutionResult(
                    personId = existingPerson.id,
                    phoneNumberId = existingPhone.id,
                    aliasId = aliasId,
                    isFromRemote = false
                )
            }
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

            // 2. Attach phone numbers
            val phoneEntities = mutableListOf<PhoneNumberEntity>()
            contact.phoneNumbers.forEachIndexed { index, rawPhone ->
                val norm = PhoneNumberNormalizer.normalize(rawPhone)
                if (norm.isNotEmpty()) {
                    val existingPhoneRecord = personDao.findPhoneNumberByNormalized(norm)
                    val phoneEntity = PhoneNumberEntity(
                        id = if (index == 0 && res.phoneNumberId != null) res.phoneNumberId else (existingPhoneRecord?.id ?: UUID.randomUUID().toString()),
                        personId = res.personId,
                        phoneNumber = rawPhone,
                        normalizedNumber = norm,
                        phoneType = if (index == 0) "PRIMARY" else (existingPhoneRecord?.phoneType ?: "OTHER"),
                        isPrimary = index == 0,
                        syncStatus = "SYNCED"
                    )
                    phoneEntities.add(phoneEntity)
                }
            }

            // 3. Attach alias (idempotent with compound key deviceId + androidContactId + normalizedNumber)
            val aliasId = res.aliasId ?: existingAlias?.id ?: ("ALIAS_" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase())
            val aliasEntity = ContactAliasEntity(
                id = aliasId,
                personId = res.personId,
                deviceId = deviceId,
                androidContactId = contact.contactId,
                aliasName = contact.name.ifBlank { "Unknown Contact" },
                phoneNumber = primaryPhone,
                normalizedNumber = normalizedNumber,
                syncStatus = "SYNCED"
            )

            // Atomic insert within a single database transaction to prevent UI invalidation storms
            personDao.insertPersonWithDetails(personEntity, phoneEntities, aliasEntity)

            // If alias was previously linked to a temporary local offline personId, merge & delete the stale person
            val oldPersonId = existingAlias?.personId
            if (oldPersonId != null && oldPersonId != res.personId) {
                mergeAndCleanupStalePerson(oldPersonId, res.personId)
            }

            PersonResolutionResult(
                personId = res.personId,
                phoneNumberId = res.phoneNumberId,
                aliasId = aliasId,
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
        val resolvedPersonIds = mutableSetOf<String>()

        for (contact in contacts) {
            val res = resolveAndAttachContact(device.id, contact)
            resolvedPersonIds.add(res.personId)
        }

        resolvedPersonIds.mapNotNull { personId ->
            personDao.getPersonWithDetailsById(personId)?.toDomain()
        }
    }

    override suspend fun syncGlobalContactsFromSupabase(): Result<List<Person>> = withContext(Dispatchers.IO) {
        return@withContext try {
            // 1. Fetch remote resources concurrently
            val (remotePeopleResult, remoteDevicesResult, remotePhonesResult, remoteAliasesResult, remoteLeadsResult) = coroutineScope {
                val peopleDeferred = async { supabaseService.fetchGlobalPeople() }
                val devicesDeferred = async { supabaseService.fetchGlobalDevices() }
                val phonesDeferred = async { supabaseService.fetchGlobalPhoneNumbers() }
                val aliasesDeferred = async { supabaseService.fetchGlobalContactAliases() }
                val leadsDeferred = async { supabaseService.fetchGlobalLeads() }

                listOf(
                    peopleDeferred.await(),
                    devicesDeferred.await(),
                    phonesDeferred.await(),
                    aliasesDeferred.await(),
                    leadsDeferred.await()
                )
            }

            @Suppress("UNCHECKED_CAST")
            val remotePeople = (remotePeopleResult as Result<List<com.example.callog.data.remote.model.SupabasePerson>>).getOrNull()
                ?: return@withContext Result.failure(remotePeopleResult.exceptionOrNull() ?: Exception("Failed to fetch people from Supabase"))

            @Suppress("UNCHECKED_CAST")
            val remoteDevices = (remoteDevicesResult as Result<List<com.example.callog.data.remote.model.SupabaseDevice>>).getOrNull() ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val remotePhones = (remotePhonesResult as Result<List<com.example.callog.data.remote.model.SupabasePhoneNumber>>).getOrNull() ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val remoteAliases = (remoteAliasesResult as Result<List<com.example.callog.data.remote.model.SupabaseContactAlias>>).getOrNull() ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val remoteLeads = (remoteLeadsResult as Result<List<com.example.callog.data.remote.model.SupabaseLead>>).getOrNull() ?: emptyList()

            // Track existing and new IDs in memory to avoid redundant queries and satisfy foreign keys
            val knownDeviceIds = (personDao.getAllDevices().map { it.id } + remoteDevices.map { it.id }).toMutableSet()
            val knownPersonIds = (personDao.getAllPeopleWithDetails().map { it.person.id } + remotePeople.map { it.id }).toMutableSet()

            val devicesToInsert = mutableListOf<DeviceEntity>()
            for (d in remoteDevices) {
                devicesToInsert.add(
                    DeviceEntity(
                        id = d.id,
                        deviceName = d.deviceName,
                        devicePhone = d.devicePhone,
                        deviceIdentifier = d.deviceIdentifier,
                        createdAt = parseTimestamp(d.createdAt),
                        updatedAt = parseTimestamp(d.updatedAt),
                        lastSyncAt = d.lastSyncAt?.let { parseTimestamp(it) }
                    )
                )
            }

            val peopleToInsert = mutableListOf<PersonEntity>()
            for (p in remotePeople) {
                val cleanName = p.displayName.trim().let { if (it == "." || it.isBlank()) "Unknown Contact" else it }
                peopleToInsert.add(
                    PersonEntity(
                        id = p.id,
                        displayName = cleanName,
                        companyName = p.companyName?.trim()?.ifBlank { null },
                        notes = p.notes?.trim()?.ifBlank { null },
                        createdAt = parseTimestamp(p.createdAt),
                        updatedAt = parseTimestamp(p.updatedAt),
                        syncStatus = "SYNCED"
                    )
                )
            }

            val phoneNumbersToInsert = mutableListOf<PhoneNumberEntity>()
            for (ph in remotePhones) {
                if (!knownPersonIds.contains(ph.personId)) {
                    peopleToInsert.add(
                        PersonEntity(
                            id = ph.personId,
                            displayName = "Unknown Contact",
                            createdAt = parseTimestamp(ph.createdAt),
                            updatedAt = parseTimestamp(ph.createdAt),
                            syncStatus = "SYNCED"
                        )
                    )
                    knownPersonIds.add(ph.personId)
                }
                phoneNumbersToInsert.add(
                    PhoneNumberEntity(
                        id = ph.id,
                        personId = ph.personId,
                        phoneNumber = ph.phoneNumber,
                        normalizedNumber = ph.normalizedNumber,
                        phoneType = ph.phoneType,
                        isPrimary = ph.isPrimary,
                        createdAt = parseTimestamp(ph.createdAt),
                        syncStatus = "SYNCED"
                    )
                )
            }

            val aliasesToInsert = mutableListOf<ContactAliasEntity>()
            for (al in remoteAliases) {
                if (!knownPersonIds.contains(al.personId)) {
                    val cleanAliasName = al.aliasName.trim().let { if (it == "." || it.isBlank()) "Unknown Contact" else it }
                    peopleToInsert.add(
                        PersonEntity(
                            id = al.personId,
                            displayName = cleanAliasName,
                            createdAt = parseTimestamp(al.createdAt),
                            updatedAt = parseTimestamp(al.createdAt),
                            syncStatus = "SYNCED"
                        )
                    )
                    knownPersonIds.add(al.personId)
                }
                if (!knownDeviceIds.contains(al.deviceId)) {
                    devicesToInsert.add(
                        DeviceEntity(
                            id = al.deviceId,
                            deviceName = "Synced Device",
                            devicePhone = al.phoneNumber,
                            deviceIdentifier = al.deviceId,
                            createdAt = parseTimestamp(al.createdAt),
                            updatedAt = parseTimestamp(al.createdAt)
                        )
                    )
                    knownDeviceIds.add(al.deviceId)
                }
                val cleanAliasName = al.aliasName.trim().let { if (it == "." || it.isBlank()) "Unknown Contact" else it }
                aliasesToInsert.add(
                    ContactAliasEntity(
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
                )
            }

            val leadsToInsert = mutableListOf<LeadEntity>()
            for (l in remoteLeads) {
                val existingLocal = leadDao.getLeadByPersonId(l.personId)
                val remoteUpdatedAt = parseTimestamp(l.updatedAt)
                if (existingLocal != null && existingLocal.syncStatus == "PENDING" && existingLocal.updatedAt > remoteUpdatedAt) {
                    // Retain pending offline edits to be synced by background worker
                    continue
                }
                if (!knownPersonIds.contains(l.personId)) {
                    peopleToInsert.add(
                        PersonEntity(
                            id = l.personId,
                            displayName = "Unknown Contact",
                            createdAt = parseTimestamp(l.createdAt),
                            updatedAt = remoteUpdatedAt,
                            syncStatus = "SYNCED"
                        )
                    )
                    knownPersonIds.add(l.personId)
                }
                leadsToInsert.add(
                    LeadEntity(
                        id = l.id,
                        personId = l.personId,
                        status = l.status,
                        priority = l.priority,
                        feedback = l.feedback,
                        feedbackRating = l.feedbackRating,
                        notes = l.notes,
                        ownerId = l.ownerId,
                        source = l.source,
                        nextFollowUpAt = l.nextFollowUpAt?.let { parseTimestamp(it) },
                        isArchived = l.isArchived,
                        archivedAt = l.archivedAt?.let { parseTimestamp(it) },
                        createdAt = parseTimestamp(l.createdAt),
                        updatedAt = remoteUpdatedAt,
                        syncStatus = "SYNCED"
                    )
                )
            }

            // Perform batch Room insertions
            if (devicesToInsert.isNotEmpty()) {
                personDao.insertDevices(devicesToInsert.distinctBy { it.id })
            }
            if (peopleToInsert.isNotEmpty()) {
                personDao.insertPeople(peopleToInsert.distinctBy { it.id })
            }
            if (phoneNumbersToInsert.isNotEmpty()) {
                personDao.insertPhoneNumbers(phoneNumbersToInsert.distinctBy { it.id })
            }
            if (aliasesToInsert.isNotEmpty()) {
                personDao.insertAliases(aliasesToInsert.distinctBy { it.id })
            }
            if (leadsToInsert.isNotEmpty()) {
                leadDao.insertLeads(leadsToInsert.distinctBy { it.id })
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

    private fun formatTimestamp(epochMillis: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date(epochMillis))
    }

    private suspend fun mergeAndCleanupStalePerson(oldPersonId: String, newPersonId: String) {
        try {
            android.util.Log.i("PersonRepository", "Merging stale offline Person $oldPersonId into canonical Person $newPersonId")
            callDao.reassignCallsPersonId(oldPersonId, newPersonId)
            salesCallDao.reassignSalesCallsPersonId(oldPersonId, newPersonId)
            personDao.reassignAliasesPersonId(oldPersonId, newPersonId)
            personDao.deletePhoneNumbersForPerson(oldPersonId)
            personDao.deletePersonById(oldPersonId)
        } catch (e: Exception) {
            android.util.Log.w("PersonRepository", "Error during local person merge/cleanup: ${e.message}")
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
        updatedAt = person.updatedAt,
        syncStatus = person.syncStatus
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
