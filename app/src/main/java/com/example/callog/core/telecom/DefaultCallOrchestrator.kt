package com.example.callog.core.telecom

import com.example.callog.domain.call.AudioRoute
import com.example.callog.domain.call.CallAction
import com.example.callog.domain.call.CallSessionState
import com.example.callog.domain.service.CallSessionManager
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultCallOrchestrator @Inject constructor(
    private val callSessionManager: CallSessionManager,
    private val callAudioController: CallAudioController
) : CallOrchestrator {

    override val callSessions: StateFlow<List<CallSessionState>> = callSessionManager.callSessions
    override val activeCallSession: StateFlow<CallSessionState?> = callSessionManager.activeCallSession
    override val audioUiState: StateFlow<CallAudioUiState> = callAudioController.audioUiState

    override fun onAction(action: CallAction) {
        callSessionManager.executeAction(action)
    }

    override fun answer(callId: String) {
        callSessionManager.executeAction(CallAction.Answer(callId))
    }

    override fun reject(callId: String) {
        callSessionManager.executeAction(CallAction.Reject(callId))
    }

    override fun rejectWithMessage(callId: String, message: String) {
        callSessionManager.executeAction(CallAction.RejectWithMessage(callId, message))
    }

    override fun disconnect(callId: String) {
        callSessionManager.executeAction(CallAction.Disconnect(callId))
    }

    override fun toggleHold(callId: String) {
        callSessionManager.executeAction(CallAction.ToggleHold(callId))
    }

    override fun swapCalls() {
        callSessionManager.executeAction(CallAction.SwapCalls)
    }

    override fun mergeCalls(callId1: String, callId2: String) {
        callSessionManager.executeAction(CallAction.MergeCalls(callId1, callId2))
    }

    override fun setAudioRoute(route: AudioRoute) {
        callAudioController.setAudioRoute(route)
    }

    override fun toggleMute() {
        callAudioController.toggleMute()
    }

    override fun toggleSpeaker() {
        callAudioController.toggleSpeaker()
    }

    override fun sendDtmf(callId: String, digit: Char) {
        callSessionManager.executeAction(CallAction.SendDtmf(callId, digit))
    }
}
