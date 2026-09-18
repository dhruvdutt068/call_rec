package com.example.callog.presentation.call

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.domain.call.CallAction
import com.example.callog.domain.call.CallSessionState
import com.example.callog.domain.call.CallState
import com.example.callog.domain.repository.CallRepository
import com.example.callog.domain.repository.LeadRepository
import com.example.callog.domain.service.CallSessionManager
import com.example.callog.domain.service.simulator.CallSimulatorManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CallUiState {
    data object NoSession : CallUiState
    data class Incoming(val session: CallSessionState) : CallUiState
    data class Connecting(
        val session: CallSessionState,
        val otherSessions: List<CallSessionState> = emptyList()
    ) : CallUiState
    data class Active(
        val session: CallSessionState,
        val otherSessions: List<CallSessionState> = emptyList()
    ) : CallUiState
    data class Ended(
        val session: CallSessionState,
        val reason: String = "Call Ended"
    ) : CallUiState
}

@HiltViewModel
class CallSessionViewModel @Inject constructor(
    private val callSessionManager: CallSessionManager,
    private val leadRepository: LeadRepository,
    private val callRepository: CallRepository,
    private val callSimulatorManager: CallSimulatorManager
) : ViewModel() {

    companion object {
        fun mapToUiState(activeSession: CallSessionState?, allSessions: List<CallSessionState>): CallUiState {
            if (activeSession == null) {
                return CallUiState.NoSession
            }
            val others = allSessions.filter { it.callId != activeSession.callId }
            return when (activeSession.state) {
                CallState.RINGING -> CallUiState.Incoming(session = activeSession)
                CallState.CONNECTING -> CallUiState.Connecting(session = activeSession, otherSessions = others)
                CallState.ACTIVE, CallState.ON_HOLD -> CallUiState.Active(
                    session = activeSession,
                    otherSessions = others
                )
                CallState.DISCONNECTING, CallState.DISCONNECTED -> CallUiState.Ended(
                    session = activeSession,
                    reason = if (activeSession.durationSeconds > 0) "Call Ended" else "Call Declined"
                )
            }
        }
    }

    val uiState: StateFlow<CallUiState> = combine(
        callSessionManager.activeCallSession,
        callSessionManager.callSessions
    ) { activeSession, allSessions ->
        val mapped = mapToUiState(activeSession, allSessions)
        DeveloperLogger.info("CALL_UI", "UI state mapped: $mapped (callId=${activeSession?.callId}, state=${activeSession?.state})")
        mapped
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        initialValue = mapToUiState(
            callSessionManager.activeCallSession.value,
            callSessionManager.callSessions.value
        )
    )

    fun onAction(action: CallAction) {
        callSessionManager.executeAction(action)
    }

    fun endCall(callId: String? = null) {
        val targetId = callId ?: callSessionManager.activeCallSession.value?.callId
        if (targetId != null) {
            DeveloperLogger.info("CALL_UI", "endCall invoked for callId=$targetId")
            callSessionManager.executeAction(CallAction.Disconnect(targetId))
        }
    }

    fun saveCallWrapUp(
        session: CallSessionState,
        outcome: CallOutcome,
        notes: String,
        isFavorite: Boolean,
        scheduleFollowUpDays: Int?
    ) {
        viewModelScope.launch {
            val personId = session.personId
            if (personId != null) {
                if (notes.isNotBlank()) {
                    leadRepository.updateLeadNotes(personId, notes)
                }
                if (scheduleFollowUpDays != null) {
                    val followUpTime = System.currentTimeMillis() + (scheduleFollowUpDays * 24L * 60L * 60L * 1000L)
                    leadRepository.updateLeadFollowUp(personId, followUpTime)
                }
            }

            // If this is a simulated call, persist it to Room DB (CallEntity & SalesCallEntity)
            if (session.callId.startsWith("sim_")) {
                callSimulatorManager.persistCompletedCall(
                    session = session,
                    outcomeLabel = outcome.label,
                    notes = notes,
                    isFavorite = isFavorite
                )
            }
        }
    }
}
