package com.example.callog.core.telecom

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.domain.model.Lead
import com.example.callog.domain.model.Person
import com.example.callog.domain.repository.RingtonePolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authoritative controller for playing incoming call ringtones and vibrations.
 *
 * Fulfills Android Telecom's contract when declaring:
 * <meta-data android:name="android.telecom.IN_CALL_SERVICE_RINGING" android:value="true" />
 */
@Singleton
open class RingtoneController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val ringtonePolicy: RingtonePolicy
) {
    companion object {
        private const val TAG = "RingtoneController"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isPlaying = false

    private val lock = Any()

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    val isRingtonePlaying: Boolean
        get() = synchronized(lock) { isPlaying }

    /**
     * Starts playing the ringtone resolved from [RingtonePolicy] for the incoming caller.
     */
    open fun startRingtone(person: Person? = null, lead: Lead? = null) {
        synchronized(lock) {
            stopRingtoneInternal()

            val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
            if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
                Log.i(TAG, "Device is in SILENT mode. Suppressing ringtone audio.")
                return
            }

            // Trigger vibration in VIBRATE or NORMAL mode
            startVibration()

            if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                Log.i(TAG, "Device is in VIBRATE mode. Suppressing ringtone audio.")
                return
            }

            requestAudioFocus()

            val ringtoneUri = ringtonePolicy.ringtoneFor(person, lead)
            DeveloperLogger.info(
                "RINGTONE_CONTROLLER_STARTED",
                "Playing CRM policy ringtone: uri=$ringtoneUri, caller=${person?.displayName ?: "Unknown"}"
            )

            try {
                val player = MediaPlayer().apply {
                    setDataSource(context, ringtoneUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra. Stopping ringtone.")
                        stopRingtone()
                        true
                    }
                    prepare()
                    start()
                }
                mediaPlayer = player
                isPlaying = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed playing ringtone uri: $ringtoneUri. Falling back to default tone.", e)
                playDefaultFallback()
            }
        }
    }

    private fun playDefaultFallback() {
        try {
            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (defaultUri != null) {
                val player = MediaPlayer().apply {
                    setDataSource(context, defaultUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
                mediaPlayer = player
                isPlaying = true
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Fallback ringtone playback also failed", ex)
        }
    }

    /**
     * Silences the active ringtone and vibration without hanging up the call.
     */
    open fun silenceRinger() {
        synchronized(lock) {
            DeveloperLogger.info("RINGTONE_SILENCED", "Silencing incoming ringer.")
            stopRingtoneInternal()
        }
    }

    /**
     * Stops the ringtone, cancels vibration, and abandons audio focus.
     */
    open fun stopRingtone() {
        synchronized(lock) {
            stopRingtoneInternal()
        }
    }

    private fun stopRingtoneInternal() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.stop()
                }
                mediaPlayer?.release()
                mediaPlayer = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception releasing MediaPlayer", e)
        } finally {
            mediaPlayer = null
            isPlaying = false
        }

        stopVibration()
        abandonAudioFocus()
    }

    private fun startVibration() {
        try {
            val pattern = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start vibration", e)
        }
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel vibration", e)
        }
    }

    private fun requestAudioFocus() {
        if (audioManager == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .build()
                audioFocusRequest = focusRequest
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_RING,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request audio focus", e)
        }
    }

    private fun abandonAudioFocus() {
        if (audioManager == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to abandon audio focus", e)
        }
    }
}
