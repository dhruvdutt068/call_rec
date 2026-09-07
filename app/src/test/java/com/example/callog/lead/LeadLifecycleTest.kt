package com.example.callog.lead

import com.example.callog.core.utils.PhoneNumberNormalizer
import com.example.callog.data.local.dao.LeadDao
import com.example.callog.data.local.dao.PersonDao
import com.example.callog.data.local.entity.*
import com.example.callog.data.provider.ContactDto
import com.example.callog.data.repository.LeadRepositoryImpl
import com.example.callog.domain.model.LeadPriority
import com.example.callog.domain.model.LeadStatus
import com.example.callog.domain.repository.LeadRepository
import com.example.callog.domain.usecase.ResolveAndAttachContactUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LeadLifecycleTest {

    private lateinit var fakePersonDao: FakePersonDao
    private lateinit var fakeLeadDao: FakeLeadDao
    private lateinit var leadRepository: LeadRepository
    private lateinit var resolveAndAttachContactUseCase: ResolveAndAttachContactUseCase

    @Before
    fun setUp() {
        fakePersonDao = FakePersonDao()
        fakeLeadDao = FakeLeadDao()
        leadRepository = LeadRepositoryImpl(fakeLeadDao)
        resolveAndAttachContactUseCase = ResolveAndAttachContactUseCase(fakePersonDao)
    }

    // 1. Create Lead for existing Person (Person count unchanged, Lead count = 1)
    @Test
    fun testCreateLeadForExistingPerson() = runBlocking {
        val person = PersonEntity(
            id = "P1001",
            displayName = "Rahul Sharma",
            companyName = "AllSet Corp",
            notes = "Original identity bio"
        )
        fakePersonDao.insertPerson(person)

        val lead = leadRepository.getOrCreateLeadForPerson(person.id)

        assertNotNull(lead)
        assertEquals("P1001", lead.personId)
        assertEquals(LeadStatus.UNKNOWN, lead.status)
        assertEquals(LeadPriority.MEDIUM, lead.priority)
        assertEquals(1, fakePersonDao.people.size)
        assertEquals(1, fakeLeadDao.leads.size)
    }

    // 2. Retrieve Lead by canonical personId
    @Test
    fun testRetrieveLeadByCanonicalPersonId() = runBlocking {
        val personId = "P1002"
        val existingLead = LeadEntity(
            id = "LEAD_001",
            personId = personId,
            status = "HOT",
            priority = "HIGH",
            notes = "Key enterprise account",
            feedback = "Customer loved the demo",
            feedbackRating = 5
        )
        fakeLeadDao.insertLead(existingLead)

        val retrieved = leadRepository.getLeadForPerson(personId)

        assertNotNull(retrieved)
        assertEquals("LEAD_001", retrieved?.id)
        assertEquals("P1002", retrieved?.personId)
        assertEquals(LeadStatus.HOT, retrieved?.status)
        assertEquals(LeadPriority.HIGH, retrieved?.priority)
        assertEquals("Key enterprise account", retrieved?.notes)
        assertEquals("Customer loved the demo", retrieved?.feedback)
        assertEquals(5, retrieved?.feedbackRating)
    }

    // 3. Update status: UNKNOWN -> WARM -> HOT
    @Test
    fun testUpdateLeadStatusCycle() = runBlocking {
        val personId = "P1003"
        leadRepository.getOrCreateLeadForPerson(personId)

        leadRepository.updateLeadStatus(personId, LeadStatus.WARM)
        var lead = leadRepository.getLeadForPerson(personId)
        assertEquals(LeadStatus.WARM, lead?.status)

        leadRepository.updateLeadStatus(personId, LeadStatus.HOT)
        lead = leadRepository.getLeadForPerson(personId)
        assertEquals(LeadStatus.HOT, lead?.status)
        assertEquals(1, fakeLeadDao.leads.size)
    }

    // 4. Update priority: LOW -> MEDIUM -> HIGH -> URGENT
    @Test
    fun testUpdateLeadPriorityCycle() = runBlocking {
        val personId = "P1004"
        leadRepository.getOrCreateLeadForPerson(personId)

        leadRepository.updateLeadPriority(personId, LeadPriority.LOW)
        assertEquals(LeadPriority.LOW, leadRepository.getLeadForPerson(personId)?.priority)

        leadRepository.updateLeadPriority(personId, LeadPriority.HIGH)
        assertEquals(LeadPriority.HIGH, leadRepository.getLeadForPerson(personId)?.priority)

        leadRepository.updateLeadPriority(personId, LeadPriority.URGENT)
        assertEquals(LeadPriority.URGENT, leadRepository.getLeadForPerson(personId)?.priority)
    }

    // 5. Update notes: Ensure Person.notes remains untouched/separate
    @Test
    fun testUpdateLeadNotesDoesNotOverwritePersonNotes() = runBlocking {
        val person = PersonEntity(
            id = "P1005",
            displayName = "Alice Johnson",
            notes = "Personal friend from university"
        )
        fakePersonDao.insertPerson(person)

        leadRepository.getOrCreateLeadForPerson(person.id)
        leadRepository.updateLeadNotes(person.id, "Discussed annual SaaS contract $50k/year")

        val updatedPerson = fakePersonDao.getPersonById(person.id)
        val lead = leadRepository.getLeadForPerson(person.id)

        assertEquals("Personal friend from university", updatedPerson?.notes)
        assertEquals("Discussed annual SaaS contract $50k/year", lead?.notes)
    }

    // 6. Update feedback and rating
    @Test
    fun testUpdateLeadFeedbackAndRating() = runBlocking {
        val personId = "P1006"
        leadRepository.getOrCreateLeadForPerson(personId)

        leadRepository.updateLeadFeedback(personId, "Needs discount on 10+ seats", 4)
        val lead = leadRepository.getLeadForPerson(personId)

        assertEquals("Needs discount on 10+ seats", lead?.feedback)
        assertEquals(4, lead?.feedbackRating)
    }

    // 7. Update nextFollowUpAt timestamp
    @Test
    fun testUpdateNextFollowUpTimestamp() = runBlocking {
        val personId = "P1007"
        val followUpTime = System.currentTimeMillis() + 86400000L // 24 hours
        leadRepository.getOrCreateLeadForPerson(personId)

        leadRepository.updateLeadFollowUp(personId, followUpTime)
        val lead = leadRepository.getLeadForPerson(personId)

        assertEquals(followUpTime, lead?.nextFollowUpAt)
        assertEquals("PENDING", fakeLeadDao.leads[0].syncStatus)
    }

    // 8. Archive Lead
    @Test
    fun testArchiveLead() = runBlocking {
        val personId = "P1008"
        leadRepository.getOrCreateLeadForPerson(personId)

        leadRepository.archiveLead(personId)
        val lead = leadRepository.getLeadForPerson(personId)

        assertTrue(lead?.isArchived == true)
        assertNotNull(lead?.archivedAt)
    }

    // 9. Duplicate/Retry idempotency: Repeated create must not duplicate Leads
    @Test
    fun testDuplicateUpsertIdempotency() = runBlocking {
        val personId = "P1009"

        val lead1 = leadRepository.getOrCreateLeadForPerson(personId)
        val lead2 = leadRepository.getOrCreateLeadForPerson(personId)
        val lead3 = leadRepository.getOrCreateLeadForPerson(personId)

        assertEquals(lead1.id, lead2.id)
        assertEquals(lead1.id, lead3.id)
        assertEquals(1, fakeLeadDao.leads.size)
    }

    // 10. Offline modification marks sync status as PENDING
    @Test
    fun testOfflineModificationMarksSyncPending() = runBlocking {
        val personId = "P1010"
        val lead = LeadEntity(
            id = "LEAD_SYNC_TEST",
            personId = personId,
            status = "UNKNOWN",
            syncStatus = "SYNCED"
        )
        fakeLeadDao.insertLead(lead)

        leadRepository.updateLeadStatus(personId, LeadStatus.HOT)

        val updatedEntity = fakeLeadDao.getLeadById("LEAD_SYNC_TEST")
        assertEquals("HOT", updatedEntity?.status)
        assertEquals("PENDING", updatedEntity?.syncStatus)
    }

    // 11. Sync test: Pending leads fetched and marked SYNCED
    @Test
    fun testSyncPendingLeadsMarkedSynced() = runBlocking {
        val lead1 = LeadEntity(id = "L1", personId = "P1", status = "HOT", syncStatus = "PENDING")
        val lead2 = LeadEntity(id = "L2", personId = "P2", status = "WARM", syncStatus = "SYNCED")
        fakeLeadDao.insertLeads(listOf(lead1, lead2))

        val pending = fakeLeadDao.getPendingLeads()
        assertEquals(1, pending.size)
        assertEquals("L1", pending.first().id)

        fakeLeadDao.updateSyncStatus("L1", "SYNCED")
        val pendingAfter = fakeLeadDao.getPendingLeads()
        assertEquals(0, pendingAfter.size)
    }

    // 12. Five-Device Canonical Identity Regression: 5 devices with same phone attach to 1 Lead
    @Test
    fun testFiveDeviceRegressionLeadAttachesToCanonicalPerson() = runBlocking {
        val device1Contact = ContactDto("c1", "Rahul Sharma", listOf("+919876543210"), emptyList(), null, false)
        val device2Contact = ContactDto("c2", "Rahul", listOf("09876543210"), emptyList(), null, false)
        val device3Contact = ContactDto("c3", "Rahul Sir", listOf("+91 98765-43210"), emptyList(), null, false)
        val device4Contact = ContactDto("c4", "R Sharma", listOf("9876543210"), emptyList(), null, false)
        val device5Contact = ContactDto("c5", "ABC Client", listOf("+91 9876543210"), emptyList(), null, false)

        val p1 = resolveAndAttachContactUseCase(device1Contact, deviceId = "D001")
        val p2 = resolveAndAttachContactUseCase(device2Contact, deviceId = "D002")
        val p3 = resolveAndAttachContactUseCase(device3Contact, deviceId = "D003")
        val p4 = resolveAndAttachContactUseCase(device4Contact, deviceId = "D004")
        val p5 = resolveAndAttachContactUseCase(device5Contact, deviceId = "D005")

        // 1. All 5 devices MUST resolve to single canonical Person
        assertEquals(p1.id, p2.id)
        assertEquals(p1.id, p3.id)
        assertEquals(p1.id, p4.id)
        assertEquals(p1.id, p5.id)
        assertEquals(1, fakePersonDao.people.size)
        assertEquals(1, fakePersonDao.phoneNumbers.size)
        assertEquals(5, fakePersonDao.aliases.size)

        // 2. Attach CRM Lead to canonical Person
        val lead = leadRepository.getOrCreateLeadForPerson(p1.id)
        leadRepository.updateLeadStatus(p1.id, LeadStatus.HOT)
        leadRepository.updateLeadPriority(p1.id, LeadPriority.URGENT)

        // 3. Any device resolving the person resolves the exact same Lead state
        val leadFromD5Lookup = leadRepository.getLeadForPerson(p5.id)
        assertNotNull(leadFromD5Lookup)
        assertEquals(lead.id, leadFromD5Lookup?.id)
        assertEquals(LeadStatus.HOT, leadFromD5Lookup?.status)
        assertEquals(LeadPriority.URGENT, leadFromD5Lookup?.priority)
        assertEquals(1, fakeLeadDao.leads.size)
    }

    // --- Fake DAOs for Unit Testing ---

    private class FakePersonDao : PersonDao {
        val people = mutableListOf<PersonEntity>()
        val phoneNumbers = mutableListOf<PhoneNumberEntity>()
        val aliases = mutableListOf<ContactAliasEntity>()
        val devices = mutableListOf<DeviceEntity>()

        override suspend fun insertPerson(person: PersonEntity) {
            people.removeAll { it.id == person.id }
            people.add(person)
        }

        override suspend fun insertPeople(people: List<PersonEntity>) {
            people.forEach { insertPerson(it) }
        }

        override suspend fun updatePerson(person: PersonEntity) {
            insertPerson(person)
        }

        override fun getPersonByIdFlow(id: String): Flow<PersonEntity?> = flowOf(people.find { it.id == id })
        override suspend fun getPersonById(id: String): PersonEntity? = people.find { it.id == id }

        override fun getPersonWithDetailsByIdFlow(id: String): Flow<PersonWithDetails?> {
            val p = people.find { it.id == id } ?: return flowOf(null)
            val pns = phoneNumbers.filter { it.personId == id }
            val als = aliases.filter { it.personId == id }
            return flowOf(PersonWithDetails(p, pns, als))
        }

        override suspend fun getPersonWithDetailsById(id: String): PersonWithDetails? {
            val p = people.find { it.id == id } ?: return null
            val pns = phoneNumbers.filter { it.personId == id }
            val als = aliases.filter { it.personId == id }
            return PersonWithDetails(p, pns, als)
        }

        override fun getAllPeopleWithDetailsFlow(): Flow<List<PersonWithDetails>> {
            return flowOf(people.map { p ->
                PersonWithDetails(p, phoneNumbers.filter { it.personId == p.id }, aliases.filter { it.personId == p.id })
            })
        }

        override suspend fun getAllPeopleWithDetails(): List<PersonWithDetails> {
            return people.map { p ->
                PersonWithDetails(p, phoneNumbers.filter { it.personId == p.id }, aliases.filter { it.personId == p.id })
            }
        }

        override suspend fun getPendingPeople(): List<PersonEntity> = people.filter { it.syncStatus == "PENDING" }
        override suspend fun updatePersonSyncStatus(id: String, status: String, updatedAt: Long) {
            val idx = people.indexOfFirst { it.id == id }
            if (idx != -1) people[idx] = people[idx].copy(syncStatus = status, updatedAt = updatedAt)
        }

        override suspend fun insertPhoneNumber(phoneNumber: PhoneNumberEntity) {
            phoneNumbers.removeAll { it.id == phoneNumber.id || it.normalizedNumber == phoneNumber.normalizedNumber }
            phoneNumbers.add(phoneNumber)
        }

        override suspend fun insertPhoneNumbers(phoneNumbers: List<PhoneNumberEntity>) {
            phoneNumbers.forEach { insertPhoneNumber(it) }
        }

        override suspend fun getPhoneNumbersForPerson(personId: String): List<PhoneNumberEntity> = phoneNumbers.filter { it.personId == personId }
        override suspend fun findPhoneNumberByNormalized(normalizedNumber: String): PhoneNumberEntity? = phoneNumbers.find { it.normalizedNumber == normalizedNumber }
        override suspend fun findPhoneNumbersByNormalized(normalizedNumber: String): List<PhoneNumberEntity> = phoneNumbers.filter { it.normalizedNumber == normalizedNumber }
        override suspend fun getPendingPhoneNumbers(): List<PhoneNumberEntity> = phoneNumbers.filter { it.syncStatus == "PENDING" }
        override suspend fun updatePhoneNumberSyncStatus(id: String, status: String) {
            val idx = phoneNumbers.indexOfFirst { it.id == id }
            if (idx != -1) phoneNumbers[idx] = phoneNumbers[idx].copy(syncStatus = status)
        }

        override suspend fun insertAlias(alias: ContactAliasEntity) {
            aliases.removeAll { it.deviceId == alias.deviceId && it.normalizedNumber == alias.normalizedNumber }
            aliases.add(alias)
        }

        override suspend fun insertAliases(aliases: List<ContactAliasEntity>) {
            aliases.forEach { insertAlias(it) }
        }

        override suspend fun getAliasesForPerson(personId: String): List<ContactAliasEntity> = aliases.filter { it.personId == personId }
        override suspend fun getAliasesForDevice(deviceId: String): List<ContactAliasEntity> = aliases.filter { it.deviceId == deviceId }
        override suspend fun findAlias(personId: String, deviceId: String, normalizedNumber: String): ContactAliasEntity? =
            aliases.find { it.personId == personId && it.deviceId == deviceId && it.normalizedNumber == normalizedNumber }
        override suspend fun getPendingAliases(): List<ContactAliasEntity> = aliases.filter { it.syncStatus == "PENDING" }
        override suspend fun updateAliasSyncStatus(id: String, status: String) {
            val idx = aliases.indexOfFirst { it.id == id }
            if (idx != -1) aliases[idx] = aliases[idx].copy(syncStatus = status)
        }

        override suspend fun insertDevice(device: DeviceEntity) {
            devices.removeAll { it.id == device.id }
            devices.add(device)
        }

        override suspend fun getDeviceById(id: String): DeviceEntity? = devices.find { it.id == id }
        override suspend fun getDeviceByIdentifier(identifier: String): DeviceEntity? = devices.find { it.deviceIdentifier == identifier }
        override suspend fun getAllDevices(): List<DeviceEntity> = devices.toList()
        override suspend fun updateDeviceLastSync(id: String, lastSyncAt: Long) {
            val idx = devices.indexOfFirst { it.id == id }
            if (idx != -1) devices[idx] = devices[idx].copy(lastSyncAt = lastSyncAt)
        }

        override fun searchPeopleWithDetailsFlow(query: String): Flow<List<PersonWithDetails>> = flowOf(emptyList())
        override suspend fun searchPeopleWithDetails(query: String): List<PersonWithDetails> = emptyList()
    }

    private class FakeLeadDao : LeadDao {
        val leads = mutableListOf<LeadEntity>()

        override suspend fun insertLead(lead: LeadEntity) {
            leads.removeAll { it.id == lead.id || it.personId == lead.personId }
            leads.add(lead)
        }

        override suspend fun insertLeads(leads: List<LeadEntity>) {
            leads.forEach { insertLead(it) }
        }

        override suspend fun updateLead(lead: LeadEntity) {
            insertLead(lead)
        }

        override suspend fun getLeadById(id: String): LeadEntity? = leads.find { it.id == id }

        override fun getLeadByPersonIdFlow(personId: String): Flow<LeadEntity?> = flowOf(leads.find { it.personId == personId })

        override suspend fun getLeadByPersonId(personId: String): LeadEntity? = leads.find { it.personId == personId }

        override fun getAllLeadsFlow(): Flow<List<LeadEntity>> = flowOf(leads.sortedByDescending { it.updatedAt })

        override suspend fun getAllLeads(): List<LeadEntity> = leads.sortedByDescending { it.updatedAt }

        override suspend fun getPendingLeads(): List<LeadEntity> = leads.filter { it.syncStatus == "PENDING" }

        override suspend fun updateSyncStatus(id: String, status: String) {
            val idx = leads.indexOfFirst { it.id == id }
            if (idx != -1) {
                leads[idx] = leads[idx].copy(syncStatus = status)
            }
        }

        override suspend fun updateStatus(personId: String, status: String, updatedAt: Long) {
            val idx = leads.indexOfFirst { it.personId == personId }
            if (idx != -1) {
                leads[idx] = leads[idx].copy(status = status, updatedAt = updatedAt, syncStatus = "PENDING")
            }
        }

        override suspend fun updatePriority(personId: String, priority: String, updatedAt: Long) {
            val idx = leads.indexOfFirst { it.personId == personId }
            if (idx != -1) {
                leads[idx] = leads[idx].copy(priority = priority, updatedAt = updatedAt, syncStatus = "PENDING")
            }
        }

        override suspend fun updateNotes(personId: String, notes: String, updatedAt: Long) {
            val idx = leads.indexOfFirst { it.personId == personId }
            if (idx != -1) {
                leads[idx] = leads[idx].copy(notes = notes, updatedAt = updatedAt, syncStatus = "PENDING")
            }
        }

        override suspend fun updateFeedback(personId: String, feedback: String, rating: Int?, updatedAt: Long) {
            val idx = leads.indexOfFirst { it.personId == personId }
            if (idx != -1) {
                leads[idx] = leads[idx].copy(feedback = feedback, feedbackRating = rating, updatedAt = updatedAt, syncStatus = "PENDING")
            }
        }

        override suspend fun updateFollowUp(personId: String, nextFollowUpAt: Long?, updatedAt: Long) {
            val idx = leads.indexOfFirst { it.personId == personId }
            if (idx != -1) {
                leads[idx] = leads[idx].copy(nextFollowUpAt = nextFollowUpAt, updatedAt = updatedAt, syncStatus = "PENDING")
            }
        }

        override suspend fun archiveLead(personId: String, archivedAt: Long, updatedAt: Long) {
            val idx = leads.indexOfFirst { it.personId == personId }
            if (idx != -1) {
                leads[idx] = leads[idx].copy(isArchived = true, archivedAt = archivedAt, updatedAt = updatedAt, syncStatus = "PENDING")
            }
        }
    }
}
