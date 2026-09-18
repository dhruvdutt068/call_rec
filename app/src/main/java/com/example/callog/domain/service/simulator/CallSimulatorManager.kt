package com.example.callog.domain.service.simulator

import android.content.Context
import android.util.Log
import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.core.utils.PhoneNumberNormalizer
import com.example.callog.data.local.dao.CallDao
import com.example.callog.data.local.dao.SalesCallDao
import com.example.callog.data.local.entity.CallEntity
import com.example.callog.data.local.entity.SalesCallEntity
import com.example.callog.domain.call.CallAction
import com.example.callog.domain.call.CallCapabilities
import com.example.callog.domain.call.CallDirection
import com.example.callog.domain.call.CallSessionState
import com.example.callog.domain.call.CallState
import com.example.callog.domain.model.SimulatedCallConfig
import com.example.callog.domain.model.SimulatedEngineState
import com.example.callog.domain.model.SimulationPreset
import com.example.callog.domain.repository.PersonRepository
import com.example.callog.domain.service.CallSessionManager
import com.example.callog.sim.SimInfo
import com.example.callog.sim.SimRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authoritative engine for orchestrating realistic incoming and outgoing call simulations.
 *
 * Simulates:
 * 1. Single & Dual SIM incoming ringing states with CRM lead intelligence & custom ringtones.
 * 2. Outgoing dialing, connecting ringback, and remote party answering.
 * 3. Call waiting & secondary inbound calls during an active session (testing multi-line card & hold).
 * 4. Automatic timeout flows (auto-answer, auto-hangup) or manual interactive user testing.
 * 5. Persistent recording of simulated interactions to Room DB (CallEntity & SalesCallEntity).
 */
@Singleton
class CallSimulatorManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callSessionManager: CallSessionManager,
    private val personRepository: PersonRepository,
    private val simRepository: SimRepository,
    private val callDao: CallDao,
    private val salesCallDao: SalesCallDao
) {
    companion object {
        private const val TAG = "CallSimulatorManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var autoAnswerJob: Job? = null
    private var autoHangupJob: Job? = null
    private var secondaryCallJob: Job? = null

    private val _engineState = MutableStateFlow(SimulatedEngineState())
    val engineState: StateFlow<SimulatedEngineState> = _engineState.asStateFlow()

    init {
        // Observe CallSessionManager active session to keep engineState synchronized
        scope.launch {
            callSessionManager.activeCallSession.collect { activeSession ->
                if (activeSession != null && activeSession.callId.startsWith("sim_")) {
                    _engineState.update { current ->
                        current.copy(
                            isRunning = true,
                            currentCallState = activeSession.state,
                            elapsedActiveSeconds = activeSession.durationSeconds
                        )
                    }
                } else if (_engineState.value.isRunning && activeSession == null) {
                    _engineState.update { current ->
                        current.copy(
                            isRunning = false,
                            currentCallState = CallState.DISCONNECTED
                        )
                    }
                }
            }
        }
    }

    /**
     * Launches a simulation based on a [SimulationPreset].
     */
    fun launchPreset(preset: SimulationPreset, simSlot: Int = 0) {
        val config = SimulatedCallConfig(
            callId = "sim_${preset.name.lowercase()}_${System.currentTimeMillis()}",
            direction = preset.direction,
            callerName = preset.defaultCallerName,
            phoneNumber = preset.defaultPhoneNumber,
            companyName = preset.defaultCompanyName,
            crmStatus = preset.defaultLeadStatus,
            priority = preset.defaultPriority,
            simSlot = simSlot,
            autoAnswerDelaySec = preset.defaultAutoAnswerSec,
            autoHangupDurationSec = preset.defaultAutoHangupSec,
            recordToDatabase = true,
            preset = preset
        )

        startSimulation(config)

        // Special scenario: Call waiting dual call
        if (preset == SimulationPreset.CALL_WAITING_DUAL) {
            scheduleSecondaryCallScenario(config)
        }
    }

    /**
     * Starts an incoming or outgoing simulated call with the given configuration.
     */
    fun startSimulation(config: SimulatedCallConfig) {
        cancelActiveJobs()

        val normalized = PhoneNumberNormalizer.normalize(config.phoneNumber)
        val installedSims = simRepository.getInstalledSims()
        val simInfo = installedSims.getOrNull(config.simSlot) ?: SimInfo(
            subscriptionId = config.simSlot + 1,
            slotIndex = config.simSlot,
            carrierName = config.simDisplayName,
            displayName = config.simDisplayName,
            phoneNumber = "+1 (555) 000-${config.simSlot}",
            isActive = true
        )

        val initialState = if (config.direction == CallDirection.INCOMING) {
            CallState.RINGING
        } else {
            CallState.CONNECTING
        }

        val initialDisplayName = if (config.callerName.isNotBlank()) config.callerName else "Test Caller"

        val session = CallSessionState(
            callId = config.callId,
            phoneNumber = config.phoneNumber,
            normalizedPhoneNumber = normalized,
            direction = config.direction,
            state = initialState,
            callerDisplayName = initialDisplayName,
            companyName = config.companyName,
            crmStatus = config.crmStatus,
            priority = config.priority,
            personId = config.personId,
            initials = computeInitials(initialDisplayName),
            simInfo = simInfo,
            capabilities = CallCapabilities(
                canHold = true,
                canMute = true,
                canSwap = true,
                canMerge = true
            ),
            connectTimeMillis = 0L,
            durationSeconds = 0
        )

        DeveloperLogger.info(
            "SIMULATOR_START",
            "Launching simulated call: id=${config.callId}, dir=${config.direction}, caller=$initialDisplayName, num=${config.phoneNumber}"
        )

        _engineState.update {
            SimulatedEngineState(
                isRunning = true,
                activeConfig = config,
                currentCallState = initialState,
                elapsedActiveSeconds = 0,
                isSecondaryCallActive = false,
                secondaryConfig = null
            )
        }

        // Forward to CallSessionManager
        callSessionManager.addSimulatedSession(session)

        // Handle automated timer flows
        if (config.direction == CallDirection.INCOMING) {
            if (config.autoAnswerDelaySec > 0) {
                autoAnswerJob = scope.launch {
                    delay(config.autoAnswerDelaySec * 1000L)
                    DeveloperLogger.info("SIMULATOR_AUTO_ANSWER", "Auto-answering incoming call ${config.callId}")
                    answerSimulatedCall(config.callId)

                    if (config.autoHangupDurationSec > 0) {
                        scheduleAutoHangup(config.callId, config.autoHangupDurationSec)
                    }
                }
            } else if (config.autoHangupDurationSec > 0) {
                // E.g. missed call test: rings for X seconds without answer, then hangs up
                autoHangupJob = scope.launch {
                    delay(config.autoHangupDurationSec * 1000L)
                    DeveloperLogger.info("SIMULATOR_AUTO_MISSED", "Auto-terminating unanswered call ${config.callId}")
                    hangupSimulatedCall(config.callId)
                }
            }
        } else {
            // Outgoing call simulation: starts in CONNECTING, then auto-connects to ACTIVE
            val connectDelaySec = if (config.autoAnswerDelaySec > 0) config.autoAnswerDelaySec else 2
            autoAnswerJob = scope.launch {
                delay(connectDelaySec * 1000L)
                DeveloperLogger.info("SIMULATOR_REMOTE_ANSWER", "Remote party answered outgoing call ${config.callId}")
                answerSimulatedCall(config.callId)

                if (config.autoHangupDurationSec > 0) {
                    scheduleAutoHangup(config.callId, config.autoHangupDurationSec)
                }
            }
        }
    }

    /**
     * Manually triggers simulated remote party answering an outgoing call (or answering incoming call).
     */
    fun remoteAnswer(callId: String? = null) {
        val targetId = callId ?: _engineState.value.activeConfig?.callId ?: return
        answerSimulatedCall(targetId)
    }

    /**
     * Manually triggers simulated remote party ending the call.
     */
    fun remoteHangup(callId: String? = null) {
        val targetId = callId ?: _engineState.value.activeConfig?.callId ?: return
        hangupSimulatedCall(targetId)
    }

    /**
     * Spawns a 2nd simultaneous incoming call while a 1st call is currently ongoing.
     */
    fun simulateSecondaryIncomingCall(
        callerName: String = "Sarah Connor (Cyberdyne)",
        phoneNumber: String = "+1 (555) 902-3311",
        companyName: String? = "Cyberdyne Systems",
        simSlot: Int = 1
    ) {
        val secondaryCallId = "sim_sec_${System.currentTimeMillis()}"
        val installedSims = simRepository.getInstalledSims()
        val simInfo = installedSims.getOrNull(simSlot) ?: SimInfo(
            subscriptionId = simSlot + 1,
            slotIndex = simSlot,
            carrierName = "SIM 2",
            displayName = "SIM 2",
            phoneNumber = "+1 (555) 000-2",
            isActive = true
        )

        val secConfig = SimulatedCallConfig(
            callId = secondaryCallId,
            direction = CallDirection.INCOMING,
            callerName = callerName,
            phoneNumber = phoneNumber,
            companyName = companyName,
            simSlot = simSlot,
            simDisplayName = simInfo.displayName
        )

        val secSession = CallSessionState(
            callId = secondaryCallId,
            phoneNumber = phoneNumber,
            normalizedPhoneNumber = PhoneNumberNormalizer.normalize(phoneNumber),
            direction = CallDirection.INCOMING,
            state = CallState.RINGING,
            callerDisplayName = callerName,
            companyName = companyName,
            crmStatus = com.example.callog.domain.model.LeadStatus.HOT,
            priority = com.example.callog.domain.model.LeadPriority.HIGH,
            initials = computeInitials(callerName),
            simInfo = simInfo,
            capabilities = CallCapabilities(canHold = true, canMute = true, canSwap = true, canMerge = true)
        )

        DeveloperLogger.info("SIMULATOR_SECONDARY_CALL", "Spawning secondary incoming call: $secondaryCallId")
        _engineState.update { it.copy(isSecondaryCallActive = true, secondaryConfig = secConfig) }
        callSessionManager.addSimulatedSession(secSession)
    }

    /**
     * Schedules a secondary call 4 seconds into an active call.
     */
    private fun scheduleSecondaryCallScenario(primaryConfig: SimulatedCallConfig) {
        secondaryCallJob = scope.launch {
            // Wait for call to connect
            delay(4000L)
            if (_engineState.value.isRunning) {
                simulateSecondaryIncomingCall(
                    callerName = "Alexander Wright (Vanguard)",
                    phoneNumber = "+1 (555) 831-7744",
                    companyName = "Vanguard Holdings",
                    simSlot = 1
                )
            }
        }
    }

    private fun answerSimulatedCall(callId: String) {
        autoAnswerJob?.cancel()
        autoAnswerJob = null
        callSessionManager.executeAction(CallAction.Answer(callId))
    }

    private fun hangupSimulatedCall(callId: String) {
        cancelActiveJobs()
        callSessionManager.executeAction(CallAction.Disconnect(callId))
    }

    private fun scheduleAutoHangup(callId: String, durationSec: Int) {
        autoHangupJob?.cancel()
        autoHangupJob = scope.launch {
            delay(durationSec * 1000L)
            DeveloperLogger.info("SIMULATOR_AUTO_HANGUP", "Auto-hanging up call $callId after ${durationSec}s")
            hangupSimulatedCall(callId)
        }
    }

    /**
     * Saves a simulated call record to local Room database so it populates Call Logs,
     * Contact Timelines, and CRM Analytics just like a real call.
     */
    suspend fun persistCompletedCall(
        session: CallSessionState,
        outcomeLabel: String = "Connected",
        notes: String? = null,
        isFavorite: Boolean = false
    ) = withContext(Dispatchers.IO) {
        try {
            val callTypeString = when {
                session.direction == CallDirection.INCOMING && session.durationSeconds > 0 -> "INCOMING"
                session.direction == CallDirection.INCOMING && session.durationSeconds == 0 -> "MISSED"
                session.direction == CallDirection.OUTGOING -> "OUTGOING"
                else -> "INCOMING"
            }

            val timestamp = if (session.connectTimeMillis > 0) session.connectTimeMillis else System.currentTimeMillis()
            val generatedId = System.currentTimeMillis()

            val callEntity = CallEntity(
                id = generatedId,
                name = session.callerDisplayName,
                number = session.phoneNumber,
                duration = session.durationSeconds,
                timestamp = timestamp,
                callType = callTypeString,
                recordingPath = null,
                phoneAccountId = session.simInfo?.subscriptionId?.toString(),
                phoneAccountComponentName = session.simInfo?.displayName,
                isFavorite = isFavorite,
                notes = notes ?: (if (session.companyName != null) "Company: ${session.companyName}" else null),
                syncStatus = "PENDING",
                personId = session.personId
            )

            callDao.insertCall(callEntity)
            DeveloperLogger.info("SIMULATOR_DB_SAVED", "Saved simulated CallEntity #$generatedId for ${session.phoneNumber}")

            // Insert SalesCallEntity for sales pipeline CRM analytics
            val salesCallEntity = SalesCallEntity(
                salespersonPhone = "Simulated Device",
                salespersonName = "Sales Rep",
                buyerPhone = session.phoneNumber,
                buyerName = session.callerDisplayName,
                callType = callTypeString,
                callId = generatedId,
                duration = session.durationSeconds,
                personId = session.personId,
                syncStatus = "PENDING"
            )
            salesCallDao.insertSalesCall(salesCallEntity)

            _engineState.update {
                it.copy(
                    lastCompletedCallSummary = "${session.callerDisplayName} (${session.phoneNumber}) • ${session.durationSeconds}s"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting simulated call to Room DB", e)
        }
    }

    /**
     * Cancels any currently active simulation and resets state.
     */
    fun cancelSimulation() {
        cancelActiveJobs()
        val activeCallId = _engineState.value.activeConfig?.callId
        if (activeCallId != null) {
            callSessionManager.removeSimulatedSession(activeCallId)
        }
        val secCallId = _engineState.value.secondaryConfig?.callId
        if (secCallId != null) {
            callSessionManager.removeSimulatedSession(secCallId)
        }

        _engineState.update {
            SimulatedEngineState(
                isRunning = false,
                activeConfig = null,
                currentCallState = CallState.DISCONNECTED
            )
        }
        DeveloperLogger.info("SIMULATOR_CANCEL", "Simulation cancelled and reset.")
    }

    private fun cancelActiveJobs() {
        autoAnswerJob?.cancel()
        autoAnswerJob = null
        autoHangupJob?.cancel()
        autoHangupJob = null
        secondaryCallJob?.cancel()
        secondaryCallJob = null
    }

    private fun computeInitials(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(1).uppercase()
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        }
    }
}
