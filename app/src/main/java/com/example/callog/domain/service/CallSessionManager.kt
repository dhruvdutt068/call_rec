package com.example.callog.domain.service

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telecom.TelecomManager
import android.util.Log
import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.core.extensions.toRelativeTimeSpan
import com.example.callog.core.telecom.CallAudioController
import com.example.callog.core.telecom.CallHapticManager
import com.example.callog.core.telecom.DtmfTonePlayer
import com.example.callog.core.telecom.ProximityController
import com.example.callog.core.telecom.RingtoneController
import com.example.callog.core.utils.PhoneNumberNormalizer
import com.example.callog.data.telecom.CallNotificationManager
import com.example.callog.data.telecom.TelecomCallController
import com.example.callog.domain.call.*
import com.example.callog.domain.model.LeadPriority
import com.example.callog.domain.model.LeadStatus
import com.example.callog.domain.model.Person
import com.example.callog.domain.repository.CallRepository
import com.example.callog.domain.repository.LeadRepository
import com.example.callog.domain.repository.PersonRepository
import com.example.callog.sim.SimInfo
import com.example.callog.sim.SimRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallSessionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val personRepository: PersonRepository,
    private val leadRepository: LeadRepository,
    private val callRepository: CallRepository,
    private val simRepository: SimRepository,
    private val ringtoneController: RingtoneController,
    private val callAudioController: CallAudioController,
    private val proximityController: ProximityController,
    private val notificationManager: CallNotificationManager,
    private val callHapticManager: CallHapticManager,
    private val dtmfTonePlayer: DtmfTonePlayer
) {
    companion object {
        private const val TAG = "CallSessionManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tickerJob: Job? = null

    private var telecomController: TelecomCallController? = null

    // Call Sessions Map: callId -> CallSessionState
    private val _sessionsMap = MutableStateFlow<Map<String, CallSessionState>>(emptyMap())
    val callSessions: StateFlow<List<CallSessionState>> = _sessionsMap
        .map { it.values.toList() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Active primary call session (first ringing or active or connecting call)
    val activeCallSession: StateFlow<CallSessionState?> = _sessionsMap
        .map { map ->
            val list = map.values.toList()
            // Priority: Ringing first, then Active, then Connecting, then OnHold, then any
            list.firstOrNull { it.state == CallState.RINGING }
                ?: list.firstOrNull { it.state == CallState.ACTIVE }
                ?: list.firstOrNull { it.state == CallState.CONNECTING }
                ?: list.firstOrNull { it.state == CallState.ON_HOLD }
                ?: list.firstOrNull()
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    // Audio state
    private val _audioState = MutableStateFlow(CallAudioModel())
    val audioState: StateFlow<CallAudioModel> = _audioState.asStateFlow()

    fun registerTelecomController(controller: TelecomCallController) {
        telecomController = controller
        callAudioController.attachTelecomController(controller)
    }

    fun unregisterTelecomController(controller: TelecomCallController) {
        if (telecomController === controller) {
            telecomController = null
            callAudioController.detachTelecomController(controller)
        }
    }

    /**
     * Silences the active incoming ringtone without rejecting or ending the call.
     */
    fun silenceRinger() {
        DeveloperLogger.info("RING_SILENCED", "Silencing incoming ringtone upon Telecom onSilenceRinger event.")
        ringtoneController.silenceRinger()
    }

    /**
     * Pre-seeds an outgoing call session before Telecom InCallService onCallAdded arrives.
     */
    fun startOutgoingCall(number: String, simSlot: Int = 0) {
        val normalized = PhoneNumberNormalizer.normalize(number)
        val callId = "outgoing_${System.currentTimeMillis()}"
        val initialDisplayName = if (number.isNotBlank()) number else "Connecting..."
        val simInfo = simRepository.getInstalledSims().getOrNull(simSlot)

        val session = CallSessionState(
            callId = callId,
            phoneNumber = number,
            normalizedPhoneNumber = normalized,
            direction = CallDirection.OUTGOING,
            state = CallState.CONNECTING,
            callerDisplayName = initialDisplayName,
            initials = computeInitials(initialDisplayName),
            simInfo = simInfo
        )

        _sessionsMap.update { current ->
            val cleaned = current.filterKeys { !it.startsWith("outgoing_") }
            cleaned + (callId to session)
        }

        scope.launch {
            resolvePersonAndLead(callId, number, normalized, simInfo)
        }

        handleStateChange(callId, CallState.CONNECTING)
        startTickerIfNeeded()
    }

    /**
     * Called by InCallService when a new Telecom call is added.
     */
    fun onTelecomCallAdded(
        callId: String,
        rawNumber: String,
        telecomDisplayName: String?,
        direction: CallDirection,
        initialState: CallState,
        capabilities: CallCapabilities,
        accountHandleId: String?,
        accountComponentName: String?
    ) {
        DeveloperLogger.info(
            "TELECOM_CALL_ADDED",
            "Call received: id=$callId, num=$rawNumber, direction=$direction, state=$initialState"
        )

        val normalized = PhoneNumberNormalizer.normalize(rawNumber)
        val initialDisplayName = when {
            !telecomDisplayName.isNullOrBlank() -> telecomDisplayName
            rawNumber.isNotBlank() -> rawNumber
            else -> "Unknown Caller"
        }

        // SIM identification
        val subId = simRepository.resolveSubscriptionId(accountHandleId, accountComponentName)
        val simInfo = simRepository.getInstalledSims().find { it.subscriptionId == subId }

        // Find existing pre-session if any
        val existingPreSession = _sessionsMap.value.values.find {
            it.callId.startsWith("outgoing_") &&
                (it.normalizedPhoneNumber == normalized || it.phoneNumber == rawNumber ||
                 (normalized.isNotEmpty() && (normalized.endsWith(it.normalizedPhoneNumber) || it.normalizedPhoneNumber.endsWith(normalized))))
        }

        val initialSession = CallSessionState(
            callId = callId,
            phoneNumber = rawNumber.ifBlank { existingPreSession?.phoneNumber ?: "" },
            normalizedPhoneNumber = normalized.ifBlank { existingPreSession?.normalizedPhoneNumber ?: "" },
            direction = direction,
            state = initialState,
            callerDisplayName = existingPreSession?.callerDisplayName ?: initialDisplayName,
            companyName = existingPreSession?.companyName,
            crmStatus = existingPreSession?.crmStatus ?: LeadStatus.UNKNOWN,
            priority = existingPreSession?.priority ?: LeadPriority.MEDIUM,
            initials = existingPreSession?.initials ?: computeInitials(initialDisplayName),
            recentInteractionSummary = existingPreSession?.recentInteractionSummary,
            pendingFollowUp = existingPreSession?.pendingFollowUp,
            personId = existingPreSession?.personId,
            simInfo = simInfo ?: existingPreSession?.simInfo,
            capabilities = capabilities,
            connectTimeMillis = if (initialState == CallState.ACTIVE) System.currentTimeMillis() else (existingPreSession?.connectTimeMillis ?: 0L)
        )

        _sessionsMap.update { current ->
            val cleaned = if (existingPreSession != null) current - existingPreSession.callId else current
            cleaned + (callId to initialSession)
        }

        // Canonical identity & CRM resolution if not already resolved
        if (existingPreSession?.personId == null) {
            scope.launch {
                resolvePersonAndLead(callId, rawNumber.ifBlank { initialSession.phoneNumber }, normalized.ifBlank { initialSession.normalizedPhoneNumber }, simInfo)
            }
        }

        handleStateChange(callId, initialState)
        startTickerIfNeeded()
    }

    /**
     * Resolves the canonical person from PersonRepository and associated CRM lead data.
     */
    private suspend fun resolvePersonAndLead(
        callId: String,
        rawNumber: String,
        normalizedNumber: String,
        simInfo: SimInfo?
    ) {
        try {
            val person: Person? = if (normalizedNumber.isNotBlank()) {
                personRepository.findPersonByNormalizedPhone(normalizedNumber)
            } else null

            if (person != null) {
                DeveloperLogger.info(
                    "CANONICAL_PERSON_RESOLVED",
                    "Resolved canonical Person: ${person.displayName} (id=${person.id}) for $rawNumber"
                )

                val lead = leadRepository.getLeadForPerson(person.id)
                val recentCalls = callRepository.getCallsForPerson(person.id)
                val lastCall = recentCalls.firstOrNull()
                val interactionSummary = lastCall?.let {
                    "Last call: ${it.timestamp.toRelativeTimeSpan()} (${it.callType.lowercase()})"
                }

                val followUpSummary = lead?.nextFollowUpAt?.let {
                    "Follow-up: ${it.toRelativeTimeSpan()}"
                }

                _sessionsMap.update { current ->
                    val existing = current[callId] ?: return@update current
                    current + (callId to existing.copy(
                        personId = person.id,
                        callerDisplayName = person.displayName,
                        companyName = person.companyName,
                        crmStatus = lead?.status ?: LeadStatus.UNKNOWN,
                        priority = lead?.priority ?: LeadPriority.MEDIUM,
                        initials = computeInitials(person.displayName),
                        recentInteractionSummary = interactionSummary,
                        pendingFollowUp = followUpSummary
                    ))
                }

                // If currently ringing, update ringtone and notification with resolved CRM metadata
                val currentSession = _sessionsMap.value[callId]
                if (currentSession?.state == CallState.RINGING) {
                    ringtoneController.startRingtone(person, lead)
                    notificationManager.showIncomingCallNotification(currentSession)
                } else if (currentSession?.state == CallState.ACTIVE) {
                    notificationManager.showActiveCallNotification(currentSession)
                }
            } else {
                DeveloperLogger.info(
                    "CANONICAL_PERSON_NOT_FOUND",
                    "No canonical Person found for $rawNumber. Treating as Unknown / Local contact."
                )
                val currentSession = _sessionsMap.value[callId]
                if (currentSession?.state == CallState.RINGING) {
                    ringtoneController.startRingtone(null, null)
                    notificationManager.showIncomingCallNotification(currentSession)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving canonical person for call $callId", e)
        }
    }

    /**
     * Called when Telecom call state changes.
     */
    fun onTelecomCallStateChanged(
        callId: String,
        newState: CallState,
        capabilities: CallCapabilities? = null
    ) {
        DeveloperLogger.info(
            "TELECOM_CALL_STATE_CHANGED",
            "Call $callId state changed to: $newState"
        )

        _sessionsMap.update { current ->
            val existing = current[callId] ?: return@update current
            val updatedConnectTime = if (newState == CallState.ACTIVE && existing.connectTimeMillis == 0L) {
                System.currentTimeMillis()
            } else {
                existing.connectTimeMillis
            }

            current + (callId to existing.copy(
                state = newState,
                capabilities = capabilities ?: existing.capabilities,
                isOnHold = newState == CallState.ON_HOLD,
                connectTimeMillis = updatedConnectTime
            ))
        }

        handleStateChange(callId, newState)
        startTickerIfNeeded()
    }

    private fun handleStateChange(callId: String, state: CallState) {
        val session = _sessionsMap.value[callId]
        when (state) {
            CallState.RINGING -> {
                if (session != null) {
                    notificationManager.showIncomingCallNotification(session)
                }
                scope.launch {
                    val person = session?.personId?.let { personRepository.getPersonById(it) }
                    val lead = person?.let { leadRepository.getLeadForPerson(it.id) }
                    ringtoneController.startRingtone(person, lead)
                }
                launchInCallActivity()
            }
            CallState.ACTIVE -> {
                ringtoneController.stopRingtone()
                callHapticManager.vibrateCallConnected()
                proximityController.onCallStateOrRouteChanged(true, _audioState.value.route)
                if (session != null) {
                    notificationManager.showActiveCallNotification(session)
                }
                launchInCallActivity()
            }
            CallState.CONNECTING, CallState.ON_HOLD -> {
                if (session != null) {
                    notificationManager.showActiveCallNotification(session)
                }
                launchInCallActivity()
            }
            CallState.DISCONNECTED -> {
                ringtoneController.stopRingtone()
                callHapticManager.vibrateCallEnded()
                proximityController.release()
                notificationManager.cancelCallNotification()

                // If incoming call was never answered, show missed call notification
                if (session != null && session.direction == CallDirection.INCOMING && session.connectTimeMillis == 0L) {
                    notificationManager.showMissedCallNotification(session)
                }

                // Auto clean up after brief timeout so UI can show "Call Ended"
                scope.launch {
                    delay(1500)
                    onTelecomCallRemoved(callId)
                }
            }
            else -> {
                ringtoneController.stopRingtone()
            }
        }
    }

    /**
     * Called when Telecom call is completely removed.
     */
    fun onTelecomCallRemoved(callId: String) {
        DeveloperLogger.info("TELECOM_CALL_REMOVED", "Call $callId removed.")
        _sessionsMap.update { current -> current - callId }

        if (_sessionsMap.value.none { it.value.state == CallState.RINGING }) {
            ringtoneController.stopRingtone()
        }

        if (_sessionsMap.value.isEmpty()) {
            notificationManager.cancelCallNotification()
            tickerJob?.cancel()
            tickerJob = null
        }
    }

    /**
     * Forwards audio state updates from InCallService.
     */
    fun onAudioStateChanged(isMuted: Boolean, route: AudioRoute, supportedRoutes: List<AudioRoute>) {
        _audioState.update {
            it.copy(isMuted = isMuted, route = route, supportedRoutes = supportedRoutes)
        }

        proximityController.onCallStateOrRouteChanged(activeCallSession.value?.state == CallState.ACTIVE, route)

        _sessionsMap.update { current ->
            current.mapValues { (_, session) ->
                session.copy(
                    isMuted = isMuted,
                    isSpeakerOn = route == AudioRoute.SPEAKER
                )
            }
        }

        val active = activeCallSession.value
        if (active != null && active.state == CallState.ACTIVE) {
            notificationManager.showActiveCallNotification(active.copy(
                isMuted = isMuted,
                isSpeakerOn = route == AudioRoute.SPEAKER
            ))
        }
    }

    /**
     * Fallback telephony state change when InCallService is not active.
     */
    fun onTelephonyCallStateChanged(
        state: CallState,
        number: String = "",
        direction: CallDirection = CallDirection.UNKNOWN
    ) {
        val existingSession = _sessionsMap.value.values.firstOrNull()
        if (existingSession != null && telecomController != null) {
            // Telecom InCallService is already active and managing this call
            return
        }

        val callId = existingSession?.callId ?: "call_${System.currentTimeMillis()}"

        when (state) {
            CallState.RINGING, CallState.CONNECTING -> {
                val rawNumber = if (number.isNotBlank()) number else existingSession?.phoneNumber ?: ""
                val normalized = PhoneNumberNormalizer.normalize(rawNumber)
                val initialDisplayName = if (rawNumber.isNotBlank()) rawNumber else "Incoming Call"

                val session = existingSession?.copy(
                    state = state,
                    phoneNumber = rawNumber,
                    normalizedPhoneNumber = normalized,
                    direction = direction
                ) ?: CallSessionState(
                    callId = callId,
                    phoneNumber = rawNumber,
                    normalizedPhoneNumber = normalized,
                    direction = direction,
                    state = state,
                    callerDisplayName = initialDisplayName,
                    initials = computeInitials(initialDisplayName)
                )

                _sessionsMap.update { mapOf(callId to session) }
                scope.launch {
                    resolvePersonAndLead(callId, rawNumber, normalized, null)
                }
                handleStateChange(callId, state)
                startTickerIfNeeded()
            }
            CallState.ACTIVE -> {
                val rawNumber = if (number.isNotBlank()) number else existingSession?.phoneNumber ?: ""
                val normalized = PhoneNumberNormalizer.normalize(rawNumber)
                val initialDisplayName = if (rawNumber.isNotBlank()) rawNumber else "Active Call"

                val session = existingSession?.copy(
                    state = CallState.ACTIVE,
                    connectTimeMillis = if (existingSession.connectTimeMillis == 0L) System.currentTimeMillis() else existingSession.connectTimeMillis
                ) ?: CallSessionState(
                    callId = callId,
                    phoneNumber = rawNumber,
                    normalizedPhoneNumber = normalized,
                    direction = direction,
                    state = CallState.ACTIVE,
                    callerDisplayName = initialDisplayName,
                    initials = computeInitials(initialDisplayName),
                    connectTimeMillis = System.currentTimeMillis()
                )

                _sessionsMap.update { mapOf(callId to session) }
                handleStateChange(callId, CallState.ACTIVE)
                startTickerIfNeeded()
            }
            CallState.DISCONNECTED -> {
                if (existingSession != null) {
                    _sessionsMap.update {
                        mapOf(callId to existingSession.copy(state = CallState.DISCONNECTED))
                    }
                    handleStateChange(callId, CallState.DISCONNECTED)
                }
            }
            else -> {}
        }
    }

    /**
     * UI action dispatcher.
     */
    fun executeAction(action: CallAction) {
        val controller = telecomController
        if (controller == null) {
            Log.w(TAG, "TelecomCallController is not connected. Executing via system telephony/audio manager fallback.")
        }

        when (action) {
            is CallAction.Answer -> {
                ringtoneController.stopRingtone()
                // Auto-hold active call if answering an incoming call while already on a call
                val currentActive = _sessionsMap.value.values.find { it.state == CallState.ACTIVE && it.callId != action.callId }
                if (currentActive != null) {
                    DeveloperLogger.info("AUTO_HOLD", "Auto-holding call ${currentActive.callId} to answer incoming ${action.callId}")
                    controller?.holdCall(currentActive.callId)
                }

                if (controller != null) {
                    controller.answerCall(action.callId)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    try {
                        telecomManager?.acceptRingingCall()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to acceptRingingCall", e)
                    }
                }
            }
            is CallAction.Reject -> {
                ringtoneController.stopRingtone()
                if (controller != null) {
                    controller.rejectCall(action.callId)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    try {
                        telecomManager?.endCall()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to endCall", e)
                    }
                }
            }
            is CallAction.RejectWithMessage -> {
                ringtoneController.stopRingtone()
                if (controller != null) {
                    controller.rejectCallWithMessage(action.callId, action.message)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    try {
                        telecomManager?.endCall()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to endCall", e)
                    }
                }
            }
            is CallAction.Disconnect -> {
                ringtoneController.stopRingtone()
                if (controller != null) {
                    controller.disconnectCall(action.callId)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    try {
                        telecomManager?.endCall()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to endCall", e)
                    }
                }
            }
            is CallAction.ToggleMute -> {
                callHapticManager.vibrateActionToggle()
                callAudioController.toggleMute()
            }
            is CallAction.ToggleSpeaker -> {
                callHapticManager.vibrateActionToggle()
                callAudioController.toggleSpeaker()
                proximityController.onCallStateOrRouteChanged(
                    activeCallSession.value?.state == CallState.ACTIVE,
                    callAudioController.audioUiState.value.route
                )
            }
            is CallAction.SetAudioRoute -> {
                callHapticManager.vibrateActionToggle()
                callAudioController.setAudioRoute(action.route)
                proximityController.onCallStateOrRouteChanged(
                    activeCallSession.value?.state == CallState.ACTIVE,
                    action.route
                )
            }
            is CallAction.ToggleHold -> {
                callHapticManager.vibrateActionToggle()
                val session = _sessionsMap.value[action.callId]
                if (session != null) {
                    if (session.isOnHold) {
                        controller?.unholdCall(action.callId)
                    } else {
                        controller?.holdCall(action.callId)
                    }
                }
            }
            is CallAction.SwapCalls -> {
                callHapticManager.vibrateActionToggle()
                controller?.swapCalls()
            }
            is CallAction.MergeCalls -> {
                callHapticManager.vibrateActionToggle()
                controller?.mergeCalls(action.callId1, action.callId2)
            }
            is CallAction.SetKeypadVisibility -> {
                _sessionsMap.update { current ->
                    val existing = current[action.callId] ?: return@update current
                    current + (action.callId to existing.copy(isKeypadVisible = action.visible))
                }
            }
            is CallAction.SendDtmf -> {
                dtmfTonePlayer.playTone(action.digit)
                controller?.playDtmfTone(action.callId, action.digit)
                scope.launch {
                    delay(180)
                    controller?.stopDtmfTone(action.callId)
                }
            }
        }
    }

    private fun startTickerIfNeeded() {
        if (tickerJob != null && tickerJob?.isActive == true) return

        tickerJob = scope.launch {
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                _sessionsMap.update { current ->
                    current.mapValues { (_, session) ->
                        if (session.state == CallState.ACTIVE && session.connectTimeMillis > 0L) {
                            val elapsedSec = ((now - session.connectTimeMillis) / 1000).toInt().coerceAtLeast(0)
                            val updated = session.copy(durationSeconds = elapsedSec)
                            notificationManager.showActiveCallNotification(updated)
                            updated
                        } else {
                            session
                        }
                    }
                }
            }
        }
    }

    private fun launchInCallActivity() {
        try {
            val intent = Intent(context, com.example.callog.presentation.call.InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch InCallActivity", e)
        }
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
