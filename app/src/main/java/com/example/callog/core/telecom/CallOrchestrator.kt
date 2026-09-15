package com.example.callog.core.telecom

import com.example.callog.domain.call.AudioRoute
import com.example.callog.domain.call.CallAction
import com.example.callog.domain.call.CallSessionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Top-level Orchestrator interface coordinating Telecom InCall live states,
 * audio routing, proximity sensing, ringtones, and UI action dispatches.
 */
interface CallOrchestrator {
    val callSessions: StateFlow<List<CallSessionState>>
    val activeCallSession: StateFlow<CallSessionState?>
    val audioUiState: StateFlow<CallAudioUiState>

    fun onAction(action: CallAction)
    fun answer(callId: String)
    fun reject(callId: String)
    fun rejectWithMessage(callId: String, message: String)
    fun disconnect(callId: String)
    fun toggleHold(callId: String)
    fun swapCalls()
    fun mergeCalls(callId1: String, callId2: String)
    fun setAudioRoute(route: AudioRoute)
    fun toggleMute()
    fun toggleSpeaker()
    fun sendDtmf(callId: String, digit: Char)
}
