package com.example.callog.domain.service

import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.core.utils.PhoneNumberNormalizer
import com.example.callog.data.local.entity.CallEntity
import com.example.callog.domain.model.RecordingCandidate
import com.example.callog.domain.model.RecordingMatchResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Deterministic multi-factor correlation engine linking discovered OEM audio recordings
 * to completed call sessions.
 *
 * Implements Phase 6.5: Multi-factor correlation
 * Signals & Weights:
 * 1. Phone number exact normalized match: +0.40 (Contradictory phone: hard safety rejection)
 * 2. Timestamp proximity (±15s around disconnect): +0.30 (Linear falloff to ±60s)
 * 3. Audio duration tolerance (±4s against call duration): +0.20 (Falloff to ±10s)
 * 4. File timestamp >= call start time: +0.10
 *
 * Threshold: >= 0.70 for automatic Matched status.
 * Safety gates:
 * - Reject already associated calls
 * - Reject files created before call started
 * - Reject conflicting call durations
 * - Flag ambiguous when multiple calls qualify equally
 */
@Singleton
class RecordingCorrelationMatcher @Inject constructor() {

    companion object {
        private const val TAG = "RecordingCorrelationMatcher"
        const val AUTOMATIC_MATCH_THRESHOLD = 0.70
        const val MAX_TIMESTAMP_DELTA_MS = 60_000L // 60s window
        const val MAX_DURATION_DELTA_SECONDS = 15L // 15s max drift
    }

    /**
     * Evaluates a [RecordingCandidate] against a list of eligible [CallEntity] sessions.
     */
    fun match(
        candidate: RecordingCandidate,
        eligibleCalls: List<CallEntity>
    ): RecordingMatchResult {
        if (eligibleCalls.isEmpty()) {
            return RecordingMatchResult.NoMatch(
                recordingPath = candidate.filePath,
                reason = "No eligible call sessions found in correlation window."
            )
        }

        val scoredCandidates = mutableListOf<ScoredCall>()

        for (call in eligibleCalls) {
            val scoreResult = evaluatePair(candidate, call)
            if (scoreResult != null) {
                scoredCandidates.add(scoreResult)
            }
        }

        if (scoredCandidates.isEmpty()) {
            return RecordingMatchResult.NoMatch(
                recordingPath = candidate.filePath,
                reason = "All calls disqualified by safety gates (duration conflict, phone contradiction, or already linked)."
            )
        }

        // Sort descending by confidence score
        scoredCandidates.sortByDescending { it.confidence }

        val best = scoredCandidates.first()

        // Check for ambiguity among high-confidence matches
        val topQualifying = scoredCandidates.filter { it.confidence >= AUTOMATIC_MATCH_THRESHOLD }
        if (topQualifying.size > 1) {
            // Check score difference between top 2
            val second = topQualifying[1]
            if (abs(best.confidence - second.confidence) < 0.10) {
                DeveloperLogger.warning(
                    TAG,
                    "Ambiguous correlation for recording ${candidate.fileName}: Call ${best.call.id} (${best.confidence}) vs Call ${second.call.id} (${second.confidence})"
                )
                return RecordingMatchResult.Ambiguous(
                    candidates = topQualifying.map { it.call.id },
                    recordingPath = candidate.filePath,
                    reason = "Multiple calls qualify within score delta (${best.confidence} vs ${second.confidence})."
                )
            }
        }

        if (best.confidence >= AUTOMATIC_MATCH_THRESHOLD) {
            DeveloperLogger.info(
                TAG,
                "High confidence match: Call ${best.call.id} <-> ${candidate.fileName}, confidence=${"%.2f".format(best.confidence)}, details=${best.rationale}"
            )
            return RecordingMatchResult.Matched(
                callId = best.call.id,
                recordingPath = candidate.filePath,
                confidence = best.confidence,
                details = best.rationale
            )
        } else {
            return RecordingMatchResult.NoMatch(
                recordingPath = candidate.filePath,
                reason = "Best candidate (Call ${best.call.id}) score ${"%.2f".format(best.confidence)} below threshold $AUTOMATIC_MATCH_THRESHOLD."
            )
        }
    }

    /**
     * Evaluates a single call and candidate pair, returning a [ScoredCall] or null if disqualified by hard gates.
     */
    fun evaluatePair(candidate: RecordingCandidate, call: CallEntity): ScoredCall? {
        // Gate 1: Reject if call already has a recording associated
        if (!call.recordingPath.isNullOrBlank() && call.recordingPath != candidate.filePath) {
            return null
        }

        // Gate 2: Reject missed/rejected calls without audio
        if (call.callType.equals("MISSED", ignoreCase = true) || call.callType.equals("REJECTED", ignoreCase = true)) {
            return null
        }

        // Gate 3: File timestamp cannot be older than call start time by more than 15s
        val callStartMs = call.timestamp
        val callDisconnectMs = callStartMs + (call.duration * 1000L)
        val fileTimeMs = candidate.timestamp

        if (fileTimeMs < callStartMs - 15_000L) {
            // File was created well before the call began -> Impossible
            return null
        }

        // Gate 4: If extracted phone exists, check for contradiction
        val normCandidatePhone = candidate.phoneNumber?.let { PhoneNumberNormalizer.normalize(it) }
        val normCallPhone = PhoneNumberNormalizer.normalize(call.number)

        val hasExtractedPhone = !normCandidatePhone.isNullOrBlank()
        if (hasExtractedPhone && normCandidatePhone != null) {
            val phoneMatch = isPhoneMatching(normCandidatePhone, normCallPhone)
            if (!phoneMatch) {
                // Contradictory phone numbers -> Disqualify
                return null
            }
        }

        // Gate 5: Duration conflict check
        if (candidate.durationSeconds > 0 && call.duration > 0) {
            val durationDiff = abs(candidate.durationSeconds - call.duration)
            if (durationDiff > MAX_DURATION_DELTA_SECONDS) {
                // Duration differs by more than 15s -> Disqualify
                return null
            }
        }

        // --- Calculate Multi-factor Confidence Score ---
        var score = 0.0
        val signals = mutableListOf<String>()

        // Signal 1: Phone number match (+0.40)
        if (hasExtractedPhone && normCandidatePhone != null) {
            if (isPhoneMatching(normCandidatePhone, normCallPhone)) {
                score += 0.40
                signals.add("Phone exact match (+0.40)")
            }
        } else {
            // Partial neutral credit if filename had contact name or neutral identifier
            if (!candidate.contactName.isNullOrBlank() && !call.name.isNullOrBlank() &&
                candidate.contactName.equals(call.name, ignoreCase = true)) {
                score += 0.25
                signals.add("Contact name match (+0.25)")
            }
        }

        // Signal 2: Timestamp proximity to call disconnect (+0.30 max)
        val disconnectDelta = abs(fileTimeMs - callDisconnectMs)
        when {
            disconnectDelta <= 15_000L -> {
                score += 0.30
                signals.add("Disconnect proximity <= 15s (+0.30)")
            }
            disconnectDelta <= 30_000L -> {
                score += 0.20
                signals.add("Disconnect proximity <= 30s (+0.20)")
            }
            disconnectDelta <= MAX_TIMESTAMP_DELTA_MS -> {
                score += 0.10
                signals.add("Disconnect proximity <= 60s (+0.10)")
            }
        }

        // Signal 3: Audio duration tolerance (+0.20 max)
        if (candidate.durationSeconds > 0 && call.duration > 0) {
            val durationDelta = abs(candidate.durationSeconds - call.duration)
            when {
                durationDelta <= 4L -> {
                    score += 0.20
                    signals.add("Duration delta <= 4s (+0.20)")
                }
                durationDelta <= 8L -> {
                    score += 0.12
                    signals.add("Duration delta <= 8s (+0.12)")
                }
                durationDelta <= MAX_DURATION_DELTA_SECONDS -> {
                    score += 0.05
                    signals.add("Duration delta <= 15s (+0.05)")
                }
            }
        }

        // Signal 4: File modification after call start (+0.10)
        if (candidate.lastModified >= callStartMs) {
            score += 0.10
            signals.add("File modification post call start (+0.10)")
        }

        // Normalized score cap at 1.00
        val finalScore = score.coerceIn(0.0, 1.0)
        return ScoredCall(
            call = call,
            confidence = finalScore,
            rationale = signals.joinToString("; ")
        )
    }

    private fun isPhoneMatching(p1: String, p2: String): Boolean {
        if (p1 == p2) return true
        val clean1 = p1.filter { it.isDigit() }
        val clean2 = p2.filter { it.isDigit() }
        if (clean1 == clean2) return true
        // Suffix matching for national numbers (e.g. 10 digits match)
        if (clean1.length >= 10 && clean2.length >= 10) {
            return clean1.takeLast(10) == clean2.takeLast(10)
        }
        return false
    }

    data class ScoredCall(
        val call: CallEntity,
        val confidence: Double,
        val rationale: String
    )
}
