package com.example.callog.domain.call

import kotlinx.coroutines.flow.StateFlow

sealed interface CallRecordingState {
    data object Idle : CallRecordingState
    data object Starting : CallRecordingState
    data class Recording(
        val elapsedMillis: Long,
        val session: RecordingSession
    ) : CallRecordingState
    data object Stopping : CallRecordingState
    data class Saved(
        val recordingId: String,
        val filePath: String,
        val durationMillis: Long
    ) : CallRecordingState
    data class Error(
        val message: String
    ) : CallRecordingState
    data class Unsupported(
        val reason: String = "Call recording is not supported on this device/configuration"
    ) : CallRecordingState
}

data class RecordingSession(
    val recordingId: String,
    val callId: String,
    val phoneNumber: String?,
    val personId: String? = null,
    val simSlot: Int? = null,
    val filePath: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val durationMillis: Long = 0L,
    val mimeType: String = "audio/mp4"
)

interface CallRecordingController {
    val recordingState: StateFlow<CallRecordingState>

    suspend fun canRecord(): Boolean

    suspend fun start(
        callId: String,
        phoneNumber: String?,
        personId: String? = null,
        simSlot: Int? = null,
        startedAt: Long = System.currentTimeMillis()
    ): Result<RecordingSession>

    suspend fun stop(): Result<RecordingSession>

    suspend fun cancel()
}
