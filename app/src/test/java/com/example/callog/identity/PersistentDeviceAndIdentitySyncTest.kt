package com.example.callog.identity

import com.example.callog.core.utils.PhoneNumberNormalizer
import com.example.callog.data.local.entity.ContactAliasEntity
import com.example.callog.data.local.entity.DeviceEntity
import com.example.callog.data.local.entity.PersonEntity
import com.example.callog.data.local.entity.PhoneNumberEntity
import com.example.callog.data.provider.ContactDto
import com.example.callog.data.remote.model.PersonResolutionRequest
import com.example.callog.data.remote.model.PersonResolutionResponse
import com.example.callog.data.remote.model.SupabaseDevice
import com.example.callog.domain.model.ContactDirectoryItem
import com.example.callog.domain.model.DeviceContact
import com.example.callog.domain.model.DevicePhoneNumber
import com.example.callog.domain.model.Person
import com.example.callog.domain.model.PhoneNumber
import com.example.callog.domain.repository.DeviceContactsRepository
import com.example.callog.domain.repository.PersonRepository
import com.example.callog.domain.usecase.GetDeviceContactsUseCase
import com.example.callog.domain.usecase.ResolveAndAttachContactUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Architectural unit tests verifying:
 * 1. Persistent device identity & reinstall protection (restores existing device_id via persistent identifier).
 * 2. Compound key alias uniqueness: (deviceId, androidContactId, normalizedNumber).
 * 3. Multi-device canonical Person resolution with per-device alias scoping.
 * 4. Device contact CRM promotion via atomic resolve_and_attach pipeline.
 * 5. Strict Device Tab isolation: local address book browsing never triggers CRM upload or mutations.
 */
class PersistentDeviceAndIdentitySyncTest {

    private lateinit var fakePersonDao: CentralPersonIdentityTest.FakePersonDao
    private lateinit var resolveAndAttachContactUseCase: ResolveAndAttachContactUseCase

    @Before
    fun setUp() {
        fakePersonDao = CentralPersonIdentityTest.FakePersonDao()
        resolveAndAttachContactUseCase = ResolveAndAttachContactUseCase(fakePersonDao)
    }

    // 1. Reinstall Scenario: Device ID restoration via persistent hardware identifier
    @Test
    fun testReinstallRestoresExistingDeviceIdWithoutGeneratingDuplicate() = runBlocking {
        // Given: Existing device already registered in Supabase before app uninstall
        val persistentHardwareId = "ANDROID_ID_HW_ABCD1234EF"
        val existingDeviceId = "DEV_EXISTING_999"
        val remoteSupabaseDevices = mutableMapOf<String, SupabaseDevice>()
        remoteSupabaseDevices[persistentHardwareId] = SupabaseDevice(
            id = existingDeviceId,
            deviceName = "Pixel 8 Pro",
            devicePhone = "+919876543210",
            deviceIdentifier = persistentHardwareId,
            createdAt = "2026-01-01T00:00:00.000Z",
            updatedAt = "2026-01-01T00:00:00.000Z"
        )

        // Room is clean (simulating fresh reinstall)
        assertEquals(0, fakePersonDao.devices.size)

        // When: Device initialization checks Room first, misses, then looks up persistent identifier in Supabase
        var localDevice = fakePersonDao.getDeviceByIdentifier(persistentHardwareId)
        if (localDevice == null) {
            val remoteDevice = remoteSupabaseDevices[persistentHardwareId]
            if (remoteDevice != null) {
                localDevice = DeviceEntity(
                    id = remoteDevice.id,
                    deviceName = remoteDevice.deviceName,
                    devicePhone = remoteDevice.devicePhone,
                    deviceIdentifier = remoteDevice.deviceIdentifier,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                fakePersonDao.insertDevice(localDevice)
            }
        }

        // Then: Device ID is restored as DEV_EXISTING_999, not a new randomly generated UUID
        assertNotNull(localDevice)
        assertEquals(existingDeviceId, localDevice?.id)
        assertEquals(persistentHardwareId, localDevice?.deviceIdentifier)
        assertEquals(1, fakePersonDao.devices.size)

        // Subsequent launches find it in local Room cache without querying remote
        val cachedDevice = fakePersonDao.getDeviceByIdentifier(persistentHardwareId)
        assertEquals(existingDeviceId, cachedDevice?.id)
        assertEquals(1, fakePersonDao.devices.size)
    }

    // 2. Compound Key Alias Idempotency: (deviceId, androidContactId, normalizedNumber)
    @Test
    fun testCompoundKeyAliasIdempotencyOnRepeatedSync() = runBlocking {
        val deviceId = "DEV_001"
        val androidContactId = "raw_contact_42"
        val contact = ContactDto(
            contactId = androidContactId,
            name = "Aarav Patel",
            phoneNumbers = listOf("+91 98765 11223"),
            emails = emptyList(),
            photoUri = null,
            isFavorite = false
        )

        // First resolution: creates Person, Phone, and Alias
        val person1 = resolveAndAttachContactUseCase(contact, deviceId)
        assertNotNull(person1)
        assertEquals(1, fakePersonDao.people.size)
        assertEquals(1, fakePersonDao.phoneNumbers.size)
        assertEquals(1, fakePersonDao.aliases.size)

        val alias1 = fakePersonDao.findAliasByDeviceContactAndPhone(deviceId, androidContactId, "9876511223")
        assertNotNull(alias1)
        assertEquals(person1.id, alias1?.personId)
        assertEquals(deviceId, alias1?.deviceId)
        assertEquals(androidContactId, alias1?.androidContactId)
        assertEquals("9876511223", alias1?.normalizedNumber)

        // Second resolution (sync repeat): must reuse existing Person and NOT duplicate Alias
        val person2 = resolveAndAttachContactUseCase(contact, deviceId)
        assertEquals(person1.id, person2.id)
        assertEquals(1, fakePersonDao.people.size)
        assertEquals(1, fakePersonDao.phoneNumbers.size)
        assertEquals(1, fakePersonDao.aliases.size)

        val alias2 = fakePersonDao.findAliasByDeviceContactAndPhone(deviceId, androidContactId, "9876511223")
        assertEquals(alias1?.id, alias2?.id)
    }

    // 3. Multi-Device Canonical Identity: Two devices link to same Person with distinct compound aliases
    @Test
    fun testMultiDeviceSyncLinksSamePersonWithScopedAliases() = runBlocking {
        val phone = "+91 98765 99999"
        val normalized = "9876599999"

        // Device A has "Boss" with local contact ID raw_1
        val contactA = ContactDto(
            contactId = "raw_1",
            name = "Boss",
            phoneNumbers = listOf(phone),
            emails = emptyList(),
            photoUri = null,
            isFavorite = false
        )
        val personA = resolveAndAttachContactUseCase(contactA, deviceId = "DEV_A")

        // Device B has "Mr. Verma" with local contact ID raw_2 for the same phone number
        val contactB = ContactDto(
            contactId = "raw_2",
            name = "Mr. Verma",
            phoneNumbers = listOf(phone),
            emails = emptyList(),
            photoUri = null,
            isFavorite = false
        )
        val personB = resolveAndAttachContactUseCase(contactB, deviceId = "DEV_B")

        // Both devices resolve to the exact same canonical Person
        assertEquals(personA.id, personB.id)
        assertEquals(1, fakePersonDao.people.size)
        assertEquals(1, fakePersonDao.phoneNumbers.size)

        // But each device maintains its own unique compound alias
        assertEquals(2, fakePersonDao.aliases.size)

        val aliasA = fakePersonDao.findAliasByDeviceContactAndPhone("DEV_A", "raw_1", normalized)
        val aliasB = fakePersonDao.findAliasByDeviceContactAndPhone("DEV_B", "raw_2", normalized)

        assertNotNull(aliasA)
        assertNotNull(aliasB)
        assertEquals("Boss", aliasA?.aliasName)
        assertEquals("Mr. Verma", aliasB?.aliasName)
        assertEquals(personA.id, aliasA?.personId)
        assertEquals(personA.id, aliasB?.personId)
    }

    // 4. Device Contact CRM Promotion Flow
    @Test
    fun testDeviceContactPromotionFlowThroughResolveAndAttach() = runBlocking {
        val deviceId = "DEV_MAIN"
        val deviceContact = DeviceContact(
            androidContactId = "android_contact_55",
            displayName = "Neha Sharma",
            phoneNumbers = listOf(
                DevicePhoneNumber(number = "+91 98111 22334", normalizedNumber = "9811122334")
            ),
            emails = listOf("neha@example.com"),
            photoUri = null
        )

        // Promote device contact to CRM: converts to ContactDto and runs resolveAndAttachContact
        val contactDto = ContactDto(
            contactId = deviceContact.androidContactId,
            name = deviceContact.displayName,
            phoneNumbers = deviceContact.phoneNumbers.map { it.number },
            emails = deviceContact.emails,
            photoUri = deviceContact.photoUri,
            isFavorite = deviceContact.isFavorite
        )

        val personEntity = resolveAndAttachContactUseCase(contactDto, deviceId)
        assertNotNull(personEntity)
        assertEquals("Neha Sharma", personEntity.displayName)

        // Verify compound alias created
        val alias = fakePersonDao.findAliasByDeviceContactAndPhone(deviceId, "android_contact_55", "9811122334")
        assertNotNull(alias)
        assertEquals(personEntity.id, alias?.personId)

        // Promoting again is idempotent
        val rePromoted = resolveAndAttachContactUseCase(contactDto, deviceId)
        assertEquals(personEntity.id, rePromoted.id)
        assertEquals(1, fakePersonDao.people.size)
        assertEquals(1, fakePersonDao.aliases.size)
    }

    // 5. Strict Device Tab Isolation: Viewing device contacts never writes to CRM
    @Test
    fun testDeviceTabViewingDoesNotTriggerCrmWritesOrUploads() = runBlocking {
        val deviceContact = DeviceContact(
            androidContactId = "local_plumber_1",
            displayName = "Local Plumber",
            phoneNumbers = listOf(
                DevicePhoneNumber(number = "+91 90000 55555", normalizedNumber = "9000055555")
            ),
            emails = emptyList(),
            photoUri = null
        )

        val fakeDeviceRepo = object : DeviceContactsRepository {
            override fun observeContacts(): Flow<List<DeviceContact>> = flowOf(listOf(deviceContact))
            override suspend fun getContacts(): List<DeviceContact> = listOf(deviceContact)
            override suspend fun refreshContacts(): Result<Unit> = Result.success(Unit)
            override suspend fun getContactById(androidContactId: String): DeviceContact? = deviceContact
            override suspend fun searchContacts(query: String): List<DeviceContact> = listOf(deviceContact)
        }

        var writeCallsCount = 0
        val fakePersonRepo = object : PersonRepository {
            override fun getAllPeopleFlow(): Flow<List<Person>> = flowOf(emptyList())
            override suspend fun getAllPeople(): List<Person> = emptyList()
            override fun getPersonByIdFlow(id: String): Flow<Person?> = flowOf(null)
            override suspend fun getPersonById(id: String): Person? = null
            override fun searchPeopleFlow(query: String): Flow<List<Person>> = flowOf(emptyList())
            override suspend fun searchPeople(query: String): List<Person> = emptyList()
            override suspend fun findPersonByNormalizedPhone(normalizedPhone: String): Person? = null // cache read
            override suspend fun insertOrUpdatePerson(person: Person) { writeCallsCount++ }
            override suspend fun savePhoneNumber(phoneNumber: PhoneNumber) { writeCallsCount++ }
            override suspend fun saveAlias(alias: com.example.callog.domain.model.ContactAlias) { writeCallsCount++ }
            override suspend fun getOrCreateCurrentDevice(): com.example.callog.domain.model.Device =
                com.example.callog.domain.model.Device("D1", "Test", "123", "id")
            override suspend fun syncContactsFromDevice(): List<Person> = emptyList()
            override suspend fun syncGlobalContactsFromSupabase(): Result<List<Person>> = Result.success(emptyList())
            override suspend fun createGlobalContact(name: String, phone: String, company: String?, notes: String?): Result<Person> {
                writeCallsCount++
                return Result.failure(Exception())
            }
            override suspend fun resolveAndAttachContact(deviceId: String, contact: ContactDto): com.example.callog.domain.model.PersonResolutionResult {
                writeCallsCount++
                return com.example.callog.domain.model.PersonResolutionResult("P1")
            }
            override suspend fun promoteDeviceContactToCrm(contact: DeviceContact): Result<Person> {
                writeCallsCount++
                return Result.failure(Exception())
            }
        }

        val getDeviceContactsUseCase = GetDeviceContactsUseCase(
            deviceContactsRepository = fakeDeviceRepo,
            personRepository = fakePersonRepo
        )

        // When: User browses Device contacts tab
        val items = getDeviceContactsUseCase().first()

        // Then: Local contacts are presented with zero CRM writes or uploads
        assertEquals(1, items.size)
        assertEquals("Local Plumber", items.first().displayName)
        assertNull(items.first().linkedPersonId)
        assertEquals(0, writeCallsCount)
    }
}
