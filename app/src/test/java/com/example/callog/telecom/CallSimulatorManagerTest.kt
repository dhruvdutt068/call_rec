package com.example.callog.telecom

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.example.callog.core.telecom.CallAudioController
import com.example.callog.core.telecom.ProximityController
import com.example.callog.data.local.dao.CallDao
import com.example.callog.data.local.dao.SalesCallDao
import com.example.callog.data.local.entity.CallEntity
import com.example.callog.data.local.entity.SalesCallEntity
import com.example.callog.domain.call.*
import com.example.callog.domain.model.*
import com.example.callog.domain.service.CallSessionManager
import com.example.callog.domain.service.simulator.CallSimulatorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallSimulatorManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var callSessionManager: CallSessionManager
    private lateinit var callSimulatorManager: CallSimulatorManager
    private lateinit var fakeCallDao: FakeCallDao
    private lateinit var fakeSalesCallDao: FakeSalesCallDao

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val fakeContext = createFakeContext()
        val fakePersonRepo = CallSessionManagerTest.FakePersonRepository()
        val fakeLeadRepo = CallSessionManagerTest.FakeLeadRepository()
        val fakeCallRepo = CallSessionManagerTest.FakeCallRepository()
        val fakeSimRepo = CallSessionManagerTest.FakeSimRepository(fakeContext)
        val fakeRingtoneCtrl = CallSessionManagerTest.FakeRingtoneController(fakeContext)
        val fakeAudioCtrl = CallAudioController(fakeContext)
        val fakeProximityCtrl = ProximityController(fakeContext)
        val fakeNotifMgr = CallSessionManagerTest.FakeCallNotificationManager(fakeContext)
        val fakeHapticMgr = CallSessionManagerTest.FakeCallHapticManager(fakeContext)
        val fakeDtmfPlayer = CallSessionManagerTest.FakeDtmfTonePlayer()

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
            contactsProvider = com.example.callog.data.provider.ContactsProvider(fakeContext),
            dialerRoleManager = com.example.callog.domain.service.DialerRoleManager(fakeContext)
        )

        fakeCallDao = FakeCallDao()
        fakeSalesCallDao = FakeSalesCallDao()

        callSimulatorManager = CallSimulatorManager(
            context = fakeContext,
            callSessionManager = callSessionManager,
            personRepository = fakePersonRepo,
            simRepository = fakeSimRepo,
            callDao = fakeCallDao,
            salesCallDao = fakeSalesCallDao
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testStartIncomingCallSimulationCreatesRingingSession() = runBlocking {
        val config = SimulatedCallConfig(
            callerName = "Sophia Vance",
            phoneNumber = "+1 (555) 999-8888",
            direction = CallDirection.INCOMING,
            companyName = "Apex Capital",
            crmStatus = LeadStatus.HOT,
            priority = LeadPriority.URGENT,
            autoAnswerDelaySec = 0,
            autoHangupDurationSec = 0,
            recordToDatabase = true
        )

        callSimulatorManager.startSimulation(config)

        val engineState = callSimulatorManager.engineState.value
        assertTrue("Engine should be running", engineState.isRunning)
        assertEquals("Sophia Vance", engineState.activeConfig?.callerName)
        assertEquals(CallState.RINGING, engineState.currentCallState)

        val activeSession = callSessionManager.activeCallSession.value
        assertNotNull(activeSession)
        assertEquals(CallState.RINGING, activeSession?.state)
        assertEquals("Sophia Vance", activeSession?.callerDisplayName)
    }

    @Test
    fun testManualAnswerAndHangupTransitionsAndPersistsCall() = runBlocking {
        val config = SimulatedCallConfig(
            callerName = "Alex Carter",
            phoneNumber = "+1 (555) 321-4321",
            direction = CallDirection.INCOMING,
            companyName = "TechCorp",
            crmStatus = LeadStatus.HOT,
            priority = LeadPriority.HIGH,
            recordToDatabase = true
        )

        callSimulatorManager.startSimulation(config)
        kotlinx.coroutines.delay(50)

        // Trigger manual remote answer
        callSimulatorManager.remoteAnswer(config.callId)
        kotlinx.coroutines.delay(50)

        val activeState = callSimulatorManager.engineState.value
        assertTrue("Call should be running after answer", activeState.isRunning)
        assertEquals(CallState.ACTIVE, callSessionManager.activeCallSession.value?.state)

        // Simulate ending call with persistence
        val currentSession = callSessionManager.activeCallSession.value
        assertNotNull(currentSession)
        val endedSession = currentSession!!.copy(durationSeconds = 12)
        callSimulatorManager.persistCompletedCall(endedSession)

        // Verify inserted records into Room DAOs
        assertEquals(1, fakeCallDao.insertedCalls.size)
        val savedCall = fakeCallDao.insertedCalls[0]
        assertEquals("+1 (555) 321-4321", savedCall.number)
        assertEquals("Alex Carter", savedCall.name)
        assertEquals(12, savedCall.duration)

        assertEquals(1, fakeSalesCallDao.insertedSalesCalls.size)
        val savedSalesCall = fakeSalesCallDao.insertedSalesCalls[0]
        assertEquals("Alex Carter", savedSalesCall.buyerName)
        assertEquals("+1 (555) 321-4321", savedSalesCall.buyerPhone)
    }

    @Test
    fun testLaunchPresetOutgoingDialing() = runBlocking {
        callSimulatorManager.launchPreset(SimulationPreset.SALES_OUTBOUND)

        val engineState = callSimulatorManager.engineState.value
        assertTrue(engineState.isRunning)
        assertEquals(CallDirection.OUTGOING, engineState.activeConfig?.direction)
        assertEquals("David Kim (Apex Retail)", engineState.activeConfig?.callerName)

        val session = callSessionManager.activeCallSession.value
        assertNotNull(session)
        assertEquals(CallState.CONNECTING, session?.state)
    }

    @Test
    fun testTriggerSecondaryCallAddsCallWaitingLine() = runBlocking {
        // Start primary call
        val primaryConfig = SimulatedCallConfig(
            callerName = "Primary Client",
            phoneNumber = "+1 (555) 111-2222",
            direction = CallDirection.INCOMING
        )
        callSimulatorManager.startSimulation(primaryConfig)
        callSimulatorManager.remoteAnswer(primaryConfig.callId)

        // Trigger secondary incoming call while active
        callSimulatorManager.simulateSecondaryIncomingCall(
            callerName = "Secondary Lead",
            phoneNumber = "+1 (555) 999-1111",
            companyName = "Secondary Corp",
            simSlot = 1
        )

        val state = callSimulatorManager.engineState.value
        assertTrue("Should indicate secondary call active", state.isSecondaryCallActive)
        assertEquals(2, callSessionManager.callSessions.value.size)
    }

    @Test
    fun testCancelSimulationAbortsCleanly() = runBlocking {
        callSimulatorManager.launchPreset(SimulationPreset.VIP_INCOMING)
        assertTrue(callSimulatorManager.engineState.value.isRunning)

        callSimulatorManager.cancelSimulation()

        val state = callSimulatorManager.engineState.value
        assertFalse(state.isRunning)
        assertNull(state.activeConfig)
        assertNull(callSessionManager.activeCallSession.value)
    }

    private fun createFakeContext(): Context {
        return object : ContextWrapper(null) {
            override fun getPackageName(): String = "com.example.callog"
            override fun startActivity(intent: Intent?) {}
            override fun getApplicationContext(): Context = this
        }
    }

    // --- Fake DAOs ---

    class FakeCallDao : CallDao {
        val insertedCalls = mutableListOf<CallEntity>()

        override fun getAllCallsFlow(): Flow<List<CallEntity>> = flowOf(insertedCalls)
        override suspend fun getAllCalls(): List<CallEntity> = insertedCalls
        override suspend fun getCallById(id: Long): CallEntity? = insertedCalls.find { it.id == id }
        override fun getCallByIdFlow(id: Long): Flow<CallEntity?> = flowOf(insertedCalls.find { it.id == id })
        override fun getFavoriteCallsFlow(): Flow<List<CallEntity>> = flowOf(emptyList())
        override fun getCallsWithRecordingsFlow(): Flow<List<CallEntity>> = flowOf(emptyList())

        override suspend fun insertCall(call: CallEntity): Long {
            val callWithId = if (call.id == 0L) call.copy(id = (insertedCalls.size + 1).toLong()) else call
            insertedCalls.add(callWithId)
            return callWithId.id
        }

        override suspend fun insertCalls(calls: List<CallEntity>) {
            calls.forEach { insertCall(it) }
        }

        override suspend fun updateCall(call: CallEntity) {
            val idx = insertedCalls.indexOfFirst { it.id == call.id }
            if (idx >= 0) insertedCalls[idx] = call
        }

        override suspend fun deleteCall(call: CallEntity) {
            insertedCalls.removeAll { it.id == call.id }
        }

        override suspend fun deleteAllCalls() {
            insertedCalls.clear()
        }

        override suspend fun getPendingCalls(): List<CallEntity> = insertedCalls.filter { it.syncStatus != "SYNCED" }
        override suspend fun updateSyncStatus(callId: Long, syncStatus: String) {}
        override suspend fun resetAllSyncStatus() {}
        override fun getCallsForPersonFlow(personId: String): Flow<List<CallEntity>> = flowOf(emptyList())
        override suspend fun getCallsForPerson(personId: String): List<CallEntity> = emptyList()
        override suspend fun getCallCountForPerson(personId: String): Int = 0
        override suspend fun getLatestCallsForPerson(personId: String, limit: Int): List<CallEntity> = emptyList()
        override suspend fun updatePersonIdForNumber(number: String, personId: String) {}
    }

    class FakeSalesCallDao : SalesCallDao {
        val insertedSalesCalls = mutableListOf<SalesCallEntity>()

        override suspend fun insertSalesCall(salesCall: SalesCallEntity) {
            insertedSalesCalls.add(salesCall)
        }

        override fun getAllSalesCalls(): Flow<List<SalesCallEntity>> = flowOf(insertedSalesCalls)

        override suspend fun getSalesCallByCallId(callId: Long): SalesCallEntity? =
            insertedSalesCalls.find { it.callId == callId }

        override fun getSalesCallsBySalesperson(phone: String): Flow<List<SalesCallEntity>> =
            flowOf(insertedSalesCalls.filter { it.salespersonPhone == phone })

        override fun getSalesCallsByBuyer(phone: String): Flow<List<SalesCallEntity>> =
            flowOf(insertedSalesCalls.filter { it.buyerPhone == phone })

        override suspend fun deleteSalesCall(id: Long) {
            insertedSalesCalls.removeAll { it.id == id }
        }

        override suspend fun clearAll() {
            insertedSalesCalls.clear()
        }

        override suspend fun getPendingSalesCalls(): List<SalesCallEntity> =
            insertedSalesCalls.filter { it.syncStatus != "SYNCED" }

        override suspend fun updateSyncStatus(id: Long, status: String, error: String?) {}

        override suspend fun resetSyncStatus() {}
    }
}
