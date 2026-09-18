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
import com.example.callog.core.telecom.CallAudioController
import com.example.callog.core.telecom.ProximityController
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
    private lateinit var fakeRingtoneController: FakeRingtoneController
    private lateinit var fakeAudioController: CallAudioController
    private lateinit var fakeProximityController: ProximityController
    private lateinit var fakeNotificationManager: FakeCallNotificationManager
    private lateinit var fakeController: FakeTelecomCallController

    @Before
    fun setUp() {
        val fakeContext = createFakeContext()
        fakePersonRepository = FakePersonRepository()
        fakeLeadRepository = FakeLeadRepository()
        fakeCallRepository = FakeCallRepository()
        fakeSimRepository = FakeSimRepository(fakeContext)
        fakeRingtoneController = FakeRingtoneController(fakeContext)
        fakeAudioController = CallAudioController(fakeContext)
        fakeProximityController = ProximityController(fakeContext)
        fakeNotificationManager = FakeCallNotificationManager(fakeContext)
        fakeController = FakeTelecomCallController()
        val fakeHapticManager = FakeCallHapticManager(fakeContext)
        val fakeDtmfTonePlayer = FakeDtmfTonePlayer()

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
            dialerRoleManager = com.example.callog.domain.service.DialerRoleManager(fakeContext)
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
        assertTrue(fakeRingtoneController.started)
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
        assertTrue(fakeRingtoneController.started)

        // User answers via UI
        callSessionManager.executeAction(CallAction.Answer("call_2"))
        assertEquals("call_2", fakeController.lastAnsweredCallId)

        // Telecom updates state to ACTIVE
        callSessionManager.onTelecomCallStateChanged("call_2", CallState.ACTIVE)
        kotlinx.coroutines.delay(100)

        val active = callSessionManager.activeCallSession.value
        assertEquals(CallState.ACTIVE, active?.state)

        // Verify ringtone stopped upon answer
        assertTrue(fakeRingtoneController.stopped)
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
        assertTrue(fakeRingtoneController.started)

        // User rejects call
        callSessionManager.executeAction(CallAction.Reject("call_3"))
        assertEquals("call_3", fakeController.lastRejectedCallId)

        // Telecom updates state to DISCONNECTED
        callSessionManager.onTelecomCallStateChanged("call_3", CallState.DISCONNECTED)
        kotlinx.coroutines.delay(100)

        // Verify ringtone stopped upon reject
        assertTrue(fakeRingtoneController.stopped)
    }

    // ─────────────────────────────────────────────────────────────
    // 3b. Telephony Pre-session to Telecom Handover & Answer/Reject
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testTelephonyIncomingPreSessionHandoverToTelecom() = runBlocking {
        // Step 1: Telephony broadcast arrives before InCallService binds
        callSessionManager.unregisterTelecomController(fakeController)
        callSessionManager.onTelephonyCallStateChanged(
            state = CallState.RINGING,
            number = "+91 98765 43210",
            direction = CallDirection.INCOMING
        )

        val preSession = callSessionManager.activeCallSession.value
        assertNotNull(preSession)
        assertEquals(CallState.RINGING, preSession?.state)
        assertTrue(preSession?.callId?.startsWith("call_") == true)

        // Step 2: InCallService connects and Telecom onCallAdded arrives with authoritative ID
        callSessionManager.registerTelecomController(fakeController)
        val authoritativeTelecomId = "telecom_hash_12345"
        callSessionManager.onTelecomCallAdded(
            callId = authoritativeTelecomId,
            rawNumber = "+91 98765 43210",
            telecomDisplayName = "VIP Client",
            direction = CallDirection.INCOMING,
            initialState = CallState.RINGING,
            capabilities = CallCapabilities(canHold = true),
            accountHandleId = null,
            accountComponentName = null
        )

        kotlinx.coroutines.delay(50)
        val sessions = callSessionManager.callSessions.value
        assertEquals(1, sessions.size)
        val active = callSessionManager.activeCallSession.value
        assertEquals(authoritativeTelecomId, active?.callId)

        // Step 3: Answering invokes Telecom controller on authoritative ID
        callSessionManager.executeAction(CallAction.Answer(authoritativeTelecomId))
        assertEquals(authoritativeTelecomId, fakeController.lastAnsweredCallId)
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

    @Test
    fun testRejectCallWithMessageDispatchesToController() = runBlocking {
        callSessionManager.onTelecomCallAdded(
            callId = "call_msg_1",
            rawNumber = "9988776655",
            telecomDisplayName = null,
            direction = CallDirection.INCOMING,
            initialState = CallState.RINGING,
            capabilities = CallCapabilities(),
            accountHandleId = null,
            accountComponentName = null
        )

        kotlinx.coroutines.delay(100)

        callSessionManager.executeAction(
            CallAction.RejectWithMessage("call_msg_1", "In a meeting. Will call back soon.")
        )

        assertEquals("call_msg_1", fakeController.lastRejectedWithMessageCallId)
        assertEquals("In a meeting. Will call back soon.", fakeController.lastRejectMessage)
        assertTrue(fakeRingtoneController.stopped)
    }

    @Test
    fun testAnswerCallAutoHoldsExistingActiveCall() = runBlocking {
        // Line 1: Active call
        callSessionManager.onTelecomCallAdded(
            callId = "call_line_1",
            rawNumber = "1111111111",
            telecomDisplayName = null,
            direction = CallDirection.OUTGOING,
            initialState = CallState.ACTIVE,
            capabilities = CallCapabilities(canHold = true),
            accountHandleId = null,
            accountComponentName = null
        )

        // Line 2: Incoming call
        callSessionManager.onTelecomCallAdded(
            callId = "call_line_2",
            rawNumber = "2222222222",
            telecomDisplayName = null,
            direction = CallDirection.INCOMING,
            initialState = CallState.RINGING,
            capabilities = CallCapabilities(),
            accountHandleId = null,
            accountComponentName = null
        )

        // Answering Line 2
        callSessionManager.executeAction(CallAction.Answer("call_line_2"))

        // Assert Line 1 was auto-held and Line 2 was answered
        assertEquals("call_line_1", fakeController.lastHeldCallId)
        assertEquals("call_line_2", fakeController.lastAnsweredCallId)
    }

    @Test
    fun testSwapAndMergeCallsDispatches() = runBlocking {
        callSessionManager.executeAction(CallAction.SwapCalls)
        assertTrue(fakeController.swapCallsCalled)

        callSessionManager.executeAction(CallAction.MergeCalls("call_1", "call_2"))
        assertEquals("call_1", fakeController.lastMergedCall1)
        assertEquals("call_2", fakeController.lastMergedCall2)
    }

    @Test
    fun testSetAudioRouteAndSendDtmf() = runBlocking {
        callSessionManager.onTelecomCallAdded(
            callId = "call_audio_test",
            rawNumber = "5556667777",
            telecomDisplayName = null,
            direction = CallDirection.OUTGOING,
            initialState = CallState.ACTIVE,
            capabilities = CallCapabilities(),
            accountHandleId = null,
            accountComponentName = null
        )

        callSessionManager.executeAction(CallAction.SetAudioRoute(AudioRoute.SPEAKER))
        assertEquals(AudioRoute.SPEAKER, fakeController.lastRoute)
        callSessionManager.onAudioStateChanged(false, AudioRoute.SPEAKER, listOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER))
        assertEquals(AudioRoute.SPEAKER, callSessionManager.audioState.value.route)

        callSessionManager.executeAction(CallAction.SendDtmf("call_audio_test", '9'))
        assertEquals('9', fakeController.lastDtmfDigit)
    }

    @Test
    fun testIncomingCallShowsIncomingNotification() = runBlocking {
        callSessionManager.onTelecomCallAdded(
            callId = "call_notif_1",
            rawNumber = "9876543210",
            telecomDisplayName = "Test Lead",
            direction = CallDirection.INCOMING,
            initialState = CallState.RINGING,
            capabilities = CallCapabilities(),
            accountHandleId = null,
            accountComponentName = null
        )

        kotlinx.coroutines.delay(100)
        assertNotNull(fakeNotificationManager.lastIncomingSession)
        assertEquals("call_notif_1", fakeNotificationManager.lastIncomingSession?.callId)
        assertFalse(fakeNotificationManager.isNotificationCancelled)
    }

    @Test
    fun testActiveCallAudioStateUpdatesNotification() = runBlocking {
        callSessionManager.onTelecomCallAdded(
            callId = "call_notif_2",
            rawNumber = "9876543210",
            telecomDisplayName = "Active Lead",
            direction = CallDirection.OUTGOING,
            initialState = CallState.ACTIVE,
            capabilities = CallCapabilities(),
            accountHandleId = null,
            accountComponentName = null
        )

        kotlinx.coroutines.delay(100)
        assertNotNull(fakeNotificationManager.lastActiveSession)

        // Toggle speaker
        callSessionManager.onAudioStateChanged(true, AudioRoute.SPEAKER, listOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER))
        assertTrue(fakeNotificationManager.lastActiveSession?.isMuted == true)
        assertTrue(fakeNotificationManager.lastActiveSession?.isSpeakerOn == true)
    }

    @Test
    fun testMissedCallNotificationTriggeredOnUnansweredIncomingDisconnect() = runBlocking {
        callSessionManager.onTelecomCallAdded(
            callId = "call_missed_1",
            rawNumber = "9876543210",
            telecomDisplayName = "Important Client",
            direction = CallDirection.INCOMING,
            initialState = CallState.RINGING,
            capabilities = CallCapabilities(),
            accountHandleId = null,
            accountComponentName = null
        )

        kotlinx.coroutines.delay(100)

        // Remote caller hangs up without user answering
        callSessionManager.onTelecomCallStateChanged("call_missed_1", CallState.DISCONNECTED)
        kotlinx.coroutines.delay(100)

        assertTrue(fakeNotificationManager.isNotificationCancelled)
        assertNotNull(fakeNotificationManager.lastMissedSession)
        assertEquals("call_missed_1", fakeNotificationManager.lastMissedSession?.callId)
    }

    // ── Test Fakes ───────────────────────────────────────────────

    private fun createFakeContext(): Context {
        return object : ContextWrapper(null) {
            override fun getPackageName(): String = "com.example.callog"
            override fun startActivity(intent: Intent?) {}
            override fun getApplicationContext(): Context = this
        }
    }

    class FakeCallHapticManager(context: Context) : com.example.callog.core.telecom.CallHapticManager(context)

    class FakeProximitySensorManager(context: Context) : com.example.callog.core.telecom.ProximitySensorManager(context)

    class FakeDtmfTonePlayer : com.example.callog.core.telecom.DtmfTonePlayer()

    class FakeTelecomCallController : TelecomCallController {
        var lastAnsweredCallId: String? = null
        var lastRejectedCallId: String? = null
        var lastRejectedWithMessageCallId: String? = null
        var lastRejectMessage: String? = null
        var lastDisconnectedCallId: String? = null
        var isMuted = false
        var lastRoute: AudioRoute? = null
        var lastHeldCallId: String? = null
        var lastUnheldCallId: String? = null
        var swapCallsCalled = false
        var lastMergedCall1: String? = null
        var lastMergedCall2: String? = null
        var lastDtmfDigit: Char? = null

        override fun answerCall(callId: String) { lastAnsweredCallId = callId }
        override fun rejectCall(callId: String) { lastRejectedCallId = callId }
        override fun rejectCallWithMessage(callId: String, textMessage: String) {
            lastRejectedWithMessageCallId = callId
            lastRejectMessage = textMessage
        }
        override fun disconnectCall(callId: String) { lastDisconnectedCallId = callId }
        override fun setCallMuted(shouldMute: Boolean) { isMuted = shouldMute }
        override fun setAudioRoute(route: AudioRoute) { lastRoute = route }
        override fun holdCall(callId: String) { lastHeldCallId = callId }
        override fun unholdCall(callId: String) { lastUnheldCallId = callId }
        override fun swapCalls() { swapCallsCalled = true }
        override fun mergeCalls(callId1: String, callId2: String) {
            lastMergedCall1 = callId1
            lastMergedCall2 = callId2
        }
        override fun playDtmfTone(callId: String, digit: Char) { lastDtmfDigit = digit }
        override fun stopDtmfTone(callId: String) {}
    }

    class FakeRingtonePolicy : com.example.callog.domain.repository.RingtonePolicy {
        override fun ringtoneFor(person: Person?, lead: Lead?): android.net.Uri = android.net.Uri.EMPTY
    }

    class FakeRingtoneController(context: Context) : com.example.callog.core.telecom.RingtoneController(context, FakeRingtonePolicy()) {
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

        override fun silenceRinger() {
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
        override suspend fun syncGlobalContactsFromSupabase(): Result<List<Person>> = Result.success(emptyList())
        override suspend fun createGlobalContact(name: String, phone: String, company: String?, notes: String?): Result<Person> =
            Result.success(Person(id = "p1", displayName = name, companyName = company, notes = notes, phoneNumbers = listOf(PhoneNumber("pn1", "p1", phone, phone))))
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
        var lastIncomingSession: CallSessionState? = null
        var lastActiveSession: CallSessionState? = null
        var lastMissedSession: CallSessionState? = null
        var isNotificationCancelled: Boolean = false

        override fun showIncomingCallNotification(session: CallSessionState) {
            lastIncomingSession = session
            isNotificationCancelled = false
        }

        override fun showActiveCallNotification(session: CallSessionState) {
            lastActiveSession = session
            isNotificationCancelled = false
        }

        override fun showMissedCallNotification(session: CallSessionState) {
            lastMissedSession = session
        }

        override fun cancelCallNotification() {
            isNotificationCancelled = true
        }
    }
}
