package com.example.callog.presentation.call

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callog.domain.call.CallAction
import com.example.callog.domain.call.CallSessionState
import com.example.callog.domain.call.CallState
import com.example.callog.data.local.entity.ReminderEntity
import com.example.callog.domain.repository.CallRepository
import com.example.callog.domain.repository.LeadRepository
import com.example.callog.domain.service.CallSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CallUiState {
    data object Idle : CallUiState
    data class Incoming(val session: CallSessionState) : CallUiState
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
    private val callRepository: CallRepository
) : ViewModel() {

    val uiState: StateFlow<CallUiState> = combine(
        callSessionManager.activeCallSession,
        callSessionManager.callSessions
    ) { activeSession, allSessions ->
        if (activeSession == null) {
            CallUiState.Idle
        } else {
            when (activeSession.state) {
                CallState.RINGING -> {
                    CallUiState.Incoming(session = activeSession)
                }
                CallState.CONNECTING, CallState.ACTIVE, CallState.ON_HOLD -> {
                    val others = allSessions.filter { it.callId != activeSession.callId }
                    CallUiState.Active(
                        session = activeSession,
                        otherSessions = others
                    )
                }
                CallState.DISCONNECTING, CallState.DISCONNECTED -> {
                    CallUiState.Ended(
                        session = activeSession,
                        reason = if (activeSession.durationSeconds > 0) "Call Ended" else "Call Declined"
                    )
                }
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        CallUiState.Idle
    )

    fun onAction(action: CallAction) {
        callSessionManager.executeAction(action)
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
        }
    }
}
