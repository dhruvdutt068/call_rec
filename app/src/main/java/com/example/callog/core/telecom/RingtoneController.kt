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
import java.io.File
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

    protected var currentCallId: String? = null
    private var currentRingtoneUri: Uri? = null
    @Volatile
    private var isSilencedByUser: Boolean = false
    @Volatile
    private var isPausedForTransientFocus: Boolean = false

    private val lock = Any()
    private val currentGenerationToken = java.util.concurrent.atomic.AtomicLong(0L)

    /**
     * Issues an updated generation token to invalidate any in-flight asynchronous ringtone requests.
     */
    fun nextGenerationToken(): Long = currentGenerationToken.incrementAndGet()

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.i(TAG, "Audio focus permanently lost ($focusChange). Silencing ringtone.")
                DeveloperLogger.info("AUDIO_FOCUS_LOSS", "Audio focus permanently lost ($focusChange), silencing ringtone")
                silenceRinger()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.i(TAG, "Audio focus transiently lost. Pausing ringtone.")
                DeveloperLogger.info("AUDIO_FOCUS_LOSS_TRANSIENT", "Audio focus transiently lost, pausing ringtone")
                synchronized(lock) {
                    if (isPlaying && mediaPlayer?.isPlaying == true && !isSilencedByUser) {
                        try {
                            mediaPlayer?.pause()
                            isPausedForTransientFocus = true
                        } catch (e: Exception) {
                            Log.w(TAG, "Error pausing player on transient focus loss", e)
                        }
                    }
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.i(TAG, "Audio focus loss can duck. Lowering volume.")
                DeveloperLogger.info("AUDIO_FOCUS_DUCK", "Ducking ringtone audio volume")
                synchronized(lock) {
                    try {
                        mediaPlayer?.setVolume(0.2f, 0.2f)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error ducking volume", e)
                    }
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i(TAG, "Audio focus regained.")
                DeveloperLogger.info("AUDIO_FOCUS_GAIN", "Audio focus regained, resuming ringtone if appropriate")
                synchronized(lock) {
                    if (isPausedForTransientFocus && !isSilencedByUser) {
                        try {
                            mediaPlayer?.start()
                            mediaPlayer?.setVolume(1.0f, 1.0f)
                            isPausedForTransientFocus = false
                        } catch (e: Exception) {
                            Log.w(TAG, "Error resuming player on focus gain", e)
                        }
                    } else if (isPlaying && !isSilencedByUser) {
                        try {
                            mediaPlayer?.setVolume(1.0f, 1.0f)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error restoring volume on focus gain", e)
                        }
                    }
                }
            }
        }
    }

    val isRingtonePlaying: Boolean
        get() = synchronized(lock) { isPlaying }

    /**
     * Starts playing the ringtone associated with a specific [callId].
     * Sets the active [currentCallId] and delegates to [startRingtone].
     */
    open fun startRingtone(callId: String?, person: Person?, lead: Lead?) {
        startRingtone(callId, person, lead, null)
    }

    /**
     * Starts playing the ringtone associated with a specific [callId] and generation token validation.
     */
    open fun startRingtone(callId: String?, person: Person?, lead: Lead?, expectedToken: Long?) {
        if (expectedToken != null && expectedToken != currentGenerationToken.get()) {
            DeveloperLogger.info("RINGTONE_TOKEN_STALE", "Discarding stale ringtone start for call $callId (expected=$expectedToken, current=${currentGenerationToken.get()})")
            return
        }
        currentCallId = callId
        startRingtone(person, lead)
    }

    /**
     * Starts playing the ringtone resolved from [RingtonePolicy] for the incoming caller.
     * Idempotent: Redundant calls with the same resolved ringtone for the active call will not restart audio.
     */
    open fun startRingtone(person: Person? = null, lead: Lead? = null) {
        val callId = currentCallId
        synchronized(lock) {

            // Hardware kill-switch: If call already answered on Samsung screen
            if (audioManager?.mode == AudioManager.MODE_IN_CALL || audioManager?.mode == AudioManager.MODE_IN_COMMUNICATION) {
                Log.i(TAG, "Audio mode is already in communication/call. Aborting ringtone playback.")
                return
            }

            if (isSilencedByUser && (callId == null || callId == currentCallId)) {
                Log.i(TAG, "Ringtone was silenced by user for call $callId. Suppressing playback.")
                return
            }

            val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
            if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
                Log.i(TAG, "Device is in SILENT mode. Suppressing ringtone audio.")
                return
            }

            // Trigger lead-aware vibration in VIBRATE or NORMAL mode
            startVibration(lead?.status)

            if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                Log.i(TAG, "Device is in VIBRATE mode. Suppressing ringtone audio.")
                return
            }

            val ringtoneUri = try {
                ringtonePolicy.ringtoneFor(person, lead)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to resolve ringtone from policy for call $callId: ${e.message}", e)
                null
            }

            if (ringtoneUri == null) {
                Log.w(TAG, "Resolved ringtone URI is null for call $callId. Using default fallback.")
                playDefaultFallback()
                return
            }

            // Idempotent check: if already playing the exact same ringtone for this call, do not restart!
            if (isPlaying && currentCallId == callId && currentRingtoneUri == ringtoneUri && mediaPlayer?.isPlaying == true) {
                DeveloperLogger.info(
                    "RINGTONE_ALREADY_PLAYING",
                    "Ringtone $ringtoneUri is already playing for callId=$callId. Ignoring redundant start."
                )
                return
            }

            stopRingtoneInternal()

            requestAudioFocus()

            currentCallId = callId
            currentRingtoneUri = ringtoneUri
            isSilencedByUser = false
            isPausedForTransientFocus = false

            DeveloperLogger.info(
                "RINGTONE_CONTROLLER_STARTED",
                "Playing CRM policy ringtone: uri=$ringtoneUri, status=${lead?.status ?: "UNKNOWN"}, caller=${person?.displayName ?: "Unknown"}, callId=$callId"
            )

            // Validate file if scheme is file
            if (ringtoneUri.scheme == "file") {
                val file = File(ringtoneUri.path ?: "")
                if (!file.exists() || !file.isFile || file.length() <= 0L || !file.canRead()) {
                    DeveloperLogger.warning(
                        "RINGTONE_FILE_CORRUPTED",
                        "Ringtone file at ${file.absolutePath} is invalid or missing. Falling back to default."
                    )
                    playDefaultFallback()
                    return
                }
            }

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
                DeveloperLogger.error("RINGTONE_PLAYBACK_ERROR", "Failed playing ringtone uri: $ringtoneUri", e)
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
                DeveloperLogger.info("RINGTONE_FALLBACK_STARTED", "Playing system default fallback tone: $defaultUri")
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Fallback ringtone playback also failed", ex)
            DeveloperLogger.error("RINGTONE_FALLBACK_FAILED", "Fallback ringtone failed", ex)
        }
    }

    /**
     * Silences the active ringtone and vibration without hanging up the call.
     */
    open fun silenceRinger() {
        currentGenerationToken.incrementAndGet()
        synchronized(lock) {
            DeveloperLogger.info("RINGTONE_SILENCED", "Silencing incoming ringer for call $currentCallId.")
            isSilencedByUser = true
            isPausedForTransientFocus = false
            stopRingtoneInternal()
        }
    }

    /**
     * Stops the ringtone, cancels vibration, and abandons audio focus.
     */
    open fun stopRingtone() {
        currentGenerationToken.incrementAndGet()
        synchronized(lock) {
            DeveloperLogger.info("RINGTONE_STOPPED", "Stopping ringtone and resetting state for call $currentCallId.")
            currentCallId = null
            currentRingtoneUri = null
            isSilencedByUser = false
            isPausedForTransientFocus = false
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

    private fun startVibration(status: com.example.callog.domain.model.LeadStatus? = null) {
        try {
            val pattern = when (status) {
                com.example.callog.domain.model.LeadStatus.HOT -> longArrayOf(0, 250, 150, 250, 1000) // Double-pulse
                com.example.callog.domain.model.LeadStatus.WARM -> longArrayOf(0, 1000, 1000) // Standard vibration
                com.example.callog.domain.model.LeadStatus.COLD -> longArrayOf(0, 150, 2500) // Minimal chime pulse
                else -> longArrayOf(0, 1000, 1000) // System-compatible default
            }
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
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                audioFocusRequest = focusRequest
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    audioFocusChangeListener,
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
