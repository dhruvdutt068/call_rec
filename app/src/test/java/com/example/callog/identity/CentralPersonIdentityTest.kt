package com.example.callog.identity

import com.example.callog.core.utils.PhoneNumberNormalizer
import com.example.callog.data.local.dao.PersonDao
import com.example.callog.data.local.entity.ContactAliasEntity
import com.example.callog.data.local.entity.DeviceEntity
import com.example.callog.data.local.entity.PersonEntity
import com.example.callog.data.local.entity.PersonWithDetails
import com.example.callog.data.local.entity.PhoneNumberEntity
import com.example.callog.data.provider.ContactDto
import com.example.callog.domain.usecase.ResolveAndAttachContactUseCase
import com.example.callog.presentation.navigation3.allset.AllSetDeepLinkParser
import com.example.callog.presentation.navigation3.allset.AllSetNavKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CentralPersonIdentityTest {

    private lateinit var fakePersonDao: FakePersonDao
    private lateinit var resolveAndAttachContactUseCase: ResolveAndAttachContactUseCase

    @Before
    fun setUp() {
        fakePersonDao = FakePersonDao()
        resolveAndAttachContactUseCase = ResolveAndAttachContactUseCase(fakePersonDao)
    }

    // 1. New phone number creates a Person.
    @Test
    fun testNewPhoneNumberCreatesPerson() = runBlocking {
        val contact = ContactDto(
            contactId = "raw_101",
            name = "Rahul Sharma",
            phoneNumbers = listOf("+919876543210"),
            emails = emptyList(),
            photoUri = null,
            isFavorite = false
        )

        val person = resolveAndAttachContactUseCase(contact, deviceId = "DEV_001")

        assertNotNull(person)
        assertEquals("Rahul Sharma", person.displayName)
        assertTrue(person.id.startsWith("P"))
        assertEquals(1, fakePersonDao.people.size)
        assertEquals(1, fakePersonDao.phoneNumbers.size)
        assertEquals("9876543210", fakePersonDao.phoneNumbers.first().normalizedNumber)
    }

    // 2. Existing phone number resolves to existing Person.
    @Test
    fun testExistingPhoneNumberResolvesToExistingPerson() = runBlocking {
        val contact1 = ContactDto(
            contactId = "c_001",
            name = "Rahul Sharma",
            phoneNumbers = listOf("+91 98765 43210"),
            emails = emptyList(),
            photoUri = null,
            isFavorite = false
        )
        val person1 = resolveAndAttachContactUseCase(contact1, deviceId = "DEV_001")

        val contact2 = ContactDto(
            contactId = "c_002",
            name = "Rahul Sharma",
            phoneNumbers = listOf("9876543210"),
            emails = emptyList(),
            photoUri = null,
            isFavorite = false
        )
        val person2 = resolveAndAttachContactUseCase(contact2, deviceId = "DEV_001")

        assertEquals(person1.id, person2.id)
        assertEquals(1, fakePersonDao.people.size)
    }

    // 3. Different contact names with same phone resolve to one Person.
    @Test
    fun testDifferentContactNamesWithSamePhoneResolveToOnePerson() = runBlocking {
        val device1Contact = ContactDto("c1", "Rahul Sharma", listOf("+919876543210"), emptyList(), null, false)
        val device2Contact = ContactDto("c2", "Rahul Sir", listOf("09876543210"), emptyList(), null, false)
        val device3Contact = ContactDto("c3", "R Sharma", listOf("+91 98765-43210"), emptyList(), null, false)
        val device4Contact = ContactDto("c4", "ABC Client", listOf("9876543210"), emptyList(), null, false)

        val p1 = resolveAndAttachContactUseCase(device1Contact, deviceId = "DEV_1")
        val p2 = resolveAndAttachContactUseCase(device2Contact, deviceId = "DEV_2")
        val p3 = resolveAndAttachContactUseCase(device3Contact, deviceId = "DEV_3")
        val p4 = resolveAndAttachContactUseCase(device4Contact, deviceId = "DEV_4")

        // MUST all resolve to the same canonical Person ID
        assertEquals(p1.id, p2.id)
        assertEquals(p1.id, p3.id)
        assertEquals(p1.id, p4.id)

        assertEquals(1, fakePersonDao.people.size)
        // 4 aliases recorded across the 4 devices
        assertEquals(4, fakePersonDao.aliases.size)
    }

    // 4. Same contact imported from multiple devices creates aliases, not duplicate people.
    @Test
    fun testSameContactImportedFromMultipleDevicesCreatesAliasesNotDuplicatePeople() = runBlocking {
        val phone = "+919876543210"
        val d1 = ContactDto("1", "Rahul Sharma", listOf(phone), emptyList(), null, false)
        val d2 = ContactDto("2", "Rahul", listOf(phone), emptyList(), null, false)
        val d3 = ContactDto("3", "Rahul Sir", listOf(phone), emptyList(), null, false)
        val d4 = ContactDto("4", "R Sharma", listOf(phone), emptyList(), null, false)
        val d5 = ContactDto("5", "ABC Client", listOf(phone), emptyList(), null, false)

        resolveAndAttachContactUseCase(d1, "Device 1")
        resolveAndAttachContactUseCase(d2, "Device 2")
        resolveAndAttachContactUseCase(d3, "Device 3")
        resolveAndAttachContactUseCase(d4, "Device 4")
        resolveAndAttachContactUseCase(d5, "Device 5")

        assertEquals(1, fakePersonDao.people.size)
        val aliases = fakePersonDao.aliases
        assertEquals(5, aliases.size)

        val aliasNames = aliases.map { it.aliasName }.toSet()
        assertTrue(aliasNames.contains("Rahul Sharma"))
        assertTrue(aliasNames.contains("Rahul"))
        assertTrue(aliasNames.contains("Rahul Sir"))
        assertTrue(aliasNames.contains("R Sharma"))
        assertTrue(aliasNames.contains("ABC Client"))
    }

    // 5. Phone number normalization works consistently.
    @Test
    fun testPhoneNumberNormalizationWorksConsistently() {
        val n1 = PhoneNumberNormalizer.normalize("+91 98765 43210")
        val n2 = PhoneNumberNormalizer.normalize("+91-98765-43210")
        val n3 = PhoneNumberNormalizer.normalize("09876543210")
        val n4 = PhoneNumberNormalizer.normalize("9876543210")
        val n5 = PhoneNumberNormalizer.normalize("+919876543210")

        assertEquals("9876543210", n1)
        assertEquals(n1, n2)
        assertEquals(n2, n3)
        assertEquals(n3, n4)
        assertEquals(n4, n5)
    }

    // 6. Existing Person can have multiple phone numbers.
    @Test
    fun testExistingPersonCanHaveMultiplePhoneNumbers() = runBlocking {
        val contactWithMultipleNumbers = ContactDto(
            contactId = "multi_01",
            name = "Vikram Patel",
            phoneNumbers = listOf("+919876543210", "+919123456789", "+919988776655"),
            emails = emptyList(),
            photoUri = null,
            isFavorite = false
        )

        val person = resolveAndAttachContactUseCase(contactWithMultipleNumbers, deviceId = "DEV_001")

        assertEquals(1, fakePersonDao.people.size)
        val personPhones = fakePersonDao.phoneNumbers.filter { it.personId == person.id }
        assertEquals(3, personPhones.size)

        val normalizedList = personPhones.map { it.normalizedNumber }
        assertTrue(normalizedList.contains("9876543210"))
        assertTrue(normalizedList.contains("9123456789"))
        assertTrue(normalizedList.contains("9988776655"))
    }

    // 7. Existing navigation using canonical IDs is not broken.
    @Test
    fun testExistingNavigationUsingCanonicalIdsIsNotBroken() {
        val canonicalPersonId = "P001"
        val deepLinkUri = "allset://contact/$canonicalPersonId"
        val parsedKey = AllSetDeepLinkParser.parseUrl(deepLinkUri)

        assertNotNull(parsedKey)
        assertTrue(parsedKey is AllSetNavKey.Contacts.ContactDetails)
        assertEquals("P001", (parsedKey as AllSetNavKey.Contacts.ContactDetails).contactId)

        val logDeepLink = AllSetDeepLinkParser.parseUrl("allset://logs")
        assertNotNull(logDeepLink)
        assertEquals(AllSetNavKey.CallLogs, logDeepLink)
    }

    // --- In-Memory Fake DAO ---
    class FakePersonDao : PersonDao {
        val people = mutableListOf<PersonEntity>()
        val phoneNumbers = mutableListOf<PhoneNumberEntity>()
        val aliases = mutableListOf<ContactAliasEntity>()
        val devices = mutableListOf<DeviceEntity>()

        override suspend fun insertPerson(person: PersonEntity) {
            people.removeAll { it.id == person.id }
            people.add(person)
        }

        override suspend fun insertPeople(peopleList: List<PersonEntity>) {
            peopleList.forEach { insertPerson(it) }
        }

        override suspend fun updatePerson(person: PersonEntity) {
            insertPerson(person)
        }

        override fun getPersonByIdFlow(id: String): Flow<PersonEntity?> = flowOf(people.find { it.id == id })

        override suspend fun getPersonById(id: String): PersonEntity? = people.find { it.id == id }

        private fun buildPersonWithDetails(id: String): PersonWithDetails? {
            val p = people.find { it.id == id } ?: return null
            val phones = phoneNumbers.filter { it.personId == id }
            val als = aliases.filter { it.personId == id }
            return PersonWithDetails(p, phones, als)
        }

        private fun buildAllPeopleWithDetails(): List<PersonWithDetails> {
            return people.map { p ->
                val phones = phoneNumbers.filter { it.personId == p.id }
                val als = aliases.filter { it.personId == p.id }
                PersonWithDetails(p, phones, als)
            }
        }

        override fun getPersonWithDetailsByIdFlow(id: String): Flow<PersonWithDetails?> = flowOf(buildPersonWithDetails(id))

        override suspend fun getPersonWithDetailsById(id: String): PersonWithDetails? = buildPersonWithDetails(id)

        override fun getAllPeopleWithDetailsFlow(): Flow<List<PersonWithDetails>> = flowOf(buildAllPeopleWithDetails())

        override suspend fun getAllPeopleWithDetails(): List<PersonWithDetails> = buildAllPeopleWithDetails()

        override suspend fun getPendingPeople(): List<PersonEntity> = people.filter { it.syncStatus == "PENDING" }

        override suspend fun updatePersonSyncStatus(id: String, status: String, updatedAt: Long) {
            val p = people.find { it.id == id }
            if (p != null) {
                insertPerson(p.copy(syncStatus = status, updatedAt = updatedAt))
            }
        }

        override suspend fun insertPhoneNumber(phoneNumber: PhoneNumberEntity) {
            phoneNumbers.removeAll { it.id == phoneNumber.id }
            phoneNumbers.add(phoneNumber)
        }

        override suspend fun insertPhoneNumbers(phoneNumbersList: List<PhoneNumberEntity>) {
            phoneNumbersList.forEach { insertPhoneNumber(it) }
        }

        override suspend fun getPhoneNumbersForPerson(personId: String): List<PhoneNumberEntity> {
            return phoneNumbers.filter { it.personId == personId }
        }

        override suspend fun findPhoneNumberByNormalized(normalizedNumber: String): PhoneNumberEntity? {
            return phoneNumbers.find { it.normalizedNumber == normalizedNumber }
        }

        override suspend fun findPhoneNumbersByNormalized(normalizedNumber: String): List<PhoneNumberEntity> {
            return phoneNumbers.filter { it.normalizedNumber == normalizedNumber }
        }

        override suspend fun getPendingPhoneNumbers(): List<PhoneNumberEntity> = phoneNumbers.filter { it.syncStatus == "PENDING" }

        override suspend fun updatePhoneNumberSyncStatus(id: String, status: String) {
            val pn = phoneNumbers.find { it.id == id }
            if (pn != null) {
                insertPhoneNumber(pn.copy(syncStatus = status))
            }
        }

        override suspend fun insertAlias(alias: ContactAliasEntity) {
            aliases.removeAll { it.id == alias.id }
            aliases.add(alias)
        }

        override suspend fun insertAliases(aliasesList: List<ContactAliasEntity>) {
            aliasesList.forEach { insertAlias(it) }
        }

        override suspend fun getAliasesForPerson(personId: String): List<ContactAliasEntity> {
            return aliases.filter { it.personId == personId }
        }

        override suspend fun getAliasesForDevice(deviceId: String): List<ContactAliasEntity> {
            return aliases.filter { it.deviceId == deviceId }
        }

        override suspend fun findAlias(personId: String, deviceId: String, normalizedNumber: String): ContactAliasEntity? {
            return aliases.find { it.personId == personId && it.deviceId == deviceId && it.normalizedNumber == normalizedNumber }
        }

        override suspend fun getPendingAliases(): List<ContactAliasEntity> = aliases.filter { it.syncStatus == "PENDING" }

        override suspend fun updateAliasSyncStatus(id: String, status: String) {
            val a = aliases.find { it.id == id }
            if (a != null) {
                insertAlias(a.copy(syncStatus = status))
            }
        }

        override suspend fun insertDevice(device: DeviceEntity) {
            devices.removeAll { it.id == device.id }
            devices.add(device)
        }

        override suspend fun getDeviceById(id: String): DeviceEntity? = devices.find { it.id == id }

        override suspend fun getDeviceByIdentifier(identifier: String): DeviceEntity? = devices.find { it.deviceIdentifier == identifier }

        override suspend fun getAllDevices(): List<DeviceEntity> = devices

        override suspend fun updateDeviceLastSync(id: String, lastSyncAt: Long) {
            val d = devices.find { it.id == id }
            if (d != null) {
                insertDevice(d.copy(lastSyncAt = lastSyncAt))
            }
        }

        private fun executeSearchPeopleWithDetails(query: String): List<PersonWithDetails> {
            val q = query.lowercase()
            return buildAllPeopleWithDetails().filter { pwd ->
                pwd.person.displayName.lowercase().contains(q) ||
                (pwd.person.companyName != null && pwd.person.companyName.lowercase().contains(q)) ||
                pwd.phoneNumbers.any { it.phoneNumber.contains(q) || it.normalizedNumber.contains(q) } ||
                pwd.aliases.any { it.aliasName.lowercase().contains(q) || it.phoneNumber.contains(q) }
            }
        }

        override fun searchPeopleWithDetailsFlow(query: String): Flow<List<PersonWithDetails>> = flowOf(executeSearchPeopleWithDetails(query))

        override suspend fun searchPeopleWithDetails(query: String): List<PersonWithDetails> = executeSearchPeopleWithDetails(query)
    }
}
