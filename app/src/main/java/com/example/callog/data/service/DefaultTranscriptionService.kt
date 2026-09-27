package com.example.callog.data.service

import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.domain.service.TranscriptionService
import com.example.callog.domain.service.TranscriptionState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultTranscriptionService @Inject constructor() : TranscriptionService {

    override fun isAvailable(): Boolean {
        // Safe default: no external transcription credentials hardcoded on client
        return false
    }

    override suspend fun requestTranscription(recordingId: String, remoteUrl: String): Result<TranscriptionState> {
        DeveloperLogger.info(
            "TRANSCRIPTION_SERVICE",
            "Transcription requested for recording $recordingId ($remoteUrl), but service is currently disabled."
        )
        return Result.success(TranscriptionState.Unavailable("No AI transcription backend endpoint configured on this device."))
    }
}
