package com.example.callog.domain.service

/**
 * State of AI transcription for a call recording.
 */
sealed interface TranscriptionState {
    data object Idle : TranscriptionState
    data class Processing(val recordingId: String) : TranscriptionState
    data class Completed(val recordingId: String, val transcript: String) : TranscriptionState
    data class Failed(val recordingId: String, val error: String) : TranscriptionState
    data class Unavailable(val reason: String = "No AI transcription backend endpoint configured") : TranscriptionState
}

/**
 * Domain interface for AI call transcription capabilities.
 */
interface TranscriptionService {
    /**
     * Indicates whether an active AI transcription service endpoint is configured.
     */
    fun isAvailable(): Boolean

    /**
     * Triggers transcription for a verified uploaded recording.
     */
    suspend fun requestTranscription(recordingId: String, remoteUrl: String): Result<TranscriptionState>
}
