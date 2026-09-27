package com.example.callog.domain.model

/**
 * Result representing multi-factor correlation matching between a discovered OEM recording
 * and call sessions recorded by Callog.
 */
sealed interface RecordingMatchResult {

    /**
     * Successfully matched to a unique call with high confidence passing all safety checks.
     */
    data class Matched(
        val callId: Long,
        val recordingPath: String,
        val confidence: Double,
        val details: String
    ) : RecordingMatchResult

    /**
     * Multiple plausible calls matched or conflict detected; requires manual review.
     */
    data class Ambiguous(
        val candidates: List<Long>,
        val recordingPath: String,
        val reason: String
    ) : RecordingMatchResult

    /**
     * No eligible call candidate found within tolerance parameters.
     */
    data class NoMatch(
        val recordingPath: String,
        val reason: String
    ) : RecordingMatchResult
}

/**
 * Encapsulates candidate metadata extracted from discovered audio files.
 */
data class RecordingCandidate(
    val filePath: String,
    val fileName: String,
    val phoneNumber: String?,
    val contactName: String?,
    val timestamp: Long,
    val durationSeconds: Long,
    val fileSize: Long,
    val lastModified: Long,
    val parserName: String
)
