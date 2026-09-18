package com.example.callog.telecom

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.example.callog.core.telecom.CallAudioController
import com.example.callog.core.telecom.ProximityController
import com.example.callog.core.telecom.RingtoneController
import com.example.callog.data.local.dao.CallDao
import com.example.callog.data.local.dao.ReminderWithCall
import com.example.callog.data.local.dao.SalesCallDao
import com.example.callog.data.local.entity.CallEntity
import com.example.callog.data.local.entity.ReminderEntity
import com.example.callog.data.local.entity.SalesCallEntity
import com.example.callog.data.provider.ContactDto
import com.example.callog.data.telecom.CallNotificationManager
import com.example.callog.data.telecom.TelecomCallController
import com.example.callog.domain.call.*
import com.example.callog.domain.model.*
import com.example.callog.domain.repository.CallRepository
import com.example.callog.domain.repository.LeadRepository
import com.example.callog.domain.repository.PersonRepository
import com.example.callog.domain.service.CallSessionManager
import com.example.callog.domain.service.DialerRoleManager
import com.example.callog.domain.service.simulator.CallSimulatorManager
import com.example.callog.presentation.call.CallSessionViewModel
import com.example.callog.presentation.call.CallUiState
import com.example.callog.sim.SimInfo
import com.example.callog.sim.SimManager
import com.example.callog.sim.SimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class CallSessionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var callSessionManager: CallSessionManager
    private lateinit var fakePersonRepository: FakePersonRepo
    private lateinit var fakeLeadRepository: FakeLeadRepo
    private lateinit var fakeCallRepository: FakeCallRepo
    private lateinit var fakeSimRepository: FakeSimRepo
    private lateinit var fakeRingtoneController: FakeRingtoneCtrl
    private lateinit var fakeAudioController: CallAudioController
    private lateinit var fakeProximityController: ProximityController
    private lateinit var fakeNotificationManager: FakeCallNotificationMgr
    private lateinit var fakeController: FakeTelecomCallCtrl
    private lateinit var fakeSimulatorManager: CallSimulatorManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        val fakeContext = object : ContextWrapper(null) {
            override fun getPackageName(): String = "com.example.callog"
            override fun getApplicationContext(): Context = this
            override fun startActivity(intent: Intent?) {}
            override fun getSystemService(name: String): Any? = null
        }

        fakePersonRepository = FakePersonRepo()
        fakeLeadRepository = FakeLeadRepo()
        fakeCallRepository = FakeCallRepo()
        fakeSimRepository = FakeSimRepo(fakeContext)
        fakeRingtoneController = FakeRingtoneCtrl(fakeContext)
        fakeAudioController = CallAudioController(fakeContext)
        fakeProximityController = ProximityController(fakeContext)
        fakeNotificationManager = FakeCallNotificationMgr(fakeContext)
        fakeController = FakeTelecomCallCtrl()
        val fakeHapticManager = com.example.callog.core.telecom.CallHapticManager(fakeContext)
        val fakeDtmfTonePlayer = com.example.callog.core.telecom.DtmfTonePlayer()

        callSessionManager = CallSessionManager(
            context = fakeContext,
            personRepository = fakePersonRepository,
            leadRepository = fakeLeadRepository,
            callRepository = fakeCallRepository,
            simRepository = fakeSimRepository,
            ringtoneController = fakeRingtoneController,
            callAudioController = fakeAudioController,
            proximityController = fakeProximityController,
            notificationManager = fakeNotificationManager,
            callHapticManager = fakeHapticManager,
            dtmfTonePlayer = fakeDtmfTonePlayer,
            contactsProvider = com.example.callog.data.provider.ContactsProvider(fakeContext),
            dialerRoleManager = DialerRoleManager(fakeContext)
        )

        callSessionManager.registerTelecomController(fakeController)

        val fakeCallDao = Proxy.newProxyInstance(
            CallDao::class.java.classLoader,
            arrayOf(CallDao::class.java)
        ) { _, _, _ -> null } as CallDao

        val fakeSalesCallDao = Proxy.newProxyInstance(
            SalesCallDao::class.java.classLoader,
            arrayOf(SalesCallDao::class.java)
        ) { _, _, _ -> null } as SalesCallDao

        fakeSimulatorManager = CallSimulatorManager(
            context = fakeContext,
            callSessionManager = callSessionManager,
            personRepository = fakePersonRepository,
            simRepository = fakeSimRepository,
            callDao = fakeCallDao,
            salesCallDao = fakeSalesCallDao
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): CallSessionViewModel {
        return CallSessionViewModel(
            callSessionManager = callSessionManager,
            leadRepository = fakeLeadRepository,
            callRepository = fakeCallRepository,
            callSimulatorManager = fakeSimulatorManager
        )
    }

    @Test
    fun testMapToUiStatePureFunction() {
        // 1. Null active session -> NoSession
        val noSession = CallSessionViewModel.mapToUiState(null, emptyList())
        assertEquals(CallUiState.NoSession, noSession)

        // 2. CONNECTING session -> Connecting
        val connectingSession = CallSessionState(
            callId = "c1",
            phoneNumber = "12345",
            direction = CallDirection.OUTGOING,
            state = CallState.CONNECTING
        )
        val connectingUi = CallSessionViewModel.mapToUiState(connectingSession, listOf(connectingSession))
        assertTrue(connectingUi is CallUiState.Connecting)

        // 3. ACTIVE session -> Active
        val activeSession = connectingSession.copy(state = CallState.ACTIVE)
        val activeUi = CallSessionViewModel.mapToUiState(activeSession, listOf(activeSession))
        assertTrue(activeUi is CallUiState.Active)

        // 4. RINGING session -> Incoming
        val ringingSession = connectingSession.copy(state = CallState.RINGING, direction = CallDirection.INCOMING)
        val ringingUi = CallSessionViewModel.mapToUiState(ringingSession, listOf(ringingSession))
        assertTrue(ringingUi is CallUiState.Incoming)

        // 5. DISCONNECTED session -> Ended
        val endedSession = connectingSession.copy(state = CallState.DISCONNECTED)
        val endedUi = CallSessionViewModel.mapToUiState(endedSession, listOf(endedSession))
        assertTrue(endedUi is CallUiState.Ended)
    }

    @Test
    fun testInitialStateWithNoSessionIsNoSession() {
        val viewModel = createViewModel()
        assertEquals(CallUiState.NoSession, viewModel.uiState.value)
    }

    @Test
    fun testInitialStateWithConnectingOutgoingCallIsConnecting() {
        // Pre-seed an outgoing call session before ViewModel creation
        callSessionManager.startOutgoingCall("+919876543210", simSlot = 0)

        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertTrue("Expected CallUiState.Connecting but got $state", state is CallUiState.Connecting)
        val connectingState = state as CallUiState.Connecting
        assertEquals("+919876543210", connectingState.session.phoneNumber)
        assertEquals(CallState.CONNECTING, connectingState.session.state)
    }

    @Test
    fun testInitialStateWithRingingCallIsIncoming() {
        callSessionManager.onTelecomCallAdded(
            callId = "call_1",
            rawNumber = "+919876543210",
            telecomDisplayName = "Alice",
            direction = CallDirection.INCOMING,
            initialState = CallState.RINGING,
            capabilities = CallCapabilities(canMute = true),
            accountHandleId = null,
            accountComponentName = null
        )

        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertTrue("Expected CallUiState.Incoming but got $state", state is CallUiState.Incoming)
        val incoming = state as CallUiState.Incoming
        assertEquals("call_1", incoming.session.callId)
        assertEquals(CallState.RINGING, incoming.session.state)
    }

    @Test
    fun testTransitionConnectingToActiveWithoutLosingSession() = runTest {
        callSessionManager.startOutgoingCall("+919876543210", simSlot = 0)
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        assertTrue(viewModel.uiState.value is CallUiState.Connecting)

        val activeCallId = callSessionManager.activeCallSession.value?.callId ?: ""
        assertTrue(activeCallId.isNotBlank())

        // Simulate transition to ACTIVE
        callSessionManager.onTelecomCallStateChanged(activeCallId, CallState.ACTIVE)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected CallUiState.Active but got $state", state is CallUiState.Active)
        val activeState = state as CallUiState.Active
        assertEquals(CallState.ACTIVE, activeState.session.state)
        assertEquals(activeCallId, activeState.session.callId)
    }

    @Test
    fun testEndCallDispatchesDisconnect() = runTest {
        callSessionManager.startOutgoingCall("+919876543210", simSlot = 0)
        val viewModel = createViewModel()
        val activeCallId = callSessionManager.activeCallSession.value?.callId ?: ""

        viewModel.endCall()
        testScheduler.advanceUntilIdle()

        assertEquals(activeCallId, fakeController.lastDisconnectedCallId)
    }

    // ── Local Fakes ────────────────────────────────────────────────────────────

    class FakePersonRepo : PersonRepository {
        val people = mutableMapOf<String, Person>()
        val peopleById = mutableMapOf<String, Person>()

        override suspend fun findPersonByNormalizedPhone(normalizedPhone: String): Person? = people[normalizedPhone]
        override suspend fun getPersonById(id: String): Person? = peopleById[id]
        override fun getPersonByIdFlow(id: String): Flow<Person?> = flowOf(peopleById[id])
        override fun getAllPeopleFlow(): Flow<List<Person>> = flowOf(peopleById.values.toList())
        override suspend fun getAllPeople(): List<Person> = peopleById.values.toList()
        override fun searchPeopleFlow(query: String): Flow<List<Person>> = flowOf(emptyList())
        override suspend fun searchPeople(query: String): List<Person> = emptyList()
        override suspend fun insertOrUpdatePerson(person: Person) { peopleById[person.id] = person }
        override suspend fun savePhoneNumber(phoneNumber: PhoneNumber) {}
        override suspend fun saveAlias(alias: ContactAlias) {}
        override suspend fun getOrCreateCurrentDevice(): Device = Device("d1", "Test", "0", "id")
        override suspend fun syncContactsFromDevice(): List<Person> = emptyList()
        override suspend fun resolveAndAttachContact(deviceId: String, contact: ContactDto): PersonResolutionResult =
            PersonResolutionResult("p1")
        override suspend fun syncGlobalContactsFromSupabase(): Result<List<Person>> = Result.success(emptyList())
        override suspend fun createGlobalContact(name: String, phone: String, company: String?, notes: String?): Result<Person> =
            Result.success(Person(id = "p1", displayName = name, companyName = company, notes = notes, phoneNumbers = listOf(PhoneNumber("pn1", "p1", phone, phone))))
    }

    class FakeLeadRepo : LeadRepository {
        val leads = mutableMapOf<String, Lead>()

        override fun getLeadForPersonFlow(personId: String): Flow<Lead?> = flowOf(leads[personId])
        override suspend fun getLeadForPerson(personId: String): Lead? = leads[personId]
        override suspend fun getLeadById(id: String): Lead? = leads.values.find { it.id == id }
        override fun getAllLeadsFlow(): Flow<List<Lead>> = flowOf(leads.values.toList())
        override suspend fun getAllLeads(): List<Lead> = leads.values.toList()
        override suspend fun saveLead(lead: Lead) { leads[lead.personId] = lead }
        override suspend fun getOrCreateLeadForPerson(personId: String): Lead =
            leads.getOrPut(personId) { Lead("L_$personId", personId) }
        override suspend fun updateLeadStatus(personId: String, status: LeadStatus) {}
        override suspend fun updateLeadPriority(personId: String, priority: LeadPriority) {}
        override suspend fun updateLeadNotes(personId: String, notes: String) {}
        override suspend fun updateLeadFeedback(personId: String, feedback: String, rating: Int?) {}
        override suspend fun updateLeadFollowUp(personId: String, nextFollowUpAt: Long?) {}
        override suspend fun archiveLead(personId: String) {}
    }

    class FakeCallRepo : CallRepository {
        override fun getCallLogsFlow(): Flow<List<CallLogEntry>> = flowOf(emptyList())
        override fun getFavoriteLogsFlow(): Flow<List<CallLogEntry>> = flowOf(emptyList())
        override fun getRecordingLogsFlow(): Flow<List<CallLogEntry>> = flowOf(emptyList())
        override fun getCallLogByIdFlow(id: Long): Flow<CallLogEntry?> = flowOf(null)
        override fun getCallsForPersonFlow(personId: String): Flow<List<CallLogEntry>> = flowOf(emptyList())
        override suspend fun getCallsForPerson(personId: String): List<CallLogEntry> = emptyList()
        override suspend fun syncCallLogs() {}
        override suspend fun getAllCallsSnapshot(): List<CallEntity> = emptyList()
        override suspend fun getContacts(): List<ContactDto> = emptyList()
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

    class FakeSimRepo(context: Context) : SimRepository(SimManager(context)) {
        val sims = mutableListOf<SimInfo>()
        override fun getInstalledSims(): List<SimInfo> = sims
        override fun resolveSubscriptionId(phoneAccountId: String?, componentName: String?): Int {
            return phoneAccountId?.toIntOrNull() ?: -1
        }
    }

    class FakeRingtoneCtrl(context: Context) : RingtoneController(context, object : com.example.callog.domain.repository.RingtonePolicy {
        override fun ringtoneFor(person: Person?, lead: Lead?): android.net.Uri = android.net.Uri.EMPTY
    }) {
        override fun startRingtone(person: Person?, lead: Lead?) {}
        override fun stopRingtone() {}
        override fun silenceRinger() {}
    }

    class FakeCallNotificationMgr(context: Context) : CallNotificationManager(context) {
        override fun showIncomingCallNotification(session: CallSessionState) {}
        override fun showActiveCallNotification(session: CallSessionState) {}
        override fun showMissedCallNotification(session: CallSessionState) {}
        override fun cancelCallNotification() {}
    }

    class FakeTelecomCallCtrl : TelecomCallController {
        var lastAnsweredCallId: String? = null
        var lastRejectedCallId: String? = null
        var lastDisconnectedCallId: String? = null

        override fun answerCall(callId: String) { lastAnsweredCallId = callId }
        override fun rejectCall(callId: String) { lastRejectedCallId = callId }
        override fun rejectCallWithMessage(callId: String, textMessage: String) {}
        override fun disconnectCall(callId: String) { lastDisconnectedCallId = callId }
        override fun setCallMuted(shouldMute: Boolean) {}
        override fun setAudioRoute(route: AudioRoute) {}
        override fun holdCall(callId: String) {}
        override fun unholdCall(callId: String) {}
        override fun swapCalls() {}
        override fun mergeCalls(callId1: String, callId2: String) {}
        override fun playDtmfTone(callId: String, digit: Char) {}
        override fun stopDtmfTone(callId: String) {}
    }
}
