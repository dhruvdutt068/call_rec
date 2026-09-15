package com.example.callog.data.telecom

import com.example.callog.domain.call.AudioRoute

/**
 * Interface abstracting direct Android Telecom actions so that [CallSessionManager]
 * can remain testable, pure, and decoupled from Android Service instances.
 */
interface TelecomCallController {
    fun answerCall(callId: String)
    fun rejectCall(callId: String)
    fun rejectCallWithMessage(callId: String, textMessage: String)
    fun disconnectCall(callId: String)
    fun setCallMuted(shouldMute: Boolean)
    fun setAudioRoute(route: AudioRoute)
    fun holdCall(callId: String)
    fun unholdCall(callId: String)
    fun swapCalls()
    fun mergeCalls(callId1: String, callId2: String)
    fun playDtmfTone(callId: String, digit: Char)
    fun stopDtmfTone(callId: String)
}
