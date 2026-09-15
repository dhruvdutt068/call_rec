package com.example.callog.core.telecom

import android.content.Context
import android.os.PowerManager
import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.domain.call.AudioRoute
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages proximity wake lock to turn off screen and touch controls
 * when the phone is held against the user's ear during Earpiece audio route.
 * Automatically deactivates when on Speaker, Bluetooth, or Wired Headset.
 */
@Singleton
open class ProximitySensorManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    init {
        try {
            if (powerManager?.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) == true) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "callog:in_call_proximity_sensor"
                ).apply {
                    setReferenceCounted(false)
                }
            }
        } catch (e: Exception) {
            DeveloperLogger.warning("PROXIMITY", "Failed to initialize Proximity WakeLock: ${e.message}")
        }
    }

    /**
     * Updates proximity sensor activation based on current audio route and call active status.
     */
    open fun updateProximityState(isCallActive: Boolean, currentRoute: AudioRoute) {
        val shouldHold = isCallActive && currentRoute == AudioRoute.EARPIECE
        if (shouldHold) {
            acquire()
        } else {
            release()
        }
    }

    open fun acquire() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(2 * 60 * 60 * 1000L /* 2 hours max */)
                DeveloperLogger.info("PROXIMITY", "Proximity wake lock acquired.")
            }
        } catch (e: Exception) {
            DeveloperLogger.warning("PROXIMITY", "Error acquiring proximity lock: ${e.message}")
        }
    }

    open fun release() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                DeveloperLogger.info("PROXIMITY", "Proximity wake lock released.")
            }
        } catch (e: Exception) {
            DeveloperLogger.warning("PROXIMITY", "Error releasing proximity lock: ${e.message}")
        }
    }
}
