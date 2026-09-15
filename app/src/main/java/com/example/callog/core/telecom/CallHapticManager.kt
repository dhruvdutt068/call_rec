package com.example.callog.core.telecom

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.callog.core.diagnostics.DeveloperLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages tactile haptic feedback during in-call lifecycle events:
 * - Call Connected (Subtle confirmation tick)
 * - Call Ended (Double tap)
 * - Mute / Hold Toggled (Short crisp pulse)
 */
@Singleton
open class CallHapticManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Vibrate briefly when call connects and timer starts.
     */
    open fun vibrateCallConnected() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(45)
            }
            DeveloperLogger.info("CALL_HAPTIC", "Vibrate call connected executed.")
        } catch (e: Exception) {
            DeveloperLogger.warning("CALL_HAPTIC", "Error vibrating on call connect: ${e.message}")
        }
    }

    /**
     * Vibrate with double pulse when call ends / is disconnected.
     */
    open fun vibrateCallEnded() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 50, 70, 50)
                val amplitudes = intArrayOf(0, 180, 0, 180)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 50, 70, 50), -1)
            }
            DeveloperLogger.info("CALL_HAPTIC", "Vibrate call ended executed.")
        } catch (e: Exception) {
            DeveloperLogger.warning("CALL_HAPTIC", "Error vibrating on call end: ${e.message}")
        }
    }

    /**
     * Vibrate on mute / hold action toggle.
     */
    open fun vibrateActionToggle() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(25)
            }
        } catch (e: Exception) {
            DeveloperLogger.warning("CALL_HAPTIC", "Error vibrating on action toggle: ${e.message}")
        }
    }
}
