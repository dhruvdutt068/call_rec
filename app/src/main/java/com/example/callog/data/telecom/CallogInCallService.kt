package com.example.callog.data.telecom

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.domain.call.AudioRoute
import com.example.callog.domain.call.CallCapabilities
import com.example.callog.domain.call.CallDirection
import com.example.callog.domain.call.CallState
import com.example.callog.domain.service.CallSessionManager
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class CallogInCallService : InCallService(), TelecomCallController {

    companion object {
        private const val TAG = "CallogInCallService"
    }

    @Inject
    lateinit var callSessionManager: CallSessionManager

    private val callMap = ConcurrentHashMap<String, Call>()
    private val callbackMap = ConcurrentHashMap<String, Call.Callback>()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "CallogInCallService created")
        DeveloperLogger.info("INCALL_SERVICE_LIFECYCLE", "CallogInCallService created and registered.")
        callSessionManager.registerTelecomController(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "CallogInCallService destroyed")
        DeveloperLogger.info("INCALL_SERVICE_LIFECYCLE", "CallogInCallService destroyed.")
        callSessionManager.unregisterTelecomController(this)
        callbackMap.forEach { (id, cb) ->
            callMap[id]?.unregisterCallback(cb)
        }
        callbackMap.clear()
        callMap.clear()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val callId = getCallId(call)
        callMap[callId] = call

        val details = call.details
        val handle = details?.handle
        val rawNumber = handle?.schemeSpecificPart ?: ""
        val displayName = details?.callerDisplayName

        val direction = when (details?.callDirection) {
            Call.Details.DIRECTION_INCOMING -> CallDirection.INCOMING
            Call.Details.DIRECTION_OUTGOING -> CallDirection.OUTGOING
            else -> CallDirection.UNKNOWN
        }

        val initialState = mapCallState(call.state)
        val capabilities = mapCapabilities(details?.callCapabilities ?: 0)
        val accountHandle = details?.accountHandle

        val callback = object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                val newState = mapCallState(state)
                val caps = mapCapabilities(c.details?.callCapabilities ?: 0)
                callSessionManager.onTelecomCallStateChanged(callId, newState, caps)
            }

            override fun onDetailsChanged(c: Call, d: Call.Details) {
                val newState = mapCallState(c.state)
                val caps = mapCapabilities(d.callCapabilities)
                callSessionManager.onTelecomCallStateChanged(callId, newState, caps)
            }
        }

        callbackMap[callId] = callback
        call.registerCallback(callback)

        callSessionManager.onTelecomCallAdded(
            callId = callId,
            rawNumber = rawNumber,
            telecomDisplayName = displayName,
            direction = direction,
            initialState = initialState,
            capabilities = capabilities,
            accountHandleId = accountHandle?.id,
            accountComponentName = accountHandle?.componentName?.flattenToString()
        )
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        val callId = getCallId(call)
        callbackMap.remove(callId)?.let { cb ->
            call.unregisterCallback(cb)
        }
        callMap.remove(callId)
        callSessionManager.onTelecomCallRemoved(callId)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        val route = when (audioState.route) {
            CallAudioState.ROUTE_SPEAKER -> AudioRoute.SPEAKER
            CallAudioState.ROUTE_BLUETOOTH -> AudioRoute.BLUETOOTH
            CallAudioState.ROUTE_WIRED_HEADSET -> AudioRoute.WIRED_HEADSET
            else -> AudioRoute.EARPIECE
        }

        val supported = mutableListOf<AudioRoute>()
        if ((audioState.supportedRouteMask and CallAudioState.ROUTE_EARPIECE) != 0) supported.add(AudioRoute.EARPIECE)
        if ((audioState.supportedRouteMask and CallAudioState.ROUTE_SPEAKER) != 0) supported.add(AudioRoute.SPEAKER)
        if ((audioState.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH) != 0) supported.add(AudioRoute.BLUETOOTH)
        if ((audioState.supportedRouteMask and CallAudioState.ROUTE_WIRED_HEADSET) != 0) supported.add(AudioRoute.WIRED_HEADSET)

        callSessionManager.onAudioStateChanged(audioState.isMuted, route, supported)
    }

    override fun onSilenceRinger() {
        super.onSilenceRinger()
        Log.i(TAG, "Telecom onSilenceRinger received")
        DeveloperLogger.info("INCALL_SILENCE_RINGER", "Telecom requested ringtone silence")
        callSessionManager.silenceRinger()
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        Log.i(TAG, "onBringToForeground called, showDialpad=$showDialpad")
        try {
            val intent = Intent(this, com.example.callog.presentation.call.InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("EXTRA_SHOW_DIALPAD", showDialpad)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error bringing InCallActivity to foreground", e)
        }
    }

    // ── TelecomCallController implementation ───────────────────────────────────

    override fun answerCall(callId: String) {
        val call = callMap[callId]
        if (call != null) {
            try {
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
            } catch (e: Exception) {
                Log.e(TAG, "Error answering call $callId", e)
            }
        }
    }

    override fun rejectCall(callId: String) {
        val call = callMap[callId]
        if (call != null) {
            try {
                call.reject(false, null)
            } catch (e: Exception) {
                Log.e(TAG, "Error rejecting call $callId", e)
            }
        }
    }

    override fun rejectCallWithMessage(callId: String, textMessage: String) {
        val call = callMap[callId]
        if (call != null) {
            try {
                call.reject(true, textMessage)
            } catch (e: Exception) {
                Log.e(TAG, "Error rejecting call $callId with message", e)
            }
        }
    }

    override fun swapCalls() {
        val activeCalls = callMap.values.toList()
        val holdingCall = activeCalls.find { it.state == Call.STATE_HOLDING }
        val activeCall = activeCalls.find { it.state == Call.STATE_ACTIVE }
        try {
            activeCall?.hold()
            holdingCall?.unhold()
        } catch (e: Exception) {
            Log.e(TAG, "Error swapping calls", e)
        }
    }

    override fun mergeCalls(callId1: String, callId2: String) {
        val call1 = callMap[callId1]
        val call2 = callMap[callId2]
        if (call1 != null && call2 != null) {
            try {
                call1.conference(call2)
            } catch (e: Exception) {
                Log.e(TAG, "Error merging calls $callId1 and $callId2", e)
            }
        }
    }

    override fun disconnectCall(callId: String) {
        val call = callMap[callId]
        if (call != null) {
            try {
                call.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting call $callId", e)
            }
        }
    }

    override fun setCallMuted(shouldMute: Boolean) {
        try {
            setMuted(shouldMute)
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling mute to $shouldMute", e)
        }
    }

    override fun setAudioRoute(route: AudioRoute) {
        val telecomRoute = when (route) {
            AudioRoute.SPEAKER -> CallAudioState.ROUTE_SPEAKER
            AudioRoute.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
            AudioRoute.WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
            AudioRoute.EARPIECE -> CallAudioState.ROUTE_EARPIECE
        }
        try {
            setAudioRoute(telecomRoute)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting audio route to $route", e)
        }
    }

    override fun holdCall(callId: String) {
        val call = callMap[callId]
        if (call != null) {
            try {
                call.hold()
            } catch (e: Exception) {
                Log.e(TAG, "Error holding call $callId", e)
            }
        }
    }

    override fun unholdCall(callId: String) {
        val call = callMap[callId]
        if (call != null) {
            try {
                call.unhold()
            } catch (e: Exception) {
                Log.e(TAG, "Error unholding call $callId", e)
            }
        }
    }

    override fun playDtmfTone(callId: String, digit: Char) {
        val call = callMap[callId]
        if (call != null) {
            try {
                call.playDtmfTone(digit)
            } catch (e: Exception) {
                Log.e(TAG, "Error playing DTMF tone $digit for call $callId", e)
            }
        }
    }

    override fun stopDtmfTone(callId: String) {
        val call = callMap[callId]
        if (call != null) {
            try {
                call.stopDtmfTone()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping DTMF tone for call $callId", e)
            }
        }
    }

    // ── Helper mapping functions ──────────────────────────────────────────────

    private fun getCallId(call: Call): String {
        return System.identityHashCode(call).toString()
    }

    private fun mapCallState(state: Int): CallState {
        return when (state) {
            Call.STATE_RINGING -> CallState.RINGING
            Call.STATE_DIALING, Call.STATE_CONNECTING -> CallState.CONNECTING
            Call.STATE_ACTIVE -> CallState.ACTIVE
            Call.STATE_HOLDING -> CallState.ON_HOLD
            Call.STATE_DISCONNECTING -> CallState.DISCONNECTING
            Call.STATE_DISCONNECTED -> CallState.DISCONNECTED
            else -> CallState.CONNECTING
        }
    }

    private fun mapCapabilities(capabilities: Int): CallCapabilities {
        val canHold = (capabilities and Call.Details.CAPABILITY_HOLD) != 0 ||
                (capabilities and Call.Details.CAPABILITY_SUPPORT_HOLD) != 0
        val canMute = (capabilities and Call.Details.CAPABILITY_MUTE) != 0
        val canSwap = (capabilities and Call.Details.CAPABILITY_SWAP_CONFERENCE) != 0
        val canMerge = (capabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0

        return CallCapabilities(
            canHold = canHold,
            canMute = canMute,
            canSwap = canSwap,
            canMerge = canMerge
        )
    }
}
