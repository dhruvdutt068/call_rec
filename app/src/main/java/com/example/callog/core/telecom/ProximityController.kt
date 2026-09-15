package com.example.callog.core.telecom

import android.content.Context
import android.os.PowerManager
import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.domain.call.AudioRoute
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controller exclusively dedicated to managing proximity sensor behavior and screen off/on transitions.
 *
 * Rule:
 * Active Call + Earpiece Route -> Proximity sensor lock acquired (Screen turns off near face).
 * Speaker, Bluetooth, Wired Headset, or Disconnected -> Lock released (Screen remains on).
 */
@Singleton
class ProximityController @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ProximityController"
        private const val LOCK_TAG = "callog:in_call_proximity_sensor"
        private const val MAX_LOCK_DURATION_MS = 3 * 60 * 60 * 1000L // 3 hours safety timeout
    }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var proximityWakeLock: PowerManager.WakeLock? = null

    init {
        try {
            if (powerManager?.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) == true) {
                proximityWakeLock = powerManager.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    LOCK_TAG
                ).apply {
                    setReferenceCounted(false)
                }
            }
        } catch (e: Exception) {
            DeveloperLogger.warning("PROXIMITY_INIT", "Proximity sensor wake lock not supported or failed to initialize: ${e.message}")
        }
    }

    /**
     * Re-evaluates proximity sensor activation based strictly on whether the call is active
     * and the audio is routed to the handset earpiece.
     */
    fun onCallStateOrRouteChanged(isCallActive: Boolean, route: AudioRoute) {
        val shouldHoldLock = isCallActive && route == AudioRoute.EARPIECE
        if (shouldHoldLock) {
            acquire()
        } else {
            release()
        }
    }

    fun acquire() {
        try {
            if (proximityWakeLock?.isHeld == false) {
                proximityWakeLock?.acquire(MAX_LOCK_DURATION_MS)
                DeveloperLogger.info("PROXIMITY_LOCK", "Proximity wake lock acquired (screen will blank when held near ear).")
            }
        } catch (e: Exception) {
            DeveloperLogger.warning("PROXIMITY_ACQUIRE_FAILED", "Failed to acquire proximity wake lock: ${e.message}")
        }
    }

    fun release() {
        try {
            if (proximityWakeLock?.isHeld == true) {
                proximityWakeLock?.release()
                DeveloperLogger.info("PROXIMITY_LOCK", "Proximity wake lock released.")
            }
        } catch (e: Exception) {
            DeveloperLogger.warning("PROXIMITY_RELEASE_FAILED", "Failed to release proximity wake lock: ${e.message}")
        }
    }
}
