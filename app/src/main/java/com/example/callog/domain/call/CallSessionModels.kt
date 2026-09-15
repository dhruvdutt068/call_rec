package com.example.callog.domain.call

import com.example.callog.domain.model.LeadPriority
import com.example.callog.domain.model.LeadStatus
import com.example.callog.sim.SimInfo

enum class CallDirection {
    INCOMING,
    OUTGOING,
    UNKNOWN
}

enum class CallState {
    RINGING,
    CONNECTING,
    ACTIVE,
    ON_HOLD,
    DISCONNECTING,
    DISCONNECTED
}

enum class AudioRoute {
    EARPIECE,
    SPEAKER,
    BLUETOOTH,
    WIRED_HEADSET
}

data class CallCapabilities(
    val canHold: Boolean = false,
    val canMute: Boolean = true,
    val canSwap: Boolean = false,
    val canMerge: Boolean = false
)

data class CallAudioModel(
    val isMuted: Boolean = false,
    val route: AudioRoute = AudioRoute.EARPIECE,
    val supportedRoutes: List<AudioRoute> = listOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER)
)

data class CallSessionState(
    val callId: String,
    val phoneNumber: String,
    val normalizedPhoneNumber: String = "",
    val direction: CallDirection = CallDirection.UNKNOWN,
    val state: CallState = CallState.CONNECTING,
    val personId: String? = null,
    val callerDisplayName: String = "Unknown Caller",
    val companyName: String? = null,
    val crmStatus: LeadStatus = LeadStatus.UNKNOWN,
    val priority: LeadPriority = LeadPriority.MEDIUM,
    val avatarUrl: String? = null,
    val initials: String = "?",
    val simInfo: SimInfo? = null,
    val capabilities: CallCapabilities = CallCapabilities(),
    val durationSeconds: Int = 0,
    val connectTimeMillis: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isOnHold: Boolean = false,
    val isKeypadVisible: Boolean = false,
    val recentInteractionSummary: String? = null,
    val pendingFollowUp: String? = null
) {
    val isRinging: Boolean
        get() = state == CallState.RINGING

    val isActive: Boolean
        get() = state == CallState.ACTIVE

    val isTerminated: Boolean
        get() = state == CallState.DISCONNECTED || state == CallState.DISCONNECTING
}

sealed interface CallAction {
    data class Answer(val callId: String) : CallAction
    data class Reject(val callId: String) : CallAction
    data class RejectWithMessage(val callId: String, val message: String) : CallAction
    data class Disconnect(val callId: String) : CallAction
    data class ToggleMute(val callId: String) : CallAction
    data class ToggleSpeaker(val callId: String) : CallAction
    data class ToggleHold(val callId: String) : CallAction
    data object SwapCalls : CallAction
    data class MergeCalls(val callId1: String, val callId2: String) : CallAction
    data class SetAudioRoute(val route: AudioRoute) : CallAction
    data class SetKeypadVisibility(val callId: String, val visible: Boolean) : CallAction
    data class SendDtmf(val callId: String, val digit: Char) : CallAction
}
