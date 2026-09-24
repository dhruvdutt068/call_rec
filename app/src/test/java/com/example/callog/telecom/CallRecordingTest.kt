package com.example.callog.telecom

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.example.callog.core.telecom.CallAudioController
import com.example.callog.core.telecom.ProximityController
import com.example.callog.data.local.dao.CallDao
import com.example.callog.data.local.dao.SalesCallDao
import com.example.callog.data.provider.ContactsProvider
import com.example.callog.domain.call.CallAction
import com.example.callog.domain.call.CallCapabilities
import com.example.callog.domain.call.CallDirection
import com.example.callog.domain.call.CallRecordingController
import com.example.callog.domain.call.CallRecordingState
import com.example.callog.domain.call.CallState
import com.example.callog.domain.call.RecordingSession
import com.example.callog.domain.service.CallSessionManager
import com.example.callog.domain.service.DialerRoleManager
import com.example.callog.domain.service.simulator.CallSimulatorManager
import com.example.callog.presentation.call.CallSessionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class CallRecordingTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var callSessionManager: CallSessionManager
    private lateinit var fakePersonRepo: CallSessionManagerTest.FakePersonRepository
    private lateinit var fakeLeadRepo: CallSessionManagerTest.FakeLeadRepository
    private lateinit var fakeCallRepo: CallSessionManagerTest.FakeCallRepository
    private lateinit var fakeSimRepo: CallSessionManagerTest.FakeSimRepository
    private lateinit var fakeRingtoneCtrl: CallSessionManagerTest.FakeRingtoneController
    private lateinit var fakeAudioCtrl: CallAudioController
    private lateinit var fakeProximityCtrl: ProximityController
    private lateinit var fakeNotifMgr: CallSessionManagerTest.FakeCallNotificationManager
    private lateinit var fakeHapticMgr: CallSessionManagerTest.FakeCallHapticManager
    private lateinit var fakeDtmfPlayer: CallSessionManagerTest.FakeDtmfTonePlayer
    private lateinit var fakeRecordingCtrl: CallSessionManagerTest.FakeCallRecordingController
    private lateinit var fakeRecordingRepo: CallSessionManagerTest.FakeRecordingRepository
    private lateinit var fakeSimulatorManager: CallSimulatorManager
    private lateinit var fakeContext: Context

    private fun createFakeContext(): Context {
        return object : ContextWrapper(null) {
            override fun getPackageName(): String = "com.example.callog"
            override fun getApplicationContext(): Context = this
            override fun startActivity(intent: Intent?) {}
            override fun getSystemService(name: String): Any? = null
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeContext = createFakeContext()
        fakePersonRepo = CallSessionManagerTest.FakePersonRepository()
        fakeLeadRepo = CallSessionManagerTest.FakeLeadRepository()
        fakeCallRepo = CallSessionManagerTest.FakeCallRepository()
        fakeSimRepo = CallSessionManagerTest.FakeSimRepository(fakeContext)
        fakeRingtoneCtrl = CallSessionManagerTest.FakeRingtoneController(fakeContext)
        fakeAudioCtrl = CallAudioController(fakeContext)
        fakeProximityCtrl = ProximityController(fakeContext)
        fakeNotifMgr = CallSessionManagerTest.FakeCallNotificationManager(fakeContext)
        fakeHapticMgr = CallSessionManagerTest.FakeCallHapticManager(fakeContext)
        fakeDtmfPlayer = CallSessionManagerTest.FakeDtmfTonePlayer()
        fakeRecordingCtrl = CallSessionManagerTest.FakeCallRecordingController()
        fakeRecordingRepo = CallSessionManagerTest.FakeRecordingRepository()

        callSessionManager = CallSessionManager(
            context = fakeContext,
            personRepository = fakePersonRepo,
            leadRepository = fakeLeadRepo,
            callRepository = fakeCallRepo,
            simRepository = fakeSimRepo,
            ringtoneController = fakeRingtoneCtrl,
            callAudioController = fakeAudioCtrl,
            proximityController = fakeProximityCtrl,
            notificationManager = fakeNotifMgr,
            callHapticManager = fakeHapticMgr,
            dtmfTonePlayer = fakeDtmfPlayer,
            contactsProvider = ContactsProvider(fakeContext),
            dialerRoleManager = DialerRoleManager(fakeContext),
            callRecordingController = fakeRecordingCtrl,
            recordingRepository = fakeRecordingRepo
        )

        fakeSimulatorManager = CallSimulatorManager(
            context = fakeContext,
            callSessionManager = callSessionManager,
            personRepository = fakePersonRepo,
            simRepository = fakeSimRepo,
            callDao = CallSimulatorManagerTest.FakeCallDao(),
            salesCallDao = CallSimulatorManagerTest.FakeSalesCallDao()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun addActiveCall(callId: String = "CALL_REC_001"): String {
        callSessionManager.onTelecomCallAdded(
            callId = callId,
            rawNumber = "+91 98765 43210",
            telecomDisplayName = "Test Caller",
            direction = CallDirection.OUTGOING,
            initialState = CallState.ACTIVE,
            capabilities = CallCapabilities(canHold = true, canMute = true),
            accountHandleId = "0",
            accountComponentName = "telecom"
        )
        return callId
    }

    @Test
    fun testInitialCallSessionRecordingStateIsIdle() = runBlocking {
        val callId = addActiveCall("CALL_REC_001")
        delay(100)

        val session = callSessionManager.activeCallSession.value
        assertNotNull(session)
        assertEquals(CallRecordingState.Idle, session?.recordingState)
    }

    @Test
    fun testStartRecordingUpdatesSessionAndController() = runBlocking {
        val callId = addActiveCall("CALL_REC_002")
        delay(100)

        callSessionManager.startRecording(callId)
        delay(100)

        assertEquals(1, fakeRecordingCtrl.startCount)
        val session = callSessionManager.activeCallSession.value
        assertTrue("Recording state should be Recording", session?.recordingState is CallRecordingState.Recording)
    }

    @Test
    fun testStopRecordingUpdatesSessionAndRegistersInRepository() = runBlocking {
        val callId = addActiveCall("CALL_REC_003")
        delay(100)

        callSessionManager.startRecording(callId)
        delay(100)

        callSessionManager.stopRecording()
        delay(100)

        assertEquals(1, fakeRecordingCtrl.stopCount)
        assertEquals(1, fakeRecordingRepo.registeredSessions.size)
        assertEquals("rec_$callId", fakeRecordingRepo.registeredSessions[0].recordingId)
    }

    @Test
    fun testCallDisconnectAutoStopsOngoingRecording() = runBlocking {
        val callId = addActiveCall("CALL_REC_004")
        delay(100)

        callSessionManager.startRecording(callId)
        delay(100)

        assertEquals(1, fakeRecordingCtrl.startCount)

        // Disconnect call
        callSessionManager.onTelecomCallStateChanged(callId, CallState.DISCONNECTED)
        delay(100)

        // Auto-stop should have been triggered
        assertEquals(1, fakeRecordingCtrl.stopCount)
        assertEquals(1, fakeRecordingRepo.registeredSessions.size)
    }

    @Test
    fun testToggleRecordingActionDispatchesCorrectly() = runBlocking {
        val callId = addActiveCall("CALL_REC_005")
        delay(100)

        // 1. Toggle ON
        callSessionManager.executeAction(CallAction.ToggleRecording(callId))
        delay(100)
        assertEquals(1, fakeRecordingCtrl.startCount)

        // 2. Toggle OFF
        callSessionManager.executeAction(CallAction.ToggleRecording(callId))
        delay(100)
        assertEquals(1, fakeRecordingCtrl.stopCount)
        assertEquals(1, fakeRecordingRepo.registeredSessions.size)
    }

    @Test
    fun testRecordingFailureDoesNotCrashCallSession() = runBlocking {
        val failingRecordingCtrl = object : CallRecordingController {
            override val recordingState = MutableStateFlow<CallRecordingState>(CallRecordingState.Idle)
            override suspend fun canRecord(): Boolean = true
            override suspend fun start(callId: String, phoneNumber: String?, personId: String?, simSlot: Int?, startedAt: Long): Result<RecordingSession> {
                recordingState.value = CallRecordingState.Error("Simulated hardware error")
                return Result.failure(IllegalStateException("Simulated hardware error"))
            }
            override suspend fun stop(): Result<RecordingSession> = Result.failure(IllegalStateException("Not recording"))
            override suspend fun cancel() {}
        }

        val csm = CallSessionManager(
            context = fakeContext,
            personRepository = fakePersonRepo,
            leadRepository = fakeLeadRepo,
            callRepository = fakeCallRepo,
            simRepository = fakeSimRepo,
            ringtoneController = fakeRingtoneCtrl,
            callAudioController = fakeAudioCtrl,
            proximityController = fakeProximityCtrl,
            notificationManager = fakeNotifMgr,
            callHapticManager = fakeHapticMgr,
            dtmfTonePlayer = fakeDtmfPlayer,
            contactsProvider = ContactsProvider(fakeContext),
            dialerRoleManager = DialerRoleManager(fakeContext),
            callRecordingController = failingRecordingCtrl,
            recordingRepository = fakeRecordingRepo
        )

        val callId = "CALL_REC_006"
        csm.onTelecomCallAdded(
            callId = callId,
            rawNumber = "+91 98765 43210",
            telecomDisplayName = "Test Caller",
            direction = CallDirection.OUTGOING,
            initialState = CallState.ACTIVE,
            capabilities = CallCapabilities(canHold = true, canMute = true),
            accountHandleId = "0",
            accountComponentName = "telecom"
        )
        delay(100)

        // Attempt start recording with failing controller
        csm.startRecording(callId)
        delay(100)

        // Active call must remain intact and in ACTIVE state
        val session = csm.activeCallSession.value
        assertNotNull(session)
        assertEquals(CallState.ACTIVE, session?.state)
        assertTrue("Recording state should be Error", session?.recordingState is CallRecordingState.Error)
    }

    @Test
    fun testViewModelToggleRecordingDispatchesAction() = runBlocking {
        val callId = addActiveCall("CALL_REC_007")
        delay(100)

        val viewModel = CallSessionViewModel(
            callSessionManager = callSessionManager,
            leadRepository = fakeLeadRepo,
            callRepository = fakeCallRepo,
            callSimulatorManager = fakeSimulatorManager
        )
        delay(100)

        assertEquals(CallRecordingState.Idle, viewModel.recordingState.value)

        // Toggle recording via ViewModel
        viewModel.toggleRecording(callId)
        delay(100)

        assertEquals(1, fakeRecordingCtrl.startCount)
    }
}
