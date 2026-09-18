package com.example.callog.contacts

import com.example.callog.data.local.dao.ReminderWithCall
import com.example.callog.data.local.entity.CallEntity
import com.example.callog.data.local.entity.ReminderEntity
import com.example.callog.data.provider.ContactDto
import com.example.callog.domain.model.*
import com.example.callog.domain.repository.CallRepository
import com.example.callog.domain.repository.LeadRepository
import com.example.callog.presentation.viewmodel.ContactDetailUiState
import com.example.callog.presentation.viewmodel.ContactDetailsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
class ContactDetailsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakePersonRepo: FakePersonRepository
    private lateinit var fakeLeadRepo: FakeLeadRepository
    private lateinit var fakeCallRepo: FakeCallRepository
    private lateinit var viewModel: ContactDetailsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakePersonRepo = FakePersonRepository()
        fakeLeadRepo = FakeLeadRepository()
        fakeCallRepo = FakeCallRepository()

        // Seed a person
        fakePersonRepo.peopleList.add(
            Person(
                id = "P001",
                displayName = "Rahul Sharma",
                companyName = "ABC Corp",
                phoneNumbers = listOf(
                    PhoneNumber(
                        id = "PN001",
                        personId = "P001",
                        phoneNumber = "+91 98765 43210",
                        normalizedNumber = "9876543210",
                        isPrimary = true
                    )
                )
            )
        )

        viewModel = ContactDetailsViewModel(
            callRepository = fakeCallRepo,
            personRepository = fakePersonRepo,
            leadRepository = fakeLeadRepo
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testLoadContactAndPersistLeadStatus() = runTest(testDispatcher) {
        viewModel.loadContact("P001")
        testDispatcher.scheduler.advanceUntilIdle()

        var state = viewModel.uiState.value
        assertTrue("State must be Success", state is ContactDetailUiState.Success)
        val success = state as ContactDetailUiState.Success
        assertEquals("Rahul Sharma", success.contact.name)
        assertNotNull(success.lead)

        // Update status to HOT
        viewModel.updateLeadStatus(LeadStatus.HOT)
        testDispatcher.scheduler.advanceUntilIdle()

        state = viewModel.uiState.value
        assertTrue(state is ContactDetailUiState.Success)
        val updatedSuccess = state as ContactDetailUiState.Success
        assertEquals(LeadStatus.HOT, updatedSuccess.lead?.status)

        // Verify in repository
        val repoLead = fakeLeadRepo.getLeadForPerson("P001")
        assertEquals(LeadStatus.HOT, repoLead?.status)
    }

    @Test
    fun testLoadContactByPhoneNumberAndPersistStatus() = runTest(testDispatcher) {
        // Load contact using phone number directly
        viewModel.loadContact("+91 98765 43210")
        testDispatcher.scheduler.advanceUntilIdle()

        var state = viewModel.uiState.value
        assertTrue("State must be Success for phone lookup", state is ContactDetailUiState.Success)
        val success = state as ContactDetailUiState.Success
        assertEquals("P001", success.person?.id)

        // Update status to CUSTOMER
        viewModel.updateLeadStatus(LeadStatus.CUSTOMER)
        testDispatcher.scheduler.advanceUntilIdle()

        state = viewModel.uiState.value
        val updatedSuccess = state as ContactDetailUiState.Success
        assertEquals(LeadStatus.CUSTOMER, updatedSuccess.lead?.status)
    }

    @Test
    fun testLoadNewContactCreatesPersonAndPersistsLeadStatus() = runTest(testDispatcher) {
        // Load completely new number not yet in database
        viewModel.loadContact("+91 99999 88888")
        testDispatcher.scheduler.advanceUntilIdle()

        var state = viewModel.uiState.value
        assertTrue("State must be Success for new contact", state is ContactDetailUiState.Success)
        val success = state as ContactDetailUiState.Success
        assertNotNull(success.person)

        // Update status to WARM and Priority to HIGH
        viewModel.updateLeadStatus(LeadStatus.WARM)
        viewModel.updateLeadPriority(LeadPriority.HIGH)
        viewModel.updateLeadNotes("Initial inquiry received via call")
        testDispatcher.scheduler.advanceUntilIdle()

        state = viewModel.uiState.value
        val updated = state as ContactDetailUiState.Success
        assertEquals(LeadStatus.WARM, updated.lead?.status)
        assertEquals(LeadPriority.HIGH, updated.lead?.priority)
        assertEquals("Initial inquiry received via call", updated.lead?.notes)
    }

    // --- Fake Test Repositories ---

    private class FakeLeadRepository : LeadRepository {
        val leads = mutableListOf<Lead>()
        private val flows = mutableMapOf<String, MutableStateFlow<Lead?>>()

        private fun getOrCreateFlow(personId: String): MutableStateFlow<Lead?> {
            return flows.getOrPut(personId) {
                MutableStateFlow(leads.find { it.personId == personId })
            }
        }

        override fun getLeadForPersonFlow(personId: String): Flow<Lead?> = getOrCreateFlow(personId).asStateFlow()

        override suspend fun getLeadForPerson(personId: String): Lead? = leads.find { it.personId == personId }

        override suspend fun getLeadById(id: String): Lead? = leads.find { it.id == id }

        override fun getAllLeadsFlow(): Flow<List<Lead>> = flowOf(leads)

        override suspend fun getAllLeads(): List<Lead> = leads

        override suspend fun saveLead(lead: Lead) {
            leads.removeAll { it.id == lead.id || it.personId == lead.personId }
            leads.add(lead)
            getOrCreateFlow(lead.personId).value = lead
        }

        override suspend fun getOrCreateLeadForPerson(personId: String): Lead {
            val existing = leads.find { it.personId == personId }
            if (existing != null) return existing
            val newLead = Lead(
                id = "LEAD_${personId}",
                personId = personId,
                status = LeadStatus.UNKNOWN,
                priority = LeadPriority.MEDIUM,
                source = LeadSource.MANUAL
            )
            leads.add(newLead)
            getOrCreateFlow(personId).value = newLead
            return newLead
        }

        override suspend fun updateLeadStatus(personId: String, status: LeadStatus) {
            val lead = getOrCreateLeadForPerson(personId)
            val updated = lead.copy(status = status, updatedAt = System.currentTimeMillis())
            saveLead(updated)
        }

        override suspend fun updateLeadPriority(personId: String, priority: LeadPriority) {
            val lead = getOrCreateLeadForPerson(personId)
            val updated = lead.copy(priority = priority, updatedAt = System.currentTimeMillis())
            saveLead(updated)
        }

        override suspend fun updateLeadNotes(personId: String, notes: String) {
            val lead = getOrCreateLeadForPerson(personId)
            val updated = lead.copy(notes = notes, updatedAt = System.currentTimeMillis())
            saveLead(updated)
        }

        override suspend fun updateLeadFeedback(personId: String, feedback: String, rating: Int?) {
            val lead = getOrCreateLeadForPerson(personId)
            val updated = lead.copy(feedback = feedback, feedbackRating = rating, updatedAt = System.currentTimeMillis())
            saveLead(updated)
        }

        override suspend fun updateLeadFollowUp(personId: String, nextFollowUpAt: Long?) {
            val lead = getOrCreateLeadForPerson(personId)
            val updated = lead.copy(nextFollowUpAt = nextFollowUpAt, updatedAt = System.currentTimeMillis())
            saveLead(updated)
        }

        override suspend fun archiveLead(personId: String) {
            val lead = getOrCreateLeadForPerson(personId)
            val updated = lead.copy(isArchived = true, archivedAt = System.currentTimeMillis())
            saveLead(updated)
        }
    }

    private class FakeCallRepository : CallRepository {
        val contactsList = mutableListOf<ContactDto>()

        override fun getCallLogsFlow(): Flow<List<CallLogEntry>> = flowOf(emptyList())
        override fun getFavoriteLogsFlow(): Flow<List<CallLogEntry>> = flowOf(emptyList())
        override fun getRecordingLogsFlow(): Flow<List<CallLogEntry>> = flowOf(emptyList())
        override fun getCallLogByIdFlow(id: Long): Flow<CallLogEntry?> = flowOf(null)
        override fun getCallsForPersonFlow(personId: String): Flow<List<CallLogEntry>> = flowOf(emptyList())
        override suspend fun getCallsForPerson(personId: String): List<CallLogEntry> = emptyList()

        override suspend fun syncCallLogs() {}
        override suspend fun getAllCallsSnapshot(): List<CallEntity> = emptyList()
        override suspend fun getContacts(): List<ContactDto> = contactsList

        override suspend fun updateNotes(callId: Long, notes: String?) {}
        override suspend fun updateTags(callId: Long, tags: List<String>) {}
        override suspend fun toggleFavorite(callId: Long) {}
        override suspend fun deleteCall(callId: Long) {}
        override suspend fun clearAllData() {}

        override fun getPendingRemindersFlow(): Flow<List<ReminderWithCall>> = flowOf(emptyList())
        override suspend fun addReminder(reminder: ReminderEntity): Long = 0L
        override suspend fun completeReminder(reminderId: Long) {}
        override suspend fun deleteReminder(reminderId: Long) {}
    }
}
