package com.example.callog.contacts

import com.example.callog.domain.model.*
import com.example.callog.domain.repository.DeviceContactsRepository
import com.example.callog.domain.repository.PersonRepository
import com.example.callog.domain.usecase.GetCloudContactsUseCase
import com.example.callog.domain.usecase.GetDeviceContactsUseCase
import com.example.callog.domain.usecase.RefreshDeviceContactsUseCase
import com.example.callog.presentation.navigation3.Nav3Key
import com.example.callog.presentation.viewmodel.ContactsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsTwoTabDirectoryTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakePersonRepo: FakePersonRepository
    private lateinit var fakeDeviceContactsRepo: FakeDeviceContactsRepository
    private lateinit var getCloudContactsUseCase: GetCloudContactsUseCase
    private lateinit var getDeviceContactsUseCase: GetDeviceContactsUseCase
    private lateinit var refreshDeviceContactsUseCase: RefreshDeviceContactsUseCase
    private lateinit var viewModel: ContactsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakePersonRepo = FakePersonRepository()
        fakeDeviceContactsRepo = FakeDeviceContactsRepository()
        getCloudContactsUseCase = GetCloudContactsUseCase(fakePersonRepo)
        getDeviceContactsUseCase = GetDeviceContactsUseCase(fakeDeviceContactsRepo, fakePersonRepo)
        refreshDeviceContactsUseCase = RefreshDeviceContactsUseCase(fakeDeviceContactsRepo)

        // Seed Cloud Contacts (Supabase / Room)
        fakePersonRepo.peopleList.add(
            Person(
                id = "P001",
                displayName = "Rahul Sharma",
                companyName = "ABC Industries",
                phoneNumbers = listOf(
                    PhoneNumber(
                        id = "PN001",
                        personId = "P001",
                        phoneNumber = "+91 98765 43210",
                        normalizedNumber = "9876543210",
                        isPrimary = true
                    )
                ),
                aliases = listOf(
                    ContactAlias(
                        id = "A001",
                        personId = "P001",
                        deviceId = "DEV_1",
                        androidContactId = "c_1",
                        aliasName = "Rahul Client",
                        phoneNumber = "+91 98765 43210",
                        normalizedNumber = "9876543210"
                    )
                )
            )
        )
        fakePersonRepo.peopleList.add(
            Person(
                id = "P002",
                displayName = "Priya Patel",
                companyName = "XYZ Ltd",
                phoneNumbers = listOf(
                    PhoneNumber(
                        id = "PN002",
                        personId = "P002",
                        phoneNumber = "+91 98200 12345",
                        normalizedNumber = "9820012345",
                        isPrimary = true
                    )
                )
            )
        )

        // Seed Device Contacts (ContactsContract)
        // 1. Matching Rahul's number (links to P001)
        fakeDeviceContactsRepo.deviceContactsList.add(
            DeviceContact(
                androidContactId = "dev_contact_101",
                displayName = "Rahul Personal",
                phoneNumbers = listOf(
                    DevicePhoneNumber(
                        number = "+91 98765 43210",
                        normalizedNumber = "9876543210"
                    )
                ),
                photoUri = "content://contacts/photos/101"
            )
        )
        // 2. Unlinked local device contact
        fakeDeviceContactsRepo.deviceContactsList.add(
            DeviceContact(
                androidContactId = "dev_contact_102",
                displayName = "Local Plumber",
                phoneNumbers = listOf(
                    DevicePhoneNumber(
                        number = "+91 91111 22222",
                        normalizedNumber = "9111122222"
                    )
                ),
                photoUri = null
            )
        )

        viewModel = ContactsViewModel(
            personRepository = fakePersonRepo,
            deviceContactsRepository = fakeDeviceContactsRepo,
            getCloudContactsUseCase = getCloudContactsUseCase,
            getDeviceContactsUseCase = getDeviceContactsUseCase,
            refreshDeviceContactsUseCase = refreshDeviceContactsUseCase,
            sharingStarted = SharingStarted.Eagerly
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testCloudTabOnlyShowsCloudContactsAndNeverFallsBackToDevice() = runTest(testDispatcher) {
        viewModel.selectSource(ContactSource.CLOUD)
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals(ContactSource.CLOUD, state.source)
        assertEquals(2, state.contacts.size)
        assertTrue(state.contacts.all { it is ContactDirectoryItem.Cloud })

        val names = state.contacts.map { it.displayName }
        assertTrue(names.contains("Rahul Sharma"))
        assertTrue(names.contains("Priya Patel"))
        assertFalse("Device contact must not be in Cloud tab", names.contains("Local Plumber"))
        assertFalse("Device contact must not be in Cloud tab", names.contains("Rahul Personal"))
    }

    @Test
    fun testDeviceTabOnlyShowsDeviceContactsAndNeverShowsSupabaseOnly() = runTest(testDispatcher) {
        viewModel.selectSource(ContactSource.DEVICE)
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals(ContactSource.DEVICE, state.source)
        assertEquals(2, state.contacts.size)
        assertTrue(state.contacts.all { it is ContactDirectoryItem.Device })

        val names = state.contacts.map { it.displayName }
        assertTrue(names.contains("Rahul Personal"))
        assertTrue(names.contains("Local Plumber"))
        assertFalse("Supabase-only contact Priya Patel must not be in Device tab", names.contains("Priya Patel"))
    }

    @Test
    fun testCloudEmptyListNeverFallsBackToDevice() = runTest(testDispatcher) {
        fakePersonRepo.peopleList.clear()
        val emptyCloudFlow = getCloudContactsUseCase().first()
        assertTrue("Cloud flow must be completely empty when no people are cached", emptyCloudFlow.isEmpty())
        assertFalse("Cloud flow must not fall back to device contacts", emptyCloudFlow.any { it.displayName == "Local Plumber" })
    }

    @Test
    fun testCloudSearchIsIsolatedToCloudProperties() = runTest(testDispatcher) {
        viewModel.selectSource(ContactSource.CLOUD)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Search by company name in Cloud tab
        viewModel.onSearchQueryChanged("XYZ")
        testDispatcher.scheduler.advanceUntilIdle()
        var state = viewModel.uiState.value
        assertEquals(1, state.contacts.size)
        assertEquals("Priya Patel", state.contacts.first().displayName)

        // Search by alias name in Cloud tab
        viewModel.onSearchQueryChanged("Rahul Client")
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals(1, state.contacts.size)
        assertEquals("Rahul Sharma", state.contacts.first().displayName)

        // Search for device-only contact while on Cloud tab must yield empty
        viewModel.onSearchQueryChanged("Plumber")
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals(0, state.contacts.size)
    }

    @Test
    fun testDeviceSearchIsIsolatedToDeviceProperties() = runTest(testDispatcher) {
        viewModel.selectSource(ContactSource.DEVICE)
        testDispatcher.scheduler.advanceUntilIdle()

        // Search for Plumber on Device tab
        viewModel.onSearchQueryChanged("Plumber")
        testDispatcher.scheduler.advanceUntilIdle()
        var state = viewModel.uiState.value
        assertEquals(1, state.contacts.size)
        assertEquals("Local Plumber", state.contacts.first().displayName)

        // Search for Cloud-only company name on Device tab must yield empty
        viewModel.onSearchQueryChanged("ABC Industries")
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals(0, state.contacts.size)
    }

    @Test
    fun testDeviceContactEnrichedWithLinkedCrmPerson() = runTest(testDispatcher) {
        val deviceItems = getDeviceContactsUseCase().first()
        val rahulDevice = deviceItems.find { it.contactId == "dev_contact_101" }
        assertNotNull(rahulDevice)
        assertEquals("P001", rahulDevice?.linkedPersonId)
        assertEquals("Rahul Sharma", rahulDevice?.linkedPersonName)

        val plumberDevice = deviceItems.find { it.contactId == "dev_contact_102" }
        assertNotNull(plumberDevice)
        assertNull("Unlinked number must have null linkedPersonId", plumberDevice?.linkedPersonId)
        assertNull(plumberDevice?.linkedPersonName)
    }

    @Test
    fun testPermissionDeniedHidesDeviceContacts() = runTest(testDispatcher) {
        viewModel.selectSource(ContactSource.DEVICE)
        viewModel.setContactsPermission(false)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasContactsPermission)
        assertTrue(state.contacts.isEmpty())
    }

    @Test
    fun testNavigationKeysSeparation() {
        val cloudKey = Nav3Key.Contacts.ContactDetails(contactId = "P001")
        val deviceKey = Nav3Key.Contacts.DeviceContactDetails(androidContactId = "dev_contact_101")

        assertEquals("P001", cloudKey.contactId)
        assertEquals("dev_contact_101", deviceKey.androidContactId)
        assertNotEquals(cloudKey, deviceKey)
    }
}

// ==========================================
// Test Doubles
// ==========================================

class FakePersonRepository : PersonRepository {
    val peopleList = mutableListOf<Person>()

    override fun getAllPeopleFlow(): Flow<List<Person>> = flowOf(peopleList)

    override suspend fun getAllPeople(): List<Person> = peopleList

    override fun getPersonByIdFlow(id: String): Flow<Person?> = flowOf(peopleList.find { it.id == id })

    override suspend fun getPersonById(id: String): Person? = peopleList.find { it.id == id }

    override fun searchPeopleFlow(query: String): Flow<List<Person>> = flowOf(
        peopleList.filter { it.displayName.contains(query, ignoreCase = true) }
    )

    override suspend fun searchPeople(query: String): List<Person> =
        peopleList.filter { it.displayName.contains(query, ignoreCase = true) }

    override suspend fun findPersonByNormalizedPhone(normalizedPhone: String): Person? {
        return peopleList.find { person ->
            person.phoneNumbers.any { it.normalizedNumber == normalizedPhone } ||
            person.aliases.any { it.normalizedNumber == normalizedPhone }
        }
    }

    override suspend fun insertOrUpdatePerson(person: Person) {
        peopleList.removeAll { it.id == person.id }
        peopleList.add(person)
    }

    override suspend fun savePhoneNumber(phoneNumber: PhoneNumber) {}

    override suspend fun saveAlias(alias: ContactAlias) {}

    override suspend fun getOrCreateCurrentDevice(): Device = Device("DEV_1", "Test", "123", "id")

    override suspend fun syncContactsFromDevice(): List<Person> = peopleList

    override suspend fun syncGlobalContactsFromSupabase(): Result<List<Person>> = Result.success(peopleList)

    override suspend fun createGlobalContact(name: String, phone: String, company: String?, notes: String?): Result<Person> {
        val p = Person(id = "P_${System.currentTimeMillis()}", displayName = name, companyName = company, notes = notes)
        peopleList.add(p)
        return Result.success(p)
    }

    override suspend fun resolveAndAttachContact(deviceId: String, contact: com.example.callog.data.provider.ContactDto): com.example.callog.domain.model.PersonResolutionResult {
        return com.example.callog.domain.model.PersonResolutionResult(personId = "P001")
    }
}

class FakeDeviceContactsRepository : DeviceContactsRepository {
    val deviceContactsList = mutableListOf<DeviceContact>()

    override fun observeContacts(): Flow<List<DeviceContact>> = flowOf(deviceContactsList)

    override suspend fun getContacts(): List<DeviceContact> = deviceContactsList

    override suspend fun refreshContacts(): Result<Unit> = Result.success(Unit)

    override suspend fun getContactById(androidContactId: String): DeviceContact? =
        deviceContactsList.find { it.androidContactId == androidContactId }

    override suspend fun searchContacts(query: String): List<DeviceContact> =
        deviceContactsList.filter { it.displayName.contains(query, ignoreCase = true) }
}
