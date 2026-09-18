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
    private val dtmfTonePlayer: DtmfTonePlayer,
    private val contactsProvider: com.example.callog.data.provider.ContactsProvider,
    private val dialerRoleManager: DialerRoleManager
) {
    companion object {
        private const val TAG = "CallSessionManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tickerJob: Job? = null

    private var telecomController: TelecomCallController? = null

    // Call Sessions Map: callId -> CallSessionState
    private val _sessionsMap = MutableStateFlow<Map<String, CallSessionState>>(emptyMap())

    private val _callSessions = MutableStateFlow<List<CallSessionState>>(emptyList())
    val callSessions: StateFlow<List<CallSessionState>> = _callSessions.asStateFlow()

    // Active primary call session (first ringing or active or connecting call)
    private val _activeCallSession = MutableStateFlow<CallSessionState?>(null)
    val activeCallSession: StateFlow<CallSessionState?> = _activeCallSession.asStateFlow()

    private inline fun updateSessionsMap(transform: (Map<String, CallSessionState>) -> Map<String, CallSessionState>) {
        _sessionsMap.update { current ->
            val updated = transform(current)
            val list = updated.values.toList()
            _callSessions.value = list
            _activeCallSession.value = list.firstOrNull { it.state == CallState.RINGING }
                ?: list.firstOrNull { it.state == CallState.ACTIVE }
                ?: list.firstOrNull { it.state == CallState.CONNECTING }
                ?: list.firstOrNull { it.state == CallState.ON_HOLD }
                ?: list.firstOrNull()
            updated
        }
    }

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

        updateSessionsMap { current ->
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

        // Find existing pre-session if any (outgoing or telephony receiver pre-session matching this call)
        val existingPreSession = _sessionsMap.value.values.find {
            (it.callId.startsWith("outgoing_") || it.callId.startsWith("call_")) &&
                ((normalized.isNotBlank() && (it.normalizedPhoneNumber == normalized || normalized.endsWith(it.normalizedPhoneNumber) || it.normalizedPhoneNumber.endsWith(normalized))) ||
                 (rawNumber.isNotBlank() && it.phoneNumber == rawNumber) ||
                 (normalized.isBlank() && rawNumber.isBlank() && it.direction == direction))
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
            avatarUrl = existingPreSession?.avatarUrl,
            recentInteractionSummary = existingPreSession?.recentInteractionSummary,
            pendingFollowUp = existingPreSession?.pendingFollowUp,
            personId = existingPreSession?.personId,
            simInfo = simInfo ?: existingPreSession?.simInfo,
            capabilities = capabilities,
            connectTimeMillis = if (initialState == CallState.ACTIVE) System.currentTimeMillis() else (existingPreSession?.connectTimeMillis ?: 0L)
        )

        updateSessionsMap { current ->
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

                updateSessionsMap { current ->
                    val existing = current[callId] ?: return@updateSessionsMap current
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
                    "No canonical Person found for $rawNumber. Querying Android Contacts."
                )
                val contactInfo = contactsProvider.lookupContactByNumber(rawNumber.ifBlank { normalizedNumber })
                val contactName = contactInfo?.first
                val contactPhoto = contactInfo?.second

                if (!contactName.isNullOrBlank()) {
                    DeveloperLogger.info(
                        "SYSTEM_CONTACT_RESOLVED",
                        "Resolved system Contact: $contactName for $rawNumber"
                    )
                    updateSessionsMap { current ->
                        val existing = current[callId] ?: return@updateSessionsMap current
                        current + (callId to existing.copy(
                            callerDisplayName = contactName,
                            initials = computeInitials(contactName),
                            avatarUrl = contactPhoto
                        ))
                    }
                }

                val currentSession = _sessionsMap.value[callId]
                if (currentSession?.state == CallState.RINGING) {
                    ringtoneController.startRingtone(null, null)
                    notificationManager.showIncomingCallNotification(currentSession)
                } else if (currentSession?.state == CallState.ACTIVE) {
                    notificationManager.showActiveCallNotification(currentSession)
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

        updateSessionsMap { current ->
            val existing = current[callId] ?: return@updateSessionsMap current
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
        val isSimulated = callId.startsWith("sim_")
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
                launchInCallActivity(isSimulated)
            }
            CallState.ACTIVE -> {
                ringtoneController.stopRingtone()
                callHapticManager.vibrateCallConnected()
                proximityController.onCallStateOrRouteChanged(true, _audioState.value.route)
                if (session != null) {
                    notificationManager.showActiveCallNotification(session)
                }
                launchInCallActivity(isSimulated)
            }
            CallState.CONNECTING, CallState.ON_HOLD -> {
                if (session != null) {
                    notificationManager.showActiveCallNotification(session)
                }
                launchInCallActivity(isSimulated)
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
        updateSessionsMap { current -> current - callId }

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

        updateSessionsMap { current ->
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

        if (telecomController != null && (state == CallState.RINGING || state == CallState.CONNECTING)) {
            // Telecom InCallService will handle onCallAdded directly
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

                updateSessionsMap { mapOf(callId to session) }
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

                updateSessionsMap { mapOf(callId to session) }
                handleStateChange(callId, CallState.ACTIVE)
                startTickerIfNeeded()
            }
            CallState.DISCONNECTED -> {
                if (existingSession != null) {
                    updateSessionsMap {
                        mapOf(callId to existingSession.copy(state = CallState.DISCONNECTED))
                    }
                    handleStateChange(callId, CallState.DISCONNECTED)
                }
            }
            else -> {}
        }
    }

    /**
     * Adds a simulated call session and triggers appropriate in-call lifecycle states.
     */
    fun addSimulatedSession(session: CallSessionState) {
        DeveloperLogger.info("SIMULATED_SESSION_ADDED", "Adding simulated call session ${session.callId} (${session.direction})")
        updateSessionsMap { current ->
            current + (session.callId to session)
        }
        handleStateChange(session.callId, session.state)
        startTickerIfNeeded()
    }

    /**
     * Updates state for a simulated call session.
     */
    fun updateSimulatedSessionState(callId: String, newState: CallState) {
        DeveloperLogger.info("SIMULATED_STATE_UPDATE", "Updating simulated call $callId to $newState")
        updateSessionsMap { current ->
            val existing = current[callId] ?: return@updateSessionsMap current
            val updatedConnect = if (newState == CallState.ACTIVE && existing.connectTimeMillis == 0L) {
                System.currentTimeMillis()
            } else {
                existing.connectTimeMillis
            }
            current + (callId to existing.copy(
                state = newState,
                isOnHold = newState == CallState.ON_HOLD,
                connectTimeMillis = updatedConnect
            ))
        }
        handleStateChange(callId, newState)
        startTickerIfNeeded()
    }

    /**
     * Removes a simulated call session.
     */
    fun removeSimulatedSession(callId: String) {
        onTelecomCallRemoved(callId)
    }

    private fun tryAcceptRingingCallFallback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.ANSWER_PHONE_CALLS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    tm?.acceptRingingCall()
                    DeveloperLogger.info("TELECOM_FALLBACK", "Accepted ringing call via TelecomManager.acceptRingingCall()")
                }
            }
        } catch (e: Exception) {
            DeveloperLogger.error("TELECOM_FALLBACK", "Failed to accept ringing call via fallback: ${e.message}")
        }
    }

    private fun tryEndCallFallback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.ANSWER_PHONE_CALLS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    tm?.endCall()
                    DeveloperLogger.info("TELECOM_FALLBACK", "Ended call via TelecomManager.endCall()")
                }
            }
        } catch (e: Exception) {
            DeveloperLogger.error("TELECOM_FALLBACK", "Failed to end call via fallback: ${e.message}")
        }
    }

    /**
     * UI action dispatcher.
     */
    fun executeAction(action: CallAction) {
        val controller = telecomController
        if (controller == null) {
            Log.w(TAG, "TelecomCallController is not connected. Executing via system telephony/audio manager fallback or simulation dispatcher.")
        }

        when (action) {
            is CallAction.Answer -> {
                ringtoneController.stopRingtone()
                // Auto-hold active call if answering an incoming call while already on a call
                val currentActive = _sessionsMap.value.values.find { it.state == CallState.ACTIVE && it.callId != action.callId }
                if (currentActive != null) {
                    DeveloperLogger.info("AUTO_HOLD", "Auto-holding call ${currentActive.callId} to answer incoming ${action.callId}")
                    if (controller != null && !currentActive.callId.startsWith("sim_")) {
                        controller.holdCall(currentActive.callId)
                    } else {
                        updateSessionsMap { current ->
                            val existing = current[currentActive.callId] ?: return@updateSessionsMap current
                            current + (currentActive.callId to existing.copy(state = CallState.ON_HOLD, isOnHold = true))
                        }
                    }
                }

                if (controller != null && !action.callId.startsWith("sim_")) {
                    controller.answerCall(action.callId)
                } else if (!action.callId.startsWith("sim_")) {
                    tryAcceptRingingCallFallback()
                    updateSessionsMap { current ->
                        val existing = current[action.callId] ?: return@updateSessionsMap current
                        current + (action.callId to existing.copy(
                            state = CallState.ACTIVE,
                            isOnHold = false,
                            connectTimeMillis = System.currentTimeMillis()
                        ))
                    }
                    handleStateChange(action.callId, CallState.ACTIVE)
                    startTickerIfNeeded()
                } else {
                    updateSessionsMap { current ->
                        val existing = current[action.callId] ?: return@updateSessionsMap current
                        current + (action.callId to existing.copy(
                            state = CallState.ACTIVE,
                            isOnHold = false,
                            connectTimeMillis = System.currentTimeMillis()
                        ))
                    }
                    handleStateChange(action.callId, CallState.ACTIVE)
                    startTickerIfNeeded()
                }
            }
            is CallAction.Reject -> {
                ringtoneController.stopRingtone()
                if (controller != null && !action.callId.startsWith("sim_")) {
                    controller.rejectCall(action.callId)
                } else if (!action.callId.startsWith("sim_")) {
                    tryEndCallFallback()
                    updateSessionsMap { current ->
                        val existing = current[action.callId] ?: return@updateSessionsMap current
                        current + (action.callId to existing.copy(state = CallState.DISCONNECTED))
                    }
                    handleStateChange(action.callId, CallState.DISCONNECTED)
                } else {
                    updateSessionsMap { current ->
                        val existing = current[action.callId] ?: return@updateSessionsMap current
                        current + (action.callId to existing.copy(state = CallState.DISCONNECTED))
                    }
                    handleStateChange(action.callId, CallState.DISCONNECTED)
                }
            }
            is CallAction.RejectWithMessage -> {
                ringtoneController.stopRingtone()
                if (controller != null && !action.callId.startsWith("sim_")) {
                    controller.rejectCallWithMessage(action.callId, action.message)
                } else if (!action.callId.startsWith("sim_")) {
                    tryEndCallFallback()
                    updateSessionsMap { current ->
                        val existing = current[action.callId] ?: return@updateSessionsMap current
                        current + (action.callId to existing.copy(state = CallState.DISCONNECTED))
                    }
                    handleStateChange(action.callId, CallState.DISCONNECTED)
                } else {
                    updateSessionsMap { current ->
                        val existing = current[action.callId] ?: return@updateSessionsMap current
                        current + (action.callId to existing.copy(state = CallState.DISCONNECTED))
                    }
                    handleStateChange(action.callId, CallState.DISCONNECTED)
                }
            }
            is CallAction.Disconnect -> {
                ringtoneController.stopRingtone()
                if (controller != null && !action.callId.startsWith("sim_")) {
                    controller.disconnectCall(action.callId)
                } else if (!action.callId.startsWith("sim_")) {
                    tryEndCallFallback()
                    updateSessionsMap { current ->
                        val existing = current[action.callId] ?: return@updateSessionsMap current
                        current + (action.callId to existing.copy(state = CallState.DISCONNECTED))
                    }
                    handleStateChange(action.callId, CallState.DISCONNECTED)
                } else {
                    updateSessionsMap { current ->
                        val existing = current[action.callId] ?: return@updateSessionsMap current
                        current + (action.callId to existing.copy(state = CallState.DISCONNECTED))
                    }
                    handleStateChange(action.callId, CallState.DISCONNECTED)
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
                    if (controller != null && !action.callId.startsWith("sim_")) {
                        if (session.isOnHold) {
                            controller.unholdCall(action.callId)
                        } else {
                            controller.holdCall(action.callId)
                        }
                    } else {
                        val newHoldState = !session.isOnHold
                        val newState = if (newHoldState) CallState.ON_HOLD else CallState.ACTIVE
                        updateSessionsMap { current ->
                            val existing = current[action.callId] ?: return@updateSessionsMap current
                            current + (action.callId to existing.copy(state = newState, isOnHold = newHoldState))
                        }
                        handleStateChange(action.callId, newState)
                    }
                }
            }
            is CallAction.SwapCalls -> {
                callHapticManager.vibrateActionToggle()
                if (controller != null && _sessionsMap.value.keys.none { it.startsWith("sim_") }) {
                    controller.swapCalls()
                } else {
                    updateSessionsMap { current ->
                        current.mapValues { (_, s) ->
                            when (s.state) {
                                CallState.ACTIVE -> s.copy(state = CallState.ON_HOLD, isOnHold = true)
                                CallState.ON_HOLD -> s.copy(state = CallState.ACTIVE, isOnHold = false)
                                else -> s
                            }
                        }
                    }
                }
            }
            is CallAction.MergeCalls -> {
                callHapticManager.vibrateActionToggle()
                if (controller != null && _sessionsMap.value.keys.none { it.startsWith("sim_") }) {
                    controller.mergeCalls(action.callId1, action.callId2)
                } else {
                    updateSessionsMap { current ->
                        current.mapValues { (_, s) ->
                            s.copy(state = CallState.ACTIVE, isOnHold = false)
                        }
                    }
                }
            }
            is CallAction.SetKeypadVisibility -> {
                updateSessionsMap { current ->
                    val existing = current[action.callId] ?: return@updateSessionsMap current
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
                updateSessionsMap { current ->
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

    private fun launchInCallActivity(isSimulated: Boolean = false) {
        // Only launch custom in-call activity if Callog is the default dialer or Telecom InCallService is active, or if this is a simulated call
        if (!isSimulated && telecomController == null && !dialerRoleManager.isRoleHeld()) {
            DeveloperLogger.info("INCALL_LAUNCH_SKIPPED", "Skipping InCallActivity launch: App is not default dialer and InCallService is not bound.")
            return
        }
        try {
            val intent = Intent(context, com.example.callog.presentation.call.InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
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
