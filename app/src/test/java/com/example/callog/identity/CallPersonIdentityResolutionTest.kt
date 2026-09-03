package com.example.callog.identity

import com.example.callog.core.utils.PhoneNumberNormalizer
import com.example.callog.data.local.dao.CallDao
import com.example.callog.data.local.dao.PersonDao
import com.example.callog.data.local.entity.CallEntity
import com.example.callog.data.local.entity.ContactAliasEntity
import com.example.callog.data.local.entity.DeviceEntity
import com.example.callog.data.local.entity.PersonEntity
import com.example.callog.data.local.entity.PersonWithDetails
import com.example.callog.data.local.entity.PhoneNumberEntity
import com.example.callog.data.local.entity.SalesCallEntity
import com.example.callog.data.provider.ContactDto
import com.example.callog.data.remote.model.SupabaseSalesCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CallPersonIdentityResolutionTest {

    private lateinit var fakePersonDao: FakePersonDaoForCallTest
    private lateinit var fakeCallDao: FakeCallDaoForTest

    @Before
    fun setUp() {
        fakePersonDao = FakePersonDaoForCallTest()
        fakeCallDao = FakeCallDaoForTest()

        // Seed Canonical Person P001 with number +91 98765 43210 (norm: 9876543210)
        val person = PersonEntity(
            id = "P001",
            displayName = "Rahul Sharma",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        fakePersonDao.people.add(person)
        fakePersonDao.phoneNumbers.add(
            PhoneNumberEntity(
                id = "PN_001",
                personId = "P001",
                phoneNumber = "+91 98765 43210",
                normalizedNumber = "9876543210",
                isPrimary = true,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    // 1. Known phone number resolves to canonical Person ID
    @Test
    fun testKnownNumberResolvesToCanonicalPerson() = runBlocking {
        val rawNumber = "+91 98765 43210"
        val norm = PhoneNumberNormalizer.normalize(rawNumber)
        val matchedPhone = fakePersonDao.findPhoneNumberByNormalized(norm)

        assertNotNull(matchedPhone)
        val resolvedPersonId = matchedPhone?.personId
        assertEquals("P001", resolvedPersonId)

        val callEntity = CallEntity(
            id = 101L,
            name = "Rahul",
            number = rawNumber,
            duration = 120,
            timestamp = System.currentTimeMillis(),
            callType = "INCOMING",
            recordingPath = null,
            personId = resolvedPersonId
        )

        assertEquals("P001", callEntity.personId)
    }

    // 2. Unknown phone number keeps personId = null without creating phantom Person
    @Test
    fun testUnknownNumberHasNullPersonId() = runBlocking {
        val unknownNumber = "+91 91111 22222"
        val norm = PhoneNumberNormalizer.normalize(unknownNumber)
        val matchedPhone = fakePersonDao.findPhoneNumberByNormalized(norm)

        assertNull(matchedPhone)
        val resolvedPersonId = matchedPhone?.personId
        assertNull(resolvedPersonId)

        val callEntity = CallEntity(
            id = 102L,
            name = null,
            number = unknownNumber,
            duration = 45,
            timestamp = System.currentTimeMillis(),
            callType = "OUTGOING",
            recordingPath = null,
            personId = resolvedPersonId
        )

        assertNull(callEntity.personId)
        // Ensure no new Person was created in the database
        assertEquals(1, fakePersonDao.people.size)
    }

    // 3. Contact name differences never affect identity resolution
    @Test
    fun testNameDoesNotAffectIdentityResolution() = runBlocking {
        val rawNumber = "09876543210" // leading zero variation
        val mismatchedName = "Spam / Telemarketer"
        val norm = PhoneNumberNormalizer.normalize(rawNumber)
        val matchedPhone = fakePersonDao.findPhoneNumberByNormalized(norm)

        assertNotNull(matchedPhone)
        assertEquals("P001", matchedPhone?.personId)

        val call = CallEntity(
            id = 103L,
            name = mismatchedName,
            number = rawNumber,
            duration = 10,
            timestamp = System.currentTimeMillis(),
            callType = "MISSED",
            recordingPath = null,
            personId = matchedPhone?.personId
        )

        assertEquals("P001", call.personId)
    }

    // 4. Five-device scenario: D001–D005 calls with varying number formats all resolve to P001
    @Test
    fun testFiveDeviceCallLogResolutionScenario() = runBlocking {
        val deviceCalls = listOf(
            Pair("D001", "+91 98765 43210"),
            Pair("D002", "9876543210"),
            Pair("D003", "+919876543210"),
            Pair("D004", "09876543210"),
            Pair("D005", "98765-43210")
        )

        val resolvedCalls = deviceCalls.mapIndexed { idx, (deviceId, number) ->
            val norm = PhoneNumberNormalizer.normalize(number)
            val match = fakePersonDao.findPhoneNumberByNormalized(norm)
            CallEntity(
                id = (200 + idx).toLong(),
                name = "Device $deviceId Contact",
                number = number,
                duration = 60,
                timestamp = System.currentTimeMillis() + idx * 1000,
                callType = "INCOMING",
                recordingPath = null,
                personId = match?.personId
            )
        }

        assertEquals(5, resolvedCalls.size)
        resolvedCalls.forEach { call ->
            assertEquals("P001", call.personId)
        }
    }

    // 4b. Explicit 5-Device Multi-Alias Contact & Call Resolution:
    // D001 -> Rahul Sharma -> 919876543210
    // D002 -> Rahul -> 919876543210
    // D003 -> Rahul Sir -> 919876543210
    // D004 -> R Sharma -> 919876543210
    // D005 -> ABC Client -> 919876543210
    // Expected: people = 1, phone_numbers = 1, aliases = 5, all calls.personId = same canonical person ID.
    @Test
    fun testExactFiveDeviceContactAndCallResolution() = runBlocking {
        val useCase = com.example.callog.domain.usecase.ResolveAndAttachContactUseCase(fakePersonDao)
        val testDao = FakePersonDaoForCallTest()
        val isolatedUseCase = com.example.callog.domain.usecase.ResolveAndAttachContactUseCase(testDao)

        val multiDeviceContacts = listOf(
            Triple("D001", "Rahul Sharma", "919876543210"),
            Triple("D002", "Rahul", "919876543210"),
            Triple("D003", "Rahul Sir", "919876543210"),
            Triple("D004", "R Sharma", "919876543210"),
            Triple("D005", "ABC Client", "919876543210")
        )

        val resolvedPeople = multiDeviceContacts.map { (deviceId, name, phone) ->
            val dto = ContactDto(
                contactId = "raw_$deviceId",
                name = name,
                phoneNumbers = listOf(phone),
                emails = emptyList(),
                photoUri = null,
                isFavorite = false
            )
            isolatedUseCase(dto, deviceId = deviceId)
        }

        // Verify Contact / Identity state: exactly 1 Person, 1 Phone Number, 5 Aliases
        assertEquals(1, testDao.people.size)
        assertEquals(1, testDao.phoneNumbers.size)
        assertEquals(5, testDao.aliases.size)

        val canonicalPerson = testDao.people.first()
        resolvedPeople.forEach { person ->
            assertEquals(canonicalPerson.id, person.id)
        }

        // Verify Calls from each device resolve to the exact same canonical personId
        val deviceCallLogs = multiDeviceContacts.mapIndexed { idx, (deviceId, name, phone) ->
            val norm = PhoneNumberNormalizer.normalize(phone)
            val matchedPhone = testDao.findPhoneNumberByNormalized(norm)
            CallEntity(
                id = (1000 + idx).toLong(),
                name = name,
                number = phone,
                duration = 45,
                timestamp = System.currentTimeMillis() + idx * 1000L,
                callType = "INCOMING",
                recordingPath = null,
                personId = matchedPhone?.personId
            )
        }

        assertEquals(5, deviceCallLogs.size)
        deviceCallLogs.forEach { call ->
            assertNotNull(call.personId)
            assertEquals(canonicalPerson.id, call.personId)
        }
    }

    // 5. CallDao query operations by personId
    @Test
    fun testCallDaoQueriesByPersonId() = runBlocking {
        val calls = listOf(
            CallEntity(1L, "Rahul", "9876543210", 30, 1000L, "INCOMING", null, personId = "P001"),
            CallEntity(2L, "Rahul", "9876543210", 60, 2000L, "OUTGOING", null, personId = "P001"),
            CallEntity(3L, "Rahul", "9876543210", 90, 3000L, "MISSED", null, personId = "P001"),
            CallEntity(4L, "Priya", "9811122233", 45, 4000L, "INCOMING", null, personId = "P002"),
            CallEntity(5L, "Unknown", "9999900000", 15, 5000L, "INCOMING", null, personId = null)
        )
        fakeCallDao.insertCalls(calls)

        val person1Calls = fakeCallDao.getCallsForPerson("P001")
        assertEquals(3, person1Calls.size)
        assertEquals(3, fakeCallDao.getCallCountForPerson("P001"))

        val person2Calls = fakeCallDao.getCallsForPerson("P002")
        assertEquals(1, person2Calls.size)

        val latestP1 = fakeCallDao.getLatestCallsForPerson("P001", limit = 2)
        assertEquals(2, latestP1.size)
        assertEquals(3000L, latestP1[0].timestamp) // Descending order
        assertEquals(2000L, latestP1[1].timestamp)
    }

    // 6. SalesCallEntity and SupabaseSalesCall inherit personId
    @Test
    fun testSalesCallInheritsPersonId() {
        val call = CallEntity(
            id = 301L,
            name = "Rahul Sharma",
            number = "+91 98765 43210",
            duration = 180,
            timestamp = 1725400000000L,
            callType = "OUTGOING",
            recordingPath = "/path/to/rec.m4a",
            personId = "P001"
        )

        val salesCallEntity = SalesCallEntity(
            salespersonPhone = "9988776655",
            salespersonName = "Sales Agent",
            buyerPhone = call.number,
            buyerName = call.name,
            callType = call.callType,
            callId = call.id,
            duration = call.duration,
            personId = call.personId
        )

        assertEquals("P001", salesCallEntity.personId)

        val supabaseCall = SupabaseSalesCall(
            salespersonPhone = salesCallEntity.salespersonPhone,
            salespersonName = salesCallEntity.salespersonName,
            buyerPhone = salesCallEntity.buyerPhone,
            buyerName = salesCallEntity.buyerName,
            callType = salesCallEntity.callType,
            callId = salesCallEntity.callId,
            duration = salesCallEntity.duration,
            createdAt = salesCallEntity.createdAt,
            personId = salesCallEntity.personId
        )

        assertEquals("P001", supabaseCall.personId)
    }

    // 7. Punctuation and country code variations all normalize and resolve to P001
    @Test
    fun testPunctuationVariationsResolveToSamePerson() = runBlocking {
        val variations = listOf(
            "+91 (987) 654-3210",
            "987.654.3210",
            "+91 987 654 3210",
            "09876543210"
        )

        variations.forEach { raw ->
            val norm = PhoneNumberNormalizer.normalize(raw)
            val match = fakePersonDao.findPhoneNumberByNormalized(norm)
            assertNotNull("Variation $raw with norm $norm should match", match)
            assertEquals("P001", match?.personId)
        }
    }
}

// ── In-Memory Fakes for Testing ──────────────────────────────────────────────

class FakePersonDaoForCallTest : PersonDao {
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

    override fun getPersonWithDetailsByIdFlow(id: String): Flow<PersonWithDetails?> {
        val p = people.find { it.id == id }
        val pwd = p?.let {
            PersonWithDetails(
                person = it,
                phoneNumbers = phoneNumbers.filter { pn -> pn.personId == id },
                aliases = aliases.filter { a -> a.personId == id }
            )
        }
        return flowOf(pwd)
    }

    override suspend fun getPersonWithDetailsById(id: String): PersonWithDetails? {
        val p = people.find { it.id == id } ?: return null
        return PersonWithDetails(
            person = p,
            phoneNumbers = phoneNumbers.filter { it.personId == id },
            aliases = aliases.filter { it.personId == id }
        )
    }

    private fun buildAllPeopleWithDetails(): List<PersonWithDetails> {
        return people.map { p ->
            PersonWithDetails(
                person = p,
                phoneNumbers = phoneNumbers.filter { it.personId == p.id },
                aliases = aliases.filter { it.personId == p.id }
            )
        }
    }

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

    override suspend fun getAllDevices(): List<DeviceEntity> = devices.toList()

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

class FakeCallDaoForTest : CallDao {
    private val calls = mutableListOf<CallEntity>()

    override fun getAllCallsFlow(): Flow<List<CallEntity>> = flowOf(calls.sortedByDescending { it.timestamp })
    override suspend fun getAllCalls(): List<CallEntity> = calls.sortedByDescending { it.timestamp }
    override suspend fun getCallById(id: Long): CallEntity? = calls.find { it.id == id }
    override fun getCallByIdFlow(id: Long): Flow<CallEntity?> = flowOf(calls.find { it.id == id })
    override fun getFavoriteCallsFlow(): Flow<List<CallEntity>> = flowOf(calls.filter { it.isFavorite }.sortedByDescending { it.timestamp })
    override fun getCallsWithRecordingsFlow(): Flow<List<CallEntity>> = flowOf(calls.filter { it.recordingPath != null }.sortedByDescending { it.timestamp })

    override suspend fun insertCall(call: CallEntity): Long {
        calls.removeAll { it.id == call.id }
        calls.add(call)
        return call.id
    }

    override suspend fun insertCalls(newCalls: List<CallEntity>) {
        newCalls.forEach { insertCall(it) }
    }

    override suspend fun updateCall(call: CallEntity) {
        insertCall(call)
    }

    override suspend fun deleteCall(call: CallEntity) {
        calls.removeAll { it.id == call.id }
    }

    override suspend fun deleteAllCalls() {
        calls.clear()
    }

    override suspend fun getPendingCalls(): List<CallEntity> = calls.filter { it.syncStatus != "SYNCED" }
    override suspend fun updateSyncStatus(callId: Long, syncStatus: String) {}
    override suspend fun resetAllSyncStatus() {}

    override fun getCallsForPersonFlow(personId: String): Flow<List<CallEntity>> {
        return flowOf(calls.filter { it.personId == personId }.sortedByDescending { it.timestamp })
    }

    override suspend fun getCallsForPerson(personId: String): List<CallEntity> {
        return calls.filter { it.personId == personId }.sortedByDescending { it.timestamp }
    }

    override suspend fun getCallCountForPerson(personId: String): Int {
        return calls.count { it.personId == personId }
    }

    override suspend fun getLatestCallsForPerson(personId: String, limit: Int): List<CallEntity> {
        return calls.filter { it.personId == personId }.sortedByDescending { it.timestamp }.take(limit)
    }

    override suspend fun updatePersonIdForNumber(number: String, personId: String) {
        val updated = calls.map {
            if (it.number == number) it.copy(personId = personId) else it
        }
        calls.clear()
        calls.addAll(updated)
    }
}
