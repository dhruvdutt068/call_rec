package com.example.callog.core.telecom

import android.media.AudioManager
import android.media.ToneGenerator
import com.example.callog.core.diagnostics.DeveloperLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays standard DTMF (Dual-Tone Multi-Frequency) touch-tones locally for tactile user feedback.
 */
@Singleton
open class DtmfTonePlayer @Inject constructor() {

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
        } catch (e: Exception) {
            DeveloperLogger.warning("DTMF_TONE", "Failed to initialize ToneGenerator: ${e.message}")
        }
    }

    /**
     * Plays the local audio tone corresponding to the DTMF character.
     */
    open fun playTone(digit: Char, durationMs: Int = 150) {
        val toneType = when (digit) {
            '0' -> ToneGenerator.TONE_DTMF_0
            '1' -> ToneGenerator.TONE_DTMF_1
            '2' -> ToneGenerator.TONE_DTMF_2
            '3' -> ToneGenerator.TONE_DTMF_3
            '4' -> ToneGenerator.TONE_DTMF_4
            '5' -> ToneGenerator.TONE_DTMF_5
            '6' -> ToneGenerator.TONE_DTMF_6
            '7' -> ToneGenerator.TONE_DTMF_7
            '8' -> ToneGenerator.TONE_DTMF_8
            '9' -> ToneGenerator.TONE_DTMF_9
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            'A', 'a' -> ToneGenerator.TONE_DTMF_A
            'B', 'b' -> ToneGenerator.TONE_DTMF_B
            'C', 'c' -> ToneGenerator.TONE_DTMF_C
            'D', 'd' -> ToneGenerator.TONE_DTMF_D
            else -> -1
        }

        if (toneType != -1) {
            try {
                toneGenerator?.startTone(toneType, durationMs)
            } catch (e: Exception) {
                DeveloperLogger.warning("DTMF_TONE", "Error playing tone $digit: ${e.message}")
            }
        }
    }

    fun stopTone() {
        try {
            toneGenerator?.stopTone()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            // ignore
        }
    }
}
