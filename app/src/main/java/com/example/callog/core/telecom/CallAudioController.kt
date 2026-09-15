package com.example.callog.core.telecom

import android.content.Context
import android.media.AudioManager
import android.telecom.CallAudioState
import android.util.Log
import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.data.telecom.TelecomCallController
import com.example.callog.domain.call.AudioRoute
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UI State representing call audio routing and mute status.
 */
data class CallAudioUiState(
    val route: AudioRoute = AudioRoute.EARPIECE,
    val availableRoutes: Set<AudioRoute> = setOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER),
    val muted: Boolean = false
)

/**
 * Authoritative controller for managing audio route selection (Earpiece, Speaker, Bluetooth, Wired Headset)
 * and microphone mute states.
 */
@Singleton
class CallAudioController @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CallAudioController"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var telecomController: TelecomCallController? = null

    private val _audioUiState = MutableStateFlow(CallAudioUiState())
    val audioUiState: StateFlow<CallAudioUiState> = _audioUiState.asStateFlow()

    fun attachTelecomController(controller: TelecomCallController) {
        telecomController = controller
    }

    fun detachTelecomController(controller: TelecomCallController) {
        if (telecomController === controller) {
            telecomController = null
        }
    }

    /**
     * Updates internal state when Telecom framework fires onCallAudioStateChanged.
     */
    fun onTelecomAudioStateChanged(callAudioState: CallAudioState) {
        val currentRoute = when (callAudioState.route) {
            CallAudioState.ROUTE_SPEAKER -> AudioRoute.SPEAKER
            CallAudioState.ROUTE_BLUETOOTH -> AudioRoute.BLUETOOTH
            CallAudioState.ROUTE_WIRED_HEADSET -> AudioRoute.WIRED_HEADSET
            else -> AudioRoute.EARPIECE
        }

        val available = mutableSetOf<AudioRoute>()
        if ((callAudioState.supportedRouteMask and CallAudioState.ROUTE_EARPIECE) != 0) available.add(AudioRoute.EARPIECE)
        if ((callAudioState.supportedRouteMask and CallAudioState.ROUTE_SPEAKER) != 0) available.add(AudioRoute.SPEAKER)
        if ((callAudioState.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH) != 0) available.add(AudioRoute.BLUETOOTH)
        if ((callAudioState.supportedRouteMask and CallAudioState.ROUTE_WIRED_HEADSET) != 0) available.add(AudioRoute.WIRED_HEADSET)

        DeveloperLogger.info(
            "CALL_AUDIO_STATE",
            "Route: $currentRoute, Available: $available, Muted: ${callAudioState.isMuted}"
        )

        _audioUiState.update {
            it.copy(
                route = currentRoute,
                availableRoutes = available,
                muted = callAudioState.isMuted
            )
        }
    }

    /**
     * Sets the desired audio route via Telecom InCallService or AudioManager fallback.
     */
    fun setAudioRoute(route: AudioRoute) {
        DeveloperLogger.info("SET_AUDIO_ROUTE", "Requested audio route: $route")
        val controller = telecomController
        if (controller != null) {
            controller.setAudioRoute(route)
        } else {
            // Fallback to AudioManager
            val isSpeaker = route == AudioRoute.SPEAKER
            try {
                audioManager?.isSpeakerphoneOn = isSpeaker
                _audioUiState.update { it.copy(route = route) }
            } catch (e: Exception) {
                Log.e(TAG, "Error switching audio route via AudioManager", e)
            }
        }
    }

    /**
     * Sets microphone mute state.
     */
    fun setMuted(shouldMute: Boolean) {
        DeveloperLogger.info("SET_CALL_MUTED", "Requested mute: $shouldMute")
        val controller = telecomController
        if (controller != null) {
            controller.setCallMuted(shouldMute)
        } else {
            try {
                audioManager?.isMicrophoneMute = shouldMute
                _audioUiState.update { it.copy(muted = shouldMute) }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling microphone mute via AudioManager", e)
            }
        }
    }

    /**
     * Toggles speaker on or off (switching between SPEAKER and EARPIECE).
     */
    fun toggleSpeaker() {
        val currentRoute = _audioUiState.value.route
        val targetRoute = if (currentRoute == AudioRoute.SPEAKER) AudioRoute.EARPIECE else AudioRoute.SPEAKER
        setAudioRoute(targetRoute)
    }

    /**
     * Toggles microphone mute.
     */
    fun toggleMute() {
        val currentMute = _audioUiState.value.muted
        setMuted(!currentMute)
    }
}
