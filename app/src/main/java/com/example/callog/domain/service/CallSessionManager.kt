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
import com.example.callog.domain.model.Lead
import com.example.callog.domain.model.LeadPriority
import com.example.callog.domain.model.LeadSource
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
import java.util.concurrent.ConcurrentHashMap
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
    private val dialerRoleManager: DialerRoleManager,
    private val callRecordingController: com.example.callog.domain.call.CallRecordingController,
    private val recordingRepository: com.example.callog.domain.repository.RecordingRepository,
    private val contactRingtoneSyncManager: com.example.callog.data.provider.ContactRingtoneSyncManager? = null,
    private val leadMasterDao: com.example.callog.data.local.dao.LeadMasterDao? = null,
    private val crmRepository: com.example.callog.domain.crm.repository.CrmRepository? = null
) {
    companion object {
        private const val TAG = "CallSessionManager"
        const val CRM_LOOKUP_TIMEOUT_MS = 350L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tickerJob: Job? = null
    private val crmResolutionJobs = ConcurrentHashMap<String, Job>()
    val recordingState: StateFlow<com.example.callog.domain.call.CallRecordingState> = callRecordingController.recordingState

    private var telecomController: TelecomCallController? = null

    // Call Sessions Map: callId -> CallSessionState
    private val _sessionsMap = MutableStateFlow<Map<String, CallSessionState>>(emptyMap())

    private val _callSessions = MutableStateFlow<List<CallSessionState>>(emptyList())
    val callSessions: StateFlow<List<CallSessionState>> = _callSessions.asStateFlow()

    // Active primary call session (first ringing or active or connecting call)
    private val _activeCallSession = MutableStateFlow<CallSessionState?>(null)
    val activeCallSession: StateFlow<CallSessionState?> = _activeCallSession.asStateFlow()

    // Audio state
    private val _audioState = MutableStateFlow(CallAudioModel())
    val audioState: StateFlow<CallAudioModel> = _audioState.asStateFlow()

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

    init {
        scope.launch {
            callRecordingController.recordingState.collect { recState ->
                DeveloperLogger.info(TAG, "Recording state updated: $recState")
                updateSessionsMap { current ->
                    current.mapValues { (_, session) ->
                        session.copy(recordingState = recState)
                    }
                }
            }
        }
    }

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
            val job = scope.launch {
                resolvePersonAndLead(
                    callId = callId,
                    rawNumber = rawNumber.ifBlank { initialSession.phoneNumber },
                    normalizedNumber = normalized.ifBlank { initialSession.normalizedPhoneNumber },
                    simInfo = simInfo,
                    isRinging = initialState == CallState.RINGING
                )
            }
            crmResolutionJobs[callId] = job
        }

        handleStateChange(callId, initialState)
        startTickerIfNeeded()
    }

    /**
     * Resolves the canonical person from PersonRepository and associated CRM lead data.
     * Coordinates ringtone playback during cold starts to avoid default tone cut-off glitches.
     */
    private suspend fun resolvePersonAndLead(
        callId: String,
        rawNumber: String,
        normalizedNumber: String,
        simInfo: SimInfo?,
        isRinging: Boolean = false
    ) {
        try {
            DeveloperLogger.info("CRM_LOOKUP_STARTED", "Starting CRM lookup for call $callId (isRinging=$isRinging)")
            var person: Person? = null
            var lead: Lead? = null
            var timedOut = false

            if (isRinging) {
                // Bounded lookup period for cold-start ringtone determination
                val lookupResult = withTimeoutOrNull(CRM_LOOKUP_TIMEOUT_MS) {
                    val p = if (normalizedNumber.isNotBlank()) {
                        personRepository.findPersonByNormalizedPhone(normalizedNumber)
                    } else null
                    val l = p?.let { leadRepository.getLeadForPerson(it.id) }
                    Pair(p, l)
                }
                if (lookupResult != null) {
                    person = lookupResult.first
                    lead = lookupResult.second
                } else {
                    timedOut = true
                    DeveloperLogger.warning(
                        "CRM_LOOKUP_TIMEOUT",
                        "CRM lookup exceeded ${CRM_LOOKUP_TIMEOUT_MS}ms for $callId. Starting fallback tone."
                    )
                }
            } else {
                person = if (normalizedNumber.isNotBlank()) {
                    personRepository.findPersonByNormalizedPhone(normalizedNumber)
                } else null
                lead = person?.let { leadRepository.getLeadForPerson(it.id) }
            }

            // Verify call is still active/ringing before continuing
            val curSession = _sessionsMap.value[callId]
            if (curSession == null || (isRinging && curSession.state != CallState.RINGING)) {
                DeveloperLogger.info("CRM_LOOKUP_ABORTED", "Call $callId is no longer ringing. Skipping ringtone and session updates.")
                return
            }

            if (person != null) {
                DeveloperLogger.info(
                    "CANONICAL_PERSON_RESOLVED",
                    "Resolved canonical Person: ${person.displayName} (id=${person.id}) for $rawNumber"
                )

                val recentCalls = callRepository.getCallsForPerson(person.id)
                val lastCall = recentCalls.firstOrNull()
                val interactionSummary = lastCall?.let {
                    "Last call: ${it.timestamp.toRelativeTimeSpan()} (${it.callType.lowercase()})"
                }

                val followUpSummary = lead?.nextFollowUpAt?.let {
                    "Follow-up: ${it.toRelativeTimeSpan()}"
                }

                val resolutionState = if (lead != null) CrmResolutionState.RESOLVED else CrmResolutionState.RESOLVED_NO_LEAD

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
                        pendingFollowUp = followUpSummary,
                        crmResolutionState = resolutionState
                    ))
                }

                val isDeviceHandlingRingtone = contactRingtoneSyncManager?.isContactAlreadyInDevice(rawNumber) ?: false
                val currentSession = _sessionsMap.value[callId]
                if (currentSession?.state == CallState.RINGING) {
                    if (!isDeviceHandlingRingtone) {
                        ringtoneController.startRingtone(callId, person, lead)
                    } else {
                        DeveloperLogger.info("RINGTONE_NATIVE_DELEGATED", "Contact already exists in device contacts. Native Samsung dialer handling ringer.")
                    }
                    notificationManager.showIncomingCallNotification(currentSession)
                } else if (currentSession?.state == CallState.ACTIVE) {
                    notificationManager.showActiveCallNotification(currentSession)
                }
            } else {
                val isDeviceHandlingRingtone = contactRingtoneSyncManager?.isContactAlreadyInDevice(rawNumber) ?: false
                var leadMasterEntity: com.example.callog.data.local.entity.LeadMasterEntity? = null
                val digits = rawNumber.filter { it.isDigit() }
                val mobileDouble = digits.toDoubleOrNull()
                val last10Double = if (digits.length >= 10) digits.takeLast(10).toDoubleOrNull() else null
                if (leadMasterDao != null) {
                    try {
                        if (last10Double != null) {
                            leadMasterEntity = leadMasterDao.getLeadMasterByMobile(last10Double)
                        }
                        if (leadMasterEntity == null && mobileDouble != null) {
                            leadMasterEntity = leadMasterDao.getLeadMasterByMobile(mobileDouble)
                        }
                    } catch (_: Exception) {}
                }

                if (leadMasterEntity != null) {
                    DeveloperLogger.info("CRM_LEAD_MASTER_RESOLVED", "Resolved LeadMasterEntity for unknown number $rawNumber: ${leadMasterEntity.leadName}")
                    val status = contactRingtoneSyncManager?.resolveLeadStatus(leadMasterEntity.fkLeadStatus, leadMasterEntity.fkLeadStage) ?: LeadStatus.NEW
                    val syntheticLead = Lead(
                        id = "lead_${leadMasterEntity.id}",
                        personId = "person_${leadMasterEntity.id}",
                        status = status,
                        priority = LeadPriority.MEDIUM,
                        source = LeadSource.CALL
                    )
                    val syntheticPerson = Person(
                        id = "person_${leadMasterEntity.id}",
                        displayName = leadMasterEntity.leadName?.ifBlank { null } ?: "Lead #${leadMasterEntity.id}",
                        companyName = leadMasterEntity.leadSource ?: "",
                        phoneNumbers = listOf(
                            com.example.callog.domain.model.PhoneNumber(
                                id = "pn_${leadMasterEntity.id}",
                                personId = "person_${leadMasterEntity.id}",
                                phoneNumber = rawNumber,
                                normalizedNumber = rawNumber
                            )
                        )
                    )

                    updateSessionsMap { current ->
                        val existing = current[callId] ?: return@updateSessionsMap current
                        current + (callId to existing.copy(
                            personId = syntheticPerson.id,
                            callerDisplayName = syntheticPerson.displayName,
                            companyName = syntheticPerson.companyName,
                            crmStatus = status,
                            priority = syntheticLead.priority,
                            initials = computeInitials(syntheticPerson.displayName),
                            crmResolutionState = CrmResolutionState.RESOLVED
                        ))
                    }

                    val currentSession = _sessionsMap.value[callId]
                    if (currentSession?.state == CallState.RINGING) {
                        if (!isDeviceHandlingRingtone) {
                            val token = ringtoneController.nextGenerationToken()
                            ringtoneController.startRingtone(callId, syntheticPerson, syntheticLead, expectedToken = token)
                        } else {
                            DeveloperLogger.info("RINGTONE_NATIVE_DELEGATED", "CRM contact already in device contacts. Native Samsung dialer handling ringer.")
                        }
                        notificationManager.showIncomingCallNotification(currentSession)
                    }

                    // Asynchronously insert/sync this lead into device contacts so future calls ring natively
                    scope.launch {
                        contactRingtoneSyncManager?.insertOrUpdateSingleLead(leadMasterEntity)
                    }
                } else if (contactRingtoneSyncManager == null) {
                    if (isRinging) {
                        val currentSession = _sessionsMap.value[callId]
                        if (currentSession?.state == CallState.RINGING) {
                            ringtoneController.startRingtone(callId, null, null)
                        }
                    }
                } else {
                    DeveloperLogger.info("UNKNOWN_CALLER_NON_CRM", "Caller $rawNumber is not a CRM lead. Native Samsung dialer playing default tone.")
                }

                // Query Android Contacts for system caller name
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
                            avatarUrl = contactPhoto,
                            crmResolutionState = CrmResolutionState.RESOLVED_NO_LEAD
                        ))
                    }
                } else {
                    updateSessionsMap { current ->
                        val existing = current[callId] ?: return@updateSessionsMap current
                        current + (callId to existing.copy(
                            crmResolutionState = if (timedOut) CrmResolutionState.FAILED else CrmResolutionState.RESOLVED_NO_LEAD
                        ))
                    }
                }

                val currentSession = _sessionsMap.value[callId]
                if (currentSession?.state == CallState.RINGING) {
                    notificationManager.showIncomingCallNotification(currentSession)
                } else if (currentSession?.state == CallState.ACTIVE) {
                    notificationManager.showActiveCallNotification(currentSession)
                }
            }
        } catch (e: CancellationException) {
            DeveloperLogger.info("CRM_LOOKUP_CANCELLED", "CRM lookup for call $callId was cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving canonical person for call $callId", e)
            DeveloperLogger.error("CRM_LOOKUP_FAILED", "Exception resolving person for $callId", e)
            updateSessionsMap { current ->
                val existing = current[callId] ?: return@updateSessionsMap current
                current + (callId to existing.copy(crmResolutionState = CrmResolutionState.FAILED))
            }
            if (isRinging) {
                val currentSession = _sessionsMap.value[callId]
                if (currentSession?.state == CallState.RINGING) {
                    ringtoneController.startRingtone(callId, null, null)
                }
            }
        } finally {
            crmResolutionJobs.remove(callId)
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

        if (newState != CallState.RINGING) {
            crmResolutionJobs.remove(callId)?.cancel()
        }

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
                val inDevice = contactRingtoneSyncManager?.isContactAlreadyInDevice(session?.phoneNumber ?: "") ?: false
                if (session?.personId != null) {
                    scope.launch {
                        val person = personRepository.getPersonById(session.personId)
                        val lead = person?.let { leadRepository.getLeadForPerson(it.id) }
                        if (_sessionsMap.value[callId]?.state == CallState.RINGING) {
                            if (!inDevice) {
                                ringtoneController.startRingtone(callId, person, lead)
                            } else {
                                DeveloperLogger.info("RINGTONE_NATIVE_DELEGATED", "Contact already in device contacts. Delegating ring to Samsung dialer.")
                            }
                        }
                    }
                } else if (!crmResolutionJobs.containsKey(callId)) {
                    val job = scope.launch {
                        resolvePersonAndLead(
                            callId = callId,
                            rawNumber = session?.phoneNumber ?: "",
                            normalizedNumber = session?.normalizedPhoneNumber ?: "",
                            simInfo = session?.simInfo,
                            isRinging = true
                        )
                    }
                    crmResolutionJobs[callId] = job
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

                // Auto-stop recording if active or starting
                if (callRecordingController.recordingState.value is com.example.callog.domain.call.CallRecordingState.Recording ||
                    callRecordingController.recordingState.value is com.example.callog.domain.call.CallRecordingState.Starting) {
                    stopRecording()
                }

                // If incoming call was never answered, show missed call notification
                if (session != null && session.direction == CallDirection.INCOMING && session.connectTimeMillis == 0L) {
                    notificationManager.showMissedCallNotification(session)
                }

                // Staggered OEM recording scans to capture newly written audio file from native dialer
                triggerPostCallRecordingScans(callId)

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
        if (callRecordingController.recordingState.value is com.example.callog.domain.call.CallRecordingState.Recording ||
            callRecordingController.recordingState.value is com.example.callog.domain.call.CallRecordingState.Starting) {
            stopRecording()
        }
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
                } else {
                    // Call was handled via external native dialer: trigger OEM recording scan
                    triggerPostCallRecordingScans(callId)
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
                crmResolutionJobs.remove(action.callId)?.cancel()
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
                crmResolutionJobs.remove(action.callId)?.cancel()
                ringtoneController.stopRingtone()
                updateSessionsMap { current ->
                    val existing = current[action.callId] ?: return@updateSessionsMap current
                    current + (action.callId to existing.copy(state = CallState.DISCONNECTED))
                }
                if (controller != null && !action.callId.startsWith("sim_")) {
                    controller.rejectCall(action.callId)
                } else if (!action.callId.startsWith("sim_")) {
                    tryEndCallFallback()
                    handleStateChange(action.callId, CallState.DISCONNECTED)
                } else {
                    handleStateChange(action.callId, CallState.DISCONNECTED)
                }
            }
            is CallAction.RejectWithMessage -> {
                crmResolutionJobs.remove(action.callId)?.cancel()
                ringtoneController.stopRingtone()
                updateSessionsMap { current ->
                    val existing = current[action.callId] ?: return@updateSessionsMap current
                    current + (action.callId to existing.copy(state = CallState.DISCONNECTED))
                }
                if (controller != null && !action.callId.startsWith("sim_")) {
                    controller.rejectCallWithMessage(action.callId, action.message)
                } else if (!action.callId.startsWith("sim_")) {
                    tryEndCallFallback()
                    handleStateChange(action.callId, CallState.DISCONNECTED)
                } else {
                    handleStateChange(action.callId, CallState.DISCONNECTED)
                }
            }
            is CallAction.Disconnect -> {
                crmResolutionJobs.remove(action.callId)?.cancel()
                ringtoneController.stopRingtone()
                updateSessionsMap { current ->
                    val existing = current[action.callId] ?: return@updateSessionsMap current
                    current + (action.callId to existing.copy(state = CallState.DISCONNECTED))
                }
                if (controller != null && !action.callId.startsWith("sim_")) {
                    controller.disconnectCall(action.callId)
                } else if (!action.callId.startsWith("sim_")) {
                    tryEndCallFallback()
                    handleStateChange(action.callId, CallState.DISCONNECTED)
                } else {
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
            is CallAction.ToggleRecording -> {
                val currentRecState = callRecordingController.recordingState.value
                if (currentRecState is com.example.callog.domain.call.CallRecordingState.Recording ||
                    currentRecState is com.example.callog.domain.call.CallRecordingState.Starting) {
                    stopRecording()
                } else {
                    startRecording(action.callId)
                }
            }
            is CallAction.StopRecording -> {
                stopRecording()
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

    fun startRecording(callId: String) {
        val session = _sessionsMap.value[callId] ?: _activeCallSession.value
        if (session == null) {
            DeveloperLogger.warning(TAG, "Cannot start recording: No call session found for callId=$callId")
            return
        }
        if (session.state != CallState.ACTIVE && session.state != CallState.CONNECTING) {
            DeveloperLogger.warning(TAG, "Cannot start recording: Call is in non-recordable state ${session.state}")
            return
        }
        callHapticManager.vibrateActionToggle()
        scope.launch {
            val contactName = session.callerDisplayName.takeIf {
                it.isNotBlank() && !it.equals("Unknown Caller", ignoreCase = true)
            }
            val result = callRecordingController.start(
                callId = session.callId,
                phoneNumber = session.phoneNumber,
                contactName = contactName,
                personId = session.personId,
                simSlot = session.simInfo?.subscriptionId
            )
            if (result.isFailure) {
                DeveloperLogger.error(TAG, "Recording start failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun stopRecording() {
        callHapticManager.vibrateActionToggle()
        scope.launch {
            val result = callRecordingController.stop()
            result.onSuccess { session ->
                DeveloperLogger.info(TAG, "Recording finalized: ${session.filePath}. Registering in Room.")
                val regResult = recordingRepository.registerCompletedRecording(session)
                regResult.onSuccess { entity ->
                    DeveloperLogger.info(TAG, "RecordingEntity persisted with id=${entity.id}, matchedCallId=${entity.matchedCallId}")
                }.onFailure { err ->
                    DeveloperLogger.error(TAG, "Failed to persist RecordingEntity: ${err.message}", err)
                }
            }.onFailure { err ->
                DeveloperLogger.warning(TAG, "Recording stop returned failure or no session: ${err.message}")
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

    private fun triggerPostCallRecordingScans(callId: String) {
        scope.launch {
            try {
                DeveloperLogger.info(TAG, "Starting post-call staggered OEM recording scans for callId=$callId")
                // Pass 1: 1.5s after disconnect (fast OEM flush)
                delay(1500)
                recordingRepository.scanRecordings()

                // Pass 2: 3.5s after disconnect (standard OEM flush)
                delay(2000)
                recordingRepository.scanRecordings()

                // Pass 3: 6.5s after disconnect (slower indexing / media store flush)
                delay(3000)
                recordingRepository.scanRecordings()
                DeveloperLogger.info(TAG, "Finished post-call staggered OEM recording scans for callId=$callId")
            } catch (e: Exception) {
                DeveloperLogger.error(TAG, "Error in post-call recording scans: ${e.message}", e)
            }
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
