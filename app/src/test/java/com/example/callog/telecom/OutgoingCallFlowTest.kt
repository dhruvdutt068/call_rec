package com.example.callog.telecom

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.example.callog.core.telecom.CallAudioController
import com.example.callog.core.telecom.CallHapticManager
import com.example.callog.core.telecom.DtmfTonePlayer
import com.example.callog.core.telecom.ProximityController
import com.example.callog.core.telecom.RingtoneController
import com.example.callog.core.telecom.TelecomDialerManager
import com.example.callog.data.local.dao.ReminderWithCall
import com.example.callog.data.local.entity.CallEntity
import com.example.callog.data.local.entity.ReminderEntity
import com.example.callog.data.provider.ContactDto
import com.example.callog.data.telecom.CallNotificationManager
import com.example.callog.data.telecom.TelecomCallController
import com.example.callog.domain.call.*
import com.example.callog.domain.model.*
import com.example.callog.domain.repository.CallRepository
import com.example.callog.domain.repository.LeadRepository
import com.example.callog.domain.repository.PersonRepository
import com.example.callog.domain.repository.RingtonePolicy
import com.example.callog.domain.service.CallSessionManager
import com.example.callog.sim.SimInfo
import com.example.callog.sim.SimManager
import com.example.callog.sim.SimRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OutgoingCallFlowTest {

    private lateinit var callSessionManager: CallSessionManager
    private lateinit var dialerManager: TelecomDialerManager
    private lateinit var fakePersonRepository: FakePersonRepository
    private lateinit var fakeLeadRepository: FakeLeadRepository
    private lateinit var fakeCallRepository: FakeCallRepository
    private lateinit var fakeSimRepository: FakeSimRepository
    private lateinit var fakeRingtoneController: FakeRingtoneController
    private lateinit var fakeAudioController: CallAudioController
    private lateinit var fakeProximityController: ProximityController
    private lateinit var fakeNotificationManager: FakeCallNotificationManager
    private lateinit var fakeTelecomController: FakeTelecomCallController
    private lateinit var fakeContext: Context

    @Before
    fun setUp() {
        fakeContext = object : ContextWrapper(null) {
            override fun getPackageName(): String = "com.example.callog"
            override fun getApplicationContext(): Context = this
            override fun startActivity(intent: Intent?) {}
        }

        fakePersonRepository = FakePersonRepository()
        fakeLeadRepository = FakeLeadRepository()
        fakeCallRepository = FakeCallRepository()
        fakeSimRepository = FakeSimRepository(fakeContext)
        fakeRingtoneController = FakeRingtoneController(fakeContext)
        fakeAudioController = CallAudioController(fakeContext)
        fakeProximityController = ProximityController(fakeContext)
        fakeNotificationManager = FakeCallNotificationManager(fakeContext)
        fakeTelecomController = FakeTelecomCallController()
        val fakeHapticManager = FakeCallHapticManager(fakeContext)
        val fakeDtmfTonePlayer = FakeDtmfTonePlayer()

        // Setup dual-SIM configuration
        fakeSimRepository.sims.addAll(
            listOf(
                SimInfo(subscriptionId = 1, slotIndex = 0, carrierName = "Jio 5G", displayName = "Personal SIM", phoneNumber = "+91 98765 00001", isActive = true),
                SimInfo(subscriptionId = 2, slotIndex = 1, carrierName = "Airtel Business", displayName = "Work SIM", phoneNumber = "+91 98765 00002", isActive = true)
            )
        )

        dialerManager = object : TelecomDialerManager(fakeContext, fakeSimRepository) {
            override fun hasCallPhonePermission(): Boolean = true
        }

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

        callSessionManager.registerTelecomController(fakeTelecomController)
    }

    // ─────────────────────────────────────────────────────────────
    // 1. Outgoing Call Pre-seeding & CRM Resolution
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testOutgoingCallPreSeedingResolvesPersonAndLead() = runBlocking {
        // Seed canonical Person & Lead
        val dialedNumber = "+91 91234 56789"
        val normalized = "9123456789"

        val person = Person(
            id = "person_client_1",
            displayName = "Anita Desai",
            companyName = "Desai Capital",
            phoneNumbers = listOf(
                PhoneNumber(id = "pn1", personId = "person_client_1", phoneNumber = dialedNumber, normalizedNumber = normalized)
            )
        )
        fakePersonRepository.people[normalized] = person
        fakePersonRepository.peopleById[person.id] = person

        val lead = Lead(
            id = "lead_client_1",
            personId = person.id,
            status = LeadStatus.HOT,
            priority = LeadPriority.URGENT
        )
        fakeLeadRepository.leads[person.id] = lead

        // Step 1: User places outgoing call via SIM 2 (Work SIM)
        callSessionManager.startOutgoingCall(dialedNumber, simSlot = 1)

        delay(100)

        val activeSession = callSessionManager.activeCallSession.value
        assertNotNull(activeSession)
        assertEquals(CallDirection.OUTGOING, activeSession?.direction)
        assertEquals(CallState.CONNECTING, activeSession?.state)
        assertEquals("Anita Desai", activeSession?.callerDisplayName)
        assertEquals("Desai Capital", activeSession?.companyName)
        assertEquals(LeadStatus.HOT, activeSession?.crmStatus)
        assertEquals(LeadPriority.URGENT, activeSession?.priority)
        assertEquals("Airtel Business", activeSession?.simInfo?.carrierName)
        assertEquals(1, activeSession?.simInfo?.slotIndex)
    }

    // ─────────────────────────────────────────────────────────────
    // 2. Telecom InCallService onCallAdded Merging
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testTelecomCallAddedMergesWithPreSeededOutgoingSession() = runBlocking {
        val dialedNumber = "+91 98888 77777"
        val normalized = "9888877777"

        val person = Person(
            id = "p_vip",
            displayName = "Vikram Patel",
            companyName = "Patel Enterprise",
            phoneNumbers = listOf(PhoneNumber("pn_v", "p_vip", dialedNumber, normalized))
        )
        fakePersonRepository.people[normalized] = person
        fakePersonRepository.peopleById["p_vip"] = person

        // 1. User starts outgoing call in UI
        callSessionManager.startOutgoingCall(dialedNumber, simSlot = 0)
        delay(100)

        val preSession = callSessionManager.activeCallSession.value
        assertTrue(preSession!!.callId.startsWith("outgoing_"))

        // 2. Telecom InCallService onCallAdded arrives from system
        val telecomCallId = "telecom_call_101"
        callSessionManager.onTelecomCallAdded(
            callId = telecomCallId,
            rawNumber = dialedNumber,
            telecomDisplayName = "Vikram Patel",
            direction = CallDirection.OUTGOING,
            initialState = CallState.CONNECTING,
            capabilities = CallCapabilities(canHold = true, canMute = true),
            accountHandleId = "1",
            accountComponentName = "telecom"
        )
        delay(100)

        // Verify pre-seeded temporary ID replaced with authoritative Telecom ID while retaining CRM info
        val mergedSession = callSessionManager.activeCallSession.value
        assertNotNull(mergedSession)
        assertEquals(telecomCallId, mergedSession?.callId)
        assertEquals(CallDirection.OUTGOING, mergedSession?.direction)
        assertEquals(CallState.CONNECTING, mergedSession?.state)
        assertEquals("Vikram Patel", mergedSession?.callerDisplayName)
        assertEquals("Patel Enterprise", mergedSession?.companyName)
        assertEquals("Personal SIM", mergedSession?.simInfo?.displayName)
    }

    // ─────────────────────────────────────────────────────────────
    // 3. Outgoing Call Connecting -> Active -> Duration Ticker
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testOutgoingCallConnectingToActiveTransitionAndTimer() = runBlocking {
        val telecomCallId = "out_call_active_test"
        callSessionManager.onTelecomCallAdded(
            callId = telecomCallId,
            rawNumber = "9900011223",
            telecomDisplayName = "Partner",
            direction = CallDirection.OUTGOING,
            initialState = CallState.CONNECTING,
            capabilities = CallCapabilities(canHold = true, canMute = true),
            accountHandleId = null,
            accountComponentName = null
        )

        assertEquals(CallState.CONNECTING, callSessionManager.activeCallSession.value?.state)

        // Remote party answers the call
        callSessionManager.onTelecomCallStateChanged(telecomCallId, CallState.ACTIVE)
        delay(100)

        val active = callSessionManager.activeCallSession.value
        assertEquals(CallState.ACTIVE, active?.state)
        assertTrue(active!!.connectTimeMillis > 0L)

        // Check active notification was posted
        assertNotNull(fakeNotificationManager.lastActiveSession)
        assertEquals(telecomCallId, fakeNotificationManager.lastActiveSession?.callId)
    }

    // ─────────────────────────────────────────────────────────────
    // 4. Outgoing Call Audio Controls (Mute, Speaker, DTMF)
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testOutgoingCallAudioControlsAndDtmf() = runBlocking {
        val telecomCallId = "out_call_controls"
        callSessionManager.onTelecomCallAdded(
            callId = telecomCallId,
            rawNumber = "1800112233",
            telecomDisplayName = "Customer Care IVR",
            direction = CallDirection.OUTGOING,
            initialState = CallState.ACTIVE,
            capabilities = CallCapabilities(canHold = true, canMute = true),
            accountHandleId = null,
            accountComponentName = null
        )

        // 1. Toggle Speaker
        callSessionManager.executeAction(CallAction.ToggleSpeaker(telecomCallId))
        assertEquals(AudioRoute.SPEAKER, fakeTelecomController.lastRoute)

        // 2. Toggle Mute
        callSessionManager.executeAction(CallAction.ToggleMute(telecomCallId))
        assertTrue(fakeTelecomController.isMuted)

        // 3. Send DTMF Digit '1' on IVR
        callSessionManager.executeAction(CallAction.SendDtmf(telecomCallId, '1'))
        assertEquals('1', fakeTelecomController.lastDtmfDigit)
    }

    // ─────────────────────────────────────────────────────────────
    // 5. Outgoing Call Hangup & Disconnection
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testOutgoingCallUserHangupCleansUpSession() = runBlocking {
        val telecomCallId = "out_call_hangup"
        callSessionManager.onTelecomCallAdded(
            callId = telecomCallId,
            rawNumber = "+91 99999 55555",
            telecomDisplayName = "Vendor",
            direction = CallDirection.OUTGOING,
            initialState = CallState.ACTIVE,
            capabilities = CallCapabilities(),
            accountHandleId = null,
            accountComponentName = null
        )

        delay(100)
        assertEquals(1, callSessionManager.callSessions.value.size)

        // User taps Disconnect / Red Hangup Button
        callSessionManager.executeAction(CallAction.Disconnect(telecomCallId))
        assertEquals(telecomCallId, fakeTelecomController.lastDisconnectedCallId)

        // Telecom signals DISCONNECTED
        callSessionManager.onTelecomCallStateChanged(telecomCallId, CallState.DISCONNECTED)
        delay(100)

        assertEquals(CallState.DISCONNECTED, callSessionManager.activeCallSession.value?.state)
        assertTrue(fakeNotificationManager.isNotificationCancelled)

        // Telecom removes call completely
        callSessionManager.onTelecomCallRemoved(telecomCallId)
        delay(100)
        assertEquals(0, callSessionManager.callSessions.value.size)
        assertNull(callSessionManager.activeCallSession.value)
    }

    // ── Test Fakes ───────────────────────────────────────────────

    class FakeCallHapticManager(context: Context) : CallHapticManager(context)
    class FakeDtmfTonePlayer : DtmfTonePlayer()

    class FakeRingtonePolicy : RingtonePolicy {
        override fun ringtoneFor(person: Person?, lead: Lead?): android.net.Uri = android.net.Uri.EMPTY
    }

    class FakeRingtoneController(context: Context) : RingtoneController(context, FakeRingtonePolicy()) {
        override fun startRingtone(person: Person?, lead: Lead?) {}
        override fun stopRingtone() {}
        override fun silenceRinger() {}
    }

    class FakeTelecomCallController : TelecomCallController {
        var lastAnsweredCallId: String? = null
        var lastRejectedCallId: String? = null
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
        override fun rejectCallWithMessage(callId: String, textMessage: String) {}
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

    class FakeSimRepository(context: Context) : SimRepository(SimManager(context)) {
        val sims = mutableListOf<SimInfo>()
        override fun getInstalledSims(): List<SimInfo> = sims
        override fun resolveSubscriptionId(phoneAccountId: String?, componentName: String?): Int {
            return phoneAccountId?.toIntOrNull() ?: -1
        }
    }

    class FakeCallNotificationManager(context: Context) : CallNotificationManager(context) {
        var lastActiveSession: CallSessionState? = null
        var isNotificationCancelled: Boolean = false

        override fun showIncomingCallNotification(session: CallSessionState) {}
        override fun showActiveCallNotification(session: CallSessionState) {
            lastActiveSession = session
            isNotificationCancelled = false
        }
        override fun showMissedCallNotification(session: CallSessionState) {}
        override fun cancelCallNotification() {
            isNotificationCancelled = true
        }
    }
}
