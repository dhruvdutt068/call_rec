package com.example.callog.telecom

import android.content.Context
import android.content.ContextWrapper
import com.example.callog.core.telecom.CallAudioController
import com.example.callog.core.telecom.DtmfTonePlayer
import com.example.callog.core.telecom.ProximityController
import com.example.callog.core.telecom.T9SearchEngine
import com.example.callog.core.telecom.TelecomDialerManager
import com.example.callog.domain.model.*
import com.example.callog.domain.service.CallSessionManager
import com.example.callog.domain.service.SyncManager
import com.example.callog.domain.service.simulator.CallSimulatorManager
import com.example.callog.domain.usecase.GetCallLogsUseCase
import com.example.callog.domain.usecase.ToggleCallFavoriteUseCase
import com.example.callog.presentation.screens.dialer.DialerMode
import com.example.callog.presentation.screens.dialer.DialerUiAction
import com.example.callog.presentation.screens.dialer.DialerViewModel
import com.example.callog.sim.SimInfo
import com.example.callog.sim.SimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DialerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: DialerViewModel
    private lateinit var fakePersonRepository: CallSessionManagerTest.FakePersonRepository
    private lateinit var fakeLeadRepository: CallSessionManagerTest.FakeLeadRepository
    private lateinit var fakeSimRepository: CallSessionManagerTest.FakeSimRepository
    private lateinit var fakeCallRepository: CallSessionManagerTest.FakeCallRepository
    private lateinit var fakeDialerManager: FakeTelecomDialerManager
    private lateinit var fakeTonePlayer: FakeDtmfTonePlayer
    private lateinit var fakeSimulatorManager: CallSimulatorManager
    private lateinit var fakeSyncManager: FakeSyncManager
    private lateinit var fakeContext: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeContext = object : ContextWrapper(null) {
            override fun getPackageName(): String = "com.example.callog"
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
        }

        fakePersonRepository = CallSessionManagerTest.FakePersonRepository()
        fakeLeadRepository = CallSessionManagerTest.FakeLeadRepository()
        fakeSimRepository = CallSessionManagerTest.FakeSimRepository(fakeContext)
        fakeCallRepository = CallSessionManagerTest.FakeCallRepository()
        fakeDialerManager = FakeTelecomDialerManager(fakeContext, fakeSimRepository)
        fakeTonePlayer = FakeDtmfTonePlayer()
        fakeSyncManager = FakeSyncManager()

        // Populate test SIMs
        fakeSimRepository.sims.addAll(
            listOf(
                SimInfo(1, 0, "Jio 5G", "SIM 1", "+91 98765 43210", true),
                SimInfo(2, 1, "Airtel", "SIM 2", "+91 88888 22222", true)
            )
        )

        // Setup CallSimulatorManager
        val callSessionManager = CallSessionManager(
            context = fakeContext,
            personRepository = fakePersonRepository,
            leadRepository = fakeLeadRepository,
            callRepository = fakeCallRepository,
            simRepository = fakeSimRepository,
            ringtoneController = CallSessionManagerTest.FakeRingtoneController(fakeContext),
            callAudioController = CallAudioController(fakeContext),
            proximityController = ProximityController(fakeContext),
            notificationManager = CallSessionManagerTest.FakeCallNotificationManager(fakeContext),
            callHapticManager = CallSessionManagerTest.FakeCallHapticManager(fakeContext),
            dtmfTonePlayer = fakeTonePlayer,
            contactsProvider = com.example.callog.data.provider.ContactsProvider(fakeContext),
            dialerRoleManager = com.example.callog.domain.service.DialerRoleManager(fakeContext),
            callRecordingController = CallSessionManagerTest.FakeCallRecordingController(),
            recordingRepository = CallSessionManagerTest.FakeRecordingRepository()
        )

        fakeSimulatorManager = CallSimulatorManager(
            context = fakeContext,
            callSessionManager = callSessionManager,
            personRepository = fakePersonRepository,
            simRepository = fakeSimRepository,
            callDao = CallSessionManagerTest.FakeCallDao(),
            salesCallDao = CallSessionManagerTest.FakeSalesCallDao()
        )

        val getCallLogsUseCase = GetCallLogsUseCase(fakeCallRepository)
        val toggleCallFavoriteUseCase = ToggleCallFavoriteUseCase(fakeCallRepository)

        viewModel = DialerViewModel(
            context = fakeContext,
            personRepository = fakePersonRepository,
            leadRepository = fakeLeadRepository,
            simRepository = fakeSimRepository,
            t9SearchEngine = T9SearchEngine(),
            telecomDialerManager = fakeDialerManager,
            dtmfTonePlayer = fakeTonePlayer,
            simulatorManager = fakeSimulatorManager,
            getCallLogsUseCase = getCallLogsUseCase,
            toggleCallFavoriteUseCase = toggleCallFavoriteUseCase,
            syncManager = fakeSyncManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState_isCallLogsMode() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DialerMode.CallLogs, state.mode)
        assertEquals("", state.inputNumber)
        assertEquals(2, state.activeSims.size)

        collectJob.cancel()
    }

    @Test
    fun testOpenAndCloseDialpad() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        viewModel.onAction(DialerUiAction.OpenDialpad)
        testScheduler.advanceUntilIdle()
        assertEquals(DialerMode.Dialpad, viewModel.uiState.value.mode)

        viewModel.onAction(DialerUiAction.CloseDialpad)
        testScheduler.advanceUntilIdle()
        assertEquals(DialerMode.CallLogs, viewModel.uiState.value.mode)

        collectJob.cancel()
    }

    @Test
    fun testToggleDialpad() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        viewModel.onAction(DialerUiAction.ToggleDialpad)
        testScheduler.advanceUntilIdle()
        assertEquals(DialerMode.Dialpad, viewModel.uiState.value.mode)

        viewModel.onAction(DialerUiAction.ToggleDialpad)
        testScheduler.advanceUntilIdle()
        assertEquals(DialerMode.CallLogs, viewModel.uiState.value.mode)

        collectJob.cancel()
    }

    @Test
    fun testDigitInputAndBackspace() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        viewModel.onAction(DialerUiAction.InputDigit('9'))
        viewModel.onAction(DialerUiAction.InputDigit('8'))
        viewModel.onAction(DialerUiAction.InputDigit('7'))
        testScheduler.advanceUntilIdle()

        assertEquals("987", viewModel.uiState.value.inputNumber)
        assertEquals(3, fakeTonePlayer.playedTones.size)

        viewModel.onAction(DialerUiAction.Backspace)
        testScheduler.advanceUntilIdle()
        assertEquals("98", viewModel.uiState.value.inputNumber)

        viewModel.onAction(DialerUiAction.ClearInput)
        testScheduler.advanceUntilIdle()
        assertEquals("", viewModel.uiState.value.inputNumber)

        collectJob.cancel()
    }

    @Test
    fun testLongPressZero_appendsPlus() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        viewModel.onAction(DialerUiAction.LongPressZero)
        testScheduler.advanceUntilIdle()

        assertEquals("+", viewModel.uiState.value.inputNumber)
        collectJob.cancel()
    }

    @Test
    fun testPlaceCall_blankNumber_doesNotInvokeManager() = runTest(testDispatcher) {
        viewModel.onAction(DialerUiAction.PlaceCall("", null))
        assertNull(fakeDialerManager.lastPlacedCall)
    }

    @Test
    fun testPlaceCall_validNumber_invokesManager() = runTest(testDispatcher) {
        val sim1 = fakeSimRepository.sims[0]
        viewModel.onAction(DialerUiAction.PlaceCall("+91 98765 43210", sim1))

        assertNotNull(fakeDialerManager.lastPlacedCall)
        assertEquals("+91 98765 43210", fakeDialerManager.lastPlacedCall?.first)
        assertEquals(sim1, fakeDialerManager.lastPlacedCall?.second)
    }

    @Test
    fun testDirectCall_usesFirstSim() = runTest(testDispatcher) {
        viewModel.onAction(DialerUiAction.DirectCall("+91 99999 11111"))

        assertNotNull(fakeDialerManager.lastPlacedCall)
        assertEquals("+91 99999 11111", fakeDialerManager.lastPlacedCall?.first)
        assertEquals(fakeSimRepository.sims[0], fakeDialerManager.lastPlacedCall?.second)
    }

    @Test
    fun testFilterAndSortCallLogs() = runTest(testDispatcher) {
        val collectJob = launch { viewModel.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        viewModel.onAction(DialerUiAction.SetFilter("MISSED"))
        testScheduler.advanceUntilIdle()
        assertEquals("MISSED", viewModel.uiState.value.activeFilter)

        viewModel.onAction(DialerUiAction.SetSearchQuery("Charlie"))
        testScheduler.advanceUntilIdle()
        assertEquals("Charlie", viewModel.uiState.value.searchQuery)

        collectJob.cancel()
    }

    @Test
    fun testToggleFavorite() = runTest(testDispatcher) {
        viewModel.onAction(DialerUiAction.ToggleFavorite(1L))
        testScheduler.advanceUntilIdle()

        assertTrue(fakeCallRepository.toggledFavorites.contains(1L))
    }

    // --- Fakes ---

    class FakeTelecomDialerManager(context: Context, simRepository: SimRepository) : TelecomDialerManager(context, simRepository) {
        var lastPlacedCall: Pair<String, SimInfo?>? = null

        override fun placeCall(phoneNumber: String, simInfo: SimInfo?): Result<Unit> {
            if (phoneNumber.isBlank()) return Result.failure(IllegalArgumentException("Blank"))
            lastPlacedCall = Pair(phoneNumber, simInfo)
            return Result.success(Unit)
        }
    }

    class FakeDtmfTonePlayer : DtmfTonePlayer() {
        val playedTones = mutableListOf<Char>()
        override fun playTone(digit: Char, durationMs: Int) {
            playedTones.add(digit)
        }
    }

    class FakeSyncManager : SyncManager {
        override val isSyncing: Flow<Boolean> = flowOf(false)
        override val syncProgress: Flow<String?> = flowOf(null)
        override fun startSync() {}
        override fun stopSync() {}
        override fun schedulePeriodicSync(intervalMinutes: Long) {}
        override fun cancelPeriodicSync() {}
        override suspend fun forceResetAndSync() {}
    }
}
