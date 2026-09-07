package com.example.callog.telecom

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.example.callog.data.local.dao.ReminderWithCall
import com.example.callog.data.local.entity.CallEntity
import com.example.callog.data.local.entity.ReminderEntity
import com.example.callog.data.provider.ContactDto
import com.example.callog.data.telecom.CallNotificationManager
import com.example.callog.data.telecom.CallRingtoneManager
import com.example.callog.data.telecom.TelecomCallController
import com.example.callog.domain.call.*
import com.example.callog.domain.model.*
import com.example.callog.domain.repository.CallRepository
import com.example.callog.domain.repository.LeadRepository
import com.example.callog.domain.repository.PersonRepository
import com.example.callog.domain.service.CallSessionManager
import com.example.callog.sim.SimInfo
import com.example.callog.sim.SimManager
import com.example.callog.sim.SimRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class CallSessionManagerTest {

    private lateinit var callSessionManager: CallSessionManager
    private lateinit var fakePersonRepository: FakePersonRepository
    private lateinit var fakeLeadRepository: FakeLeadRepository
    private lateinit var fakeCallRepository: FakeCallRepository
    private lateinit var fakeSimRepository: FakeSimRepository
    private lateinit var fakeRingtoneManager: FakeRingtoneManager
    private lateinit var fakeNotificationManager: FakeCallNotificationManager
    private lateinit var fakeController: FakeTelecomCallController

    @Before
    fun setUp() {
        val fakeContext = createFakeContext()
        fakePersonRepository = FakePersonRepository()
        fakeLeadRepository = FakeLeadRepository()
        fakeCallRepository = FakeCallRepository()
        fakeSimRepository = FakeSimRepository(fakeContext)
        fakeRingtoneManager = FakeRingtoneManager(fakeContext)
        fakeNotificationManager = FakeCallNotificationManager(fakeContext)
        fakeController = FakeTelecomCallController()

        callSessionManager = CallSessionManager(
            context = fakeContext,
            personRepository = fakePersonRepository,
            leadRepository = fakeLeadRepository,
            callRepository = fakeCallRepository,
            simRepository = fakeSimRepository,
            ringtoneManager = fakeRingtoneManager,
            notificationManager = fakeNotificationManager
        )

        callSessionManager.registerTelecomController(fakeController)
    }

    // ─────────────────────────────────────────────────────────────
    // 1. Incoming Call Event & Canonical Person Resolution
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testIncomingCallResolvesCanonicalPersonAndCrmMetadata() = runBlocking {
        // Seed canonical person with phone number +91 98765 43210 (norm: 9876543210)
        val person = Person(
            id = "P101",
            displayName = "Rahul Sharma",
            companyName = "Apex Solutions",
            phoneNumbers = listOf(
                PhoneNumber(
                    id = "PN1",
                    personId = "P101",
                    phoneNumber = "+91 98765 43210",
                    normalizedNumber = "9876543210"
                )
            )
        )
        fakePersonRepository.people["9876543210"] = person
        fakePersonRepository.peopleById["P101"] = person

        val lead = Lead(
            id = "L101",
            personId = "P101",
            status = LeadStatus.HOT,
            priority = LeadPriority.URGENT
        )
        fakeLeadRepository.leads["P101"] = lead

        // Simulate incoming call from Telecom
        callSessionManager.onTelecomCallAdded(
            callId = "call_1",
            rawNumber = "+91 98765 43210",
            telecomDisplayName = "Rahul",
            direction = CallDirection.INCOMING,
            initialState = CallState.RINGING,
            capabilities = CallCapabilities(canHold = true, canMute = true),
            accountHandleId = "1",
            accountComponentName = "telecom"
        )

        // Allow async resolution
        kotlinx.coroutines.delay(100)

        val active = callSessionManager.activeCallSession.value
        assertNotNull(active)
        assertEquals("call_1", active?.callId)
        assertEquals(CallState.RINGING, active?.state)
        assertEquals("P101", active?.personId)
        assertEquals("Rahul Sharma", active?.callerDisplayName)
        assertEquals("Apex Solutions", active?.companyName)
        assertEquals(LeadStatus.HOT, active?.crmStatus)
        assertEquals(LeadPriority.URGENT, active?.priority)

        // Verify Ringtone started for incoming ringing call
        assertTrue(fakeRingtoneManager.started)
    }

    // ─────────────────────────────────────────────────────────────
    // 2. Incoming -> Answered Transition
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testIncomingToAnsweredStateTransition() = runBlocking {
        callSessionManager.onTelecomCallAdded(
            callId = "call_2",
            rawNumber = "+91 98765 43210",
            telecomDisplayName = "Rahul",
            direction = CallDirection.INCOMING,
            initialState = CallState.RINGING,
            capabilities = CallCapabilities(canHold = true),
            accountHandleId = null,
            accountComponentName = null
        )

        kotlinx.coroutines.delay(100)
        assertTrue(fakeRingtoneManager.started)

        // User answers via UI
        callSessionManager.executeAction(CallAction.Answer("call_2"))
        assertEquals("call_2", fakeController.lastAnsweredCallId)

        // Telecom updates state to ACTIVE
        callSessionManager.onTelecomCallStateChanged("call_2", CallState.ACTIVE)
        kotlinx.coroutines.delay(100)

        val active = callSessionManager.activeCallSession.value
        assertEquals(CallState.ACTIVE, active?.state)

        // Verify ringtone stopped upon answer
        assertTrue(fakeRingtoneManager.stopped)
    }

    // ─────────────────────────────────────────────────────────────
    // 3. Incoming -> Rejected Transition
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testIncomingToRejectedStateTransition() = runBlocking {
        callSessionManager.onTelecomCallAdded(
            callId = "call_3",
            rawNumber = "+91 88888 77777",
            telecomDisplayName = "Spam Caller",
            direction = CallDirection.INCOMING,
            initialState = CallState.RINGING,
            capabilities = CallCapabilities(),
            accountHandleId = null,
            accountComponentName = null
        )

        kotlinx.coroutines.delay(100)
        assertTrue(fakeRingtoneManager.started)

        // User rejects call
        callSessionManager.executeAction(CallAction.Reject("call_3"))
        assertEquals("call_3", fakeController.lastRejectedCallId)

        // Telecom updates state to DISCONNECTED
        callSessionManager.onTelecomCallStateChanged("call_3", CallState.DISCONNECTED)
        kotlinx.coroutines.delay(100)

        // Verify ringtone stopped upon reject
        assertTrue(fakeRingtoneManager.stopped)
    }

    // ─────────────────────────────────────────────────────────────
    // 4. Active -> Disconnected Transition
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testActiveToDisconnectedStateTransition() = runBlocking {
        callSessionManager.onTelecomCallAdded(
            callId = "call_4",
            rawNumber = "+91 99999 00000",
            telecomDisplayName = "Client",
            direction = CallDirection.OUTGOING,
            initialState = CallState.ACTIVE,
            capabilities = CallCapabilities(canHold = true),
            accountHandleId = null,
            accountComponentName = null
        )

        kotlinx.coroutines.delay(100)
        val active = callSessionManager.activeCallSession.value
        assertEquals(CallState.ACTIVE, active?.state)

        // User ends call
        callSessionManager.executeAction(CallAction.Disconnect("call_4"))
        assertEquals("call_4", fakeController.lastDisconnectedCallId)

        // Telecom reports disconnected
        callSessionManager.onTelecomCallStateChanged("call_4", CallState.DISCONNECTED)
        kotlinx.coroutines.delay(100)
        assertEquals(CallState.DISCONNECTED, callSessionManager.activeCallSession.value?.state)
    }

    // ─────────────────────────────────────────────────────────────
    // 5. Multiple Call Sessions Handling
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testMultipleCallSessionsMaintained() = runBlocking {
        // Call 1: Active
        callSessionManager.onTelecomCallAdded(
            callId = "call_active",
            rawNumber = "+91 11111 11111",
            telecomDisplayName = "First Caller",
            direction = CallDirection.INCOMING,
            initialState = CallState.ACTIVE,
            capabilities = CallCapabilities(canHold = true),
            accountHandleId = null,
            accountComponentName = null
        )

        // Call 2: Second incoming call on waiting
        callSessionManager.onTelecomCallAdded(
            callId = "call_waiting",
            rawNumber = "+91 22222 22222",
            telecomDisplayName = "Second Caller",
            direction = CallDirection.INCOMING,
            initialState = CallState.RINGING,
            capabilities = CallCapabilities(canHold = true),
            accountHandleId = null,
            accountComponentName = null
        )

        kotlinx.coroutines.delay(100)
        val sessions = callSessionManager.callSessions.value
        assertEquals(2, sessions.size)

        // Active call session prioritizes ringing call
        val primary = callSessionManager.activeCallSession.value
        assertEquals("call_waiting", primary?.callId)
        assertEquals(CallState.RINGING, primary?.state)
    }

    // ─────────────────────────────────────────────────────────────
    // 6. Unknown Caller Fallback Handling
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testUnknownCallerGracefulFallback() = runBlocking {
        callSessionManager.onTelecomCallAdded(
            callId = "call_unknown",
            rawNumber = "+91 99999 12345",
            telecomDisplayName = null,
            direction = CallDirection.INCOMING,
            initialState = CallState.RINGING,
            capabilities = CallCapabilities(),
            accountHandleId = null,
            accountComponentName = null
        )

        kotlinx.coroutines.delay(100)

        val session = callSessionManager.activeCallSession.value
        assertNotNull(session)
        assertNull(session?.personId)
        assertEquals("+91 99999 12345", session?.callerDisplayName)
        assertEquals(LeadStatus.UNKNOWN, session?.crmStatus)
        assertEquals(LeadPriority.MEDIUM, session?.priority)
    }

    // ─────────────────────────────────────────────────────────────
    // 7. Unsupported Telecom Capability Handling
    // ─────────────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────
    // 7. Unsupported Telecom Capability Handling
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testUnsupportedCapabilityHandling() = runBlocking {
        // Device/carrier doesn't support hold
        val unsupportedCaps = CallCapabilities(canHold = false, canMute = true)

        callSessionManager.onTelecomCallAdded(
            callId = "call_no_hold",
            rawNumber = "+91 12345 67890",
            telecomDisplayName = "Test",
            direction = CallDirection.OUTGOING,
            initialState = CallState.ACTIVE,
            capabilities = unsupportedCaps,
            accountHandleId = null,
            accountComponentName = null
        )

        kotlinx.coroutines.delay(100)

        val session = callSessionManager.activeCallSession.value
        assertNotNull(session)
        assertFalse(session!!.capabilities.canHold)
        assertTrue(session.capabilities.canMute)
    }

    // ─────────────────────────────────────────────────────────────
    // 8. SIM Information Mapping & Fallback
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testSimInformationMappingAndFallback() = runBlocking {
        fakeSimRepository.sims.add(
            SimInfo(
                subscriptionId = 2,
                slotIndex = 1,
                carrierName = "Jio 5G",
                displayName = "Business SIM",
                phoneNumber = "+91 98765 00002"
            )
        )

        callSessionManager.onTelecomCallAdded(
            callId = "call_sim",
            rawNumber = "+91 12345 67890",
            telecomDisplayName = "Test",
            direction = CallDirection.INCOMING,
            initialState = CallState.RINGING,
            capabilities = CallCapabilities(),
            accountHandleId = "2",
            accountComponentName = "telecom"
        )

        kotlinx.coroutines.delay(100)

        val session = callSessionManager.activeCallSession.value
        assertNotNull(session)
        assertEquals("Jio 5G", session?.simInfo?.carrierName)
        assertEquals(1, session?.simInfo?.slotIndex)
    }

    // ─────────────────────────────────────────────────────────────
    // 9. Call Cleanup After Termination
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testCallTerminationCleanup() = runBlocking {
        callSessionManager.onTelecomCallAdded(
            callId = "call_to_remove",
            rawNumber = "+91 12345 67890",
            telecomDisplayName = "Test",
            direction = CallDirection.INCOMING,
            initialState = CallState.RINGING,
            capabilities = CallCapabilities(),
            accountHandleId = null,
            accountComponentName = null
        )

        kotlinx.coroutines.delay(100)
        assertEquals(1, callSessionManager.callSessions.value.size)

        callSessionManager.onTelecomCallRemoved("call_to_remove")
        kotlinx.coroutines.delay(100)
        assertEquals(0, callSessionManager.callSessions.value.size)
        assertNull(callSessionManager.activeCallSession.value)
    }

    // ── Test Fakes ───────────────────────────────────────────────

    private fun createFakeContext(): Context {
        return object : ContextWrapper(null) {
            override fun getPackageName(): String = "com.example.callog"
            override fun startActivity(intent: Intent?) {}
            override fun getApplicationContext(): Context = this
        }
    }

    class FakeTelecomCallController : TelecomCallController {
        var lastAnsweredCallId: String? = null
        var lastRejectedCallId: String? = null
        var lastDisconnectedCallId: String? = null
        var isMuted = false
        var lastRoute: AudioRoute? = null
        var lastHeldCallId: String? = null
        var lastUnheldCallId: String? = null

        override fun answerCall(callId: String) { lastAnsweredCallId = callId }
        override fun rejectCall(callId: String) { lastRejectedCallId = callId }
        override fun disconnectCall(callId: String) { lastDisconnectedCallId = callId }
        override fun setCallMuted(shouldMute: Boolean) { isMuted = shouldMute }
        override fun setAudioRoute(route: AudioRoute) { lastRoute = route }
        override fun holdCall(callId: String) { lastHeldCallId = callId }
        override fun unholdCall(callId: String) { lastUnheldCallId = callId }
        override fun playDtmfTone(callId: String, digit: Char) {}
        override fun stopDtmfTone(callId: String) {}
    }

    class FakeRingtoneManager(context: Context) : CallRingtoneManager(context) {
        var started = false
        var stopped = false

        override fun startRingtone(person: Person?, lead: Lead?) {
            started = true
            stopped = false
        }

        override fun stopRingtone() {
            stopped = true
            started = false
        }
    }

    class FakePersonRepository : PersonRepository {
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
    }

    class FakeLeadRepository : LeadRepository {
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

    class FakeCallRepository : CallRepository {
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

    class FakeSimManager(context: Context) : SimManager(context)

    class FakeSimRepository(context: Context) : SimRepository(FakeSimManager(context)) {
        val sims = mutableListOf<SimInfo>()

        override fun getInstalledSims(): List<SimInfo> = sims
        override fun resolveSubscriptionId(phoneAccountId: String?, componentName: String?): Int {
            return phoneAccountId?.toIntOrNull() ?: -1
        }
    }

    class FakeCallNotificationManager(context: Context) : CallNotificationManager(context) {
        override fun showIncomingCallNotification(session: CallSessionState) {}
        override fun showActiveCallNotification(session: CallSessionState) {}
        override fun cancelCallNotification() {}
    }
}
