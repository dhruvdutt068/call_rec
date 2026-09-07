package com.example.callog.data.telecom

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
import com.example.callog.domain.model.LeadPriority
import com.example.callog.domain.model.LeadStatus
import com.example.callog.domain.model.Person
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class RingtoneType {
    PERSON_SPECIFIC,
    CRM_PRIORITY,
    LEAD_STATUS_HOT,
    LEAD_STATUS_WARM,
    CUSTOMER_TYPE,
    DEFAULT
}

@Singleton
open class CallRingtoneManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CallRingtoneManager"
        private const val PREFS_NAME = "ringtone_settings_prefs"
        private const val KEY_PREFIX_PERSON = "ringtone_person_"
        private const val KEY_CRM_PRIORITY = "ringtone_crm_priority"
        private const val KEY_LEAD_HOT = "ringtone_lead_hot"
        private const val KEY_LEAD_WARM = "ringtone_lead_warm"
        private const val KEY_CUSTOMER_TYPE = "ringtone_customer_type"
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

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Resolves the ringtone classification based on strict priority:
     * 1. Person-specific ringtone (if customized)
     * 2. CRM Priority (URGENT or HIGH)
     * 3. Lead Status (HOT or WARM)
     * 4. Customer Type (customer status or company match)
     * 5. Default ringtone
     */
    open fun resolveRingtoneType(person: Person?, lead: Lead?): RingtoneType {
        // Priority 1: Person-specific ringtone
        if (person != null) {
            val customPersonTone = prefs().getString("$KEY_PREFIX_PERSON${person.id}", null)
            if (!customPersonTone.isNullOrBlank()) {
                return RingtoneType.PERSON_SPECIFIC
            }
        }

        // Priority 2: CRM Priority
        if (lead != null && (lead.priority == LeadPriority.URGENT || lead.priority == LeadPriority.HIGH)) {
            return RingtoneType.CRM_PRIORITY
        }

        // Priority 3: Lead Status
        if (lead != null) {
            when (lead.status) {
                LeadStatus.HOT -> return RingtoneType.LEAD_STATUS_HOT
                LeadStatus.WARM -> return RingtoneType.LEAD_STATUS_WARM
                else -> Unit
            }
        }

        // Priority 4: Customer Type
        if (lead != null) {
            val isCustomer = lead.status.name.equals("CUSTOMER", ignoreCase = true) ||
                    lead.notes?.contains("Customer", ignoreCase = true) == true ||
                    lead.feedback?.contains("Customer", ignoreCase = true) == true
            if (isCustomer) {
                return RingtoneType.CUSTOMER_TYPE
            }
        }
        if (person?.companyName?.isNotBlank() == true) {
            return RingtoneType.CUSTOMER_TYPE
        }

        // Priority 5: Default
        return RingtoneType.DEFAULT
    }

    /**
     * Selects URI based on [resolveRingtoneType].
     */
    fun selectRingtoneUri(person: Person?, lead: Lead?): Uri {
        val type = resolveRingtoneType(person, lead)
        val uriString = when (type) {
            RingtoneType.PERSON_SPECIFIC -> person?.let { prefs().getString("$KEY_PREFIX_PERSON${it.id}", null) }
            RingtoneType.CRM_PRIORITY -> prefs().getString(KEY_CRM_PRIORITY, null)
            RingtoneType.LEAD_STATUS_HOT -> prefs().getString(KEY_LEAD_HOT, null)
            RingtoneType.LEAD_STATUS_WARM -> prefs().getString(KEY_LEAD_WARM, null)
            RingtoneType.CUSTOMER_TYPE -> prefs().getString(KEY_CUSTOMER_TYPE, null)
            RingtoneType.DEFAULT -> null
        }

        if (!uriString.isNullOrBlank()) {
            try {
                return Uri.parse(uriString)
            } catch (e: Exception) {
                Log.w(TAG, "Failed parsing custom ringtone URI: $uriString, falling back to system default", e)
            }
        }

        // Fallback: System default ringtone
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    /**
     * Starts playing the appropriate ringtone and vibration for an incoming call.
     */
    open fun startRingtone(person: Person?, lead: Lead?) {
        synchronized(lock) {
            stopRingtoneInternal()

            val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
            if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
                Log.i(TAG, "Device is in SILENT mode. Suppressing ringtone.")
                return
            }

            // Trigger vibration if in VIBRATE or NORMAL mode
            startVibration()

            if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                Log.i(TAG, "Device is in VIBRATE mode. Suppressing audio ringtone.")
                return
            }

            // Request Audio Focus
            requestAudioFocus()

            val ringtoneUri = selectRingtoneUri(person, lead)
            val ringtoneType = resolveRingtoneType(person, lead)
            DeveloperLogger.info(
                "RINGTONE_STARTED",
                "Playing status-based ringtone: type=$ringtoneType, uri=$ringtoneUri"
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
                Log.e(TAG, "Failed to play ringtone uri: $ringtoneUri. Attempting default fallback.", e)
                try {
                    val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    if (defaultUri != null && defaultUri != ringtoneUri) {
                        val fallbackPlayer = MediaPlayer().apply {
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
                        mediaPlayer = fallbackPlayer
                        isPlaying = true
                    }
                } catch (fallbackEx: Exception) {
                    Log.e(TAG, "Default ringtone fallback also failed", fallbackEx)
                }
            }
        }
    }

    /**
     * Stops the ringtone, cancels vibration, and abandons audio focus.
     * Safe to call repeatedly from any thread.
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

    val isRingtonePlaying: Boolean
        get() = synchronized(lock) { isPlaying }

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

    // Settings helpers for custom tone management
    fun setPersonRingtone(personId: String, uri: Uri?) {
        if (uri == null) {
            prefs().edit().remove("$KEY_PREFIX_PERSON$personId").apply()
        } else {
            prefs().edit().putString("$KEY_PREFIX_PERSON$personId", uri.toString()).apply()
        }
    }

    fun setCrmPriorityRingtone(uri: Uri?) {
        if (uri == null) prefs().edit().remove(KEY_CRM_PRIORITY).apply()
        else prefs().edit().putString(KEY_CRM_PRIORITY, uri.toString()).apply()
    }

    fun setLeadHotRingtone(uri: Uri?) {
        if (uri == null) prefs().edit().remove(KEY_LEAD_HOT).apply()
        else prefs().edit().putString(KEY_LEAD_HOT, uri.toString()).apply()
    }
}
