package com.example.callog.core.telecom

import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.domain.model.Lead
import com.example.callog.domain.model.Person
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level manager coordinating CRM-driven dynamic ringtone playback,
 * lead status haptic feedback, and audio focus lifecycle.
 *
 * Implements requirements from Phase 3:
 * - Dynamic ringtones per CRM status (HOT, WARM, COLD, UNKNOWN)
 * - Distinct haptic profiles per lead status
 * - Explicit silence and stop actions
 * - Safe release of audio focus and vibrator resources
 */
@Singleton
class CallRingtoneManager @Inject constructor(
    private val ringtoneController: RingtoneController,
    private val hapticManager: CallHapticManager
) {
    val isRingtonePlaying: Boolean
        get() = ringtoneController.isRingtonePlaying

    /**
     * Start playing the ringtone and vibration configured for this caller and lead.
     */
    fun startRingtone(person: Person? = null, lead: Lead? = null) {
        DeveloperLogger.info(
            "CALL_RINGTONE_MANAGER",
            "Starting ringtone for caller=${person?.displayName ?: "Unknown"}, leadStatus=${lead?.status ?: "UNKNOWN"}"
        )
        ringtoneController.startRingtone(person, lead)
    }

    /**
     * Immediately silences the incoming ringtone and vibration without hanging up.
     */
    fun silenceRinger() {
        DeveloperLogger.info("CALL_RINGTONE_MANAGER", "Silencing ringer")
        ringtoneController.silenceRinger()
    }

    /**
     * Completely stops ringtone playback, cancels haptics, and abandons audio focus.
     */
    fun stopRingtone() {
        DeveloperLogger.info("CALL_RINGTONE_MANAGER", "Stopping ringtone and releasing audio focus")
        ringtoneController.stopRingtone()
    }

    /**
     * Triggers tactile feedback when call successfully connects.
     */
    fun onCallConnected() {
        hapticManager.vibrateCallConnected()
    }

    /**
     * Triggers tactile feedback when call terminates.
     */
    fun onCallEnded() {
        hapticManager.vibrateCallEnded()
    }

    /**
     * Triggers tactile feedback on action toggles (mute, hold).
     */
    fun onActionToggled() {
        hapticManager.vibrateActionToggle()
    }
}
