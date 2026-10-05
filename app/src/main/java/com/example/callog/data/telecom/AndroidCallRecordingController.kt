package com.example.callog.data.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.domain.call.CallRecordingController
import com.example.callog.domain.call.CallRecordingState
import com.example.callog.domain.call.RecordingSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidCallRecordingController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val recordingStorageManager: RecordingStorageManager
) : CallRecordingController {

    companion object {
        private const val TAG = "CallRecordingCtrl"
    }

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _recordingState = MutableStateFlow<CallRecordingState>(CallRecordingState.Idle)
    override val recordingState: StateFlow<CallRecordingState> = _recordingState.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var currentSession: RecordingSession? = null
    private var currentTempFile: File? = null
    private var currentTargetFileName: String? = null
    private var recordingStartTimeRealtime: Long = 0L
    private var tickerJob: Job? = null

    override suspend fun canRecord(): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasMicFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        return hasPermission && hasMicFeature
    }

    override suspend fun start(
        callId: String,
        phoneNumber: String?,
        contactName: String?,
        personId: String?,
        simSlot: Int?,
        startedAt: Long
    ): Result<RecordingSession> = mutex.withLock {
        withContext(Dispatchers.IO) {
            DeveloperLogger.info(TAG, "Request to start recording for callId=$callId (contactName=$contactName, phoneNumber=$phoneNumber, personId=$personId)")

            if (!canRecord()) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                val errorReason = if (!hasPermission) "RECORD_AUDIO permission not granted" else "Microphone hardware unavailable"
                DeveloperLogger.warning(TAG, "Cannot start recording: $errorReason")
                _recordingState.value = CallRecordingState.Unsupported(errorReason)
                return@withContext Result.failure(IllegalStateException(errorReason))
            }

            if (_recordingState.value is CallRecordingState.Recording || mediaRecorder != null) {
                DeveloperLogger.warning(TAG, "Recording already in progress. Ignoring duplicate start request.")
                currentSession?.let { return@withContext Result.success(it) }
            }

            _recordingState.value = CallRecordingState.Starting

            try {
                val outputDir = recordingStorageManager.getDedicatedRecordingDirectory()
                if (!recordingStorageManager.hasAvailableStorage(outputDir)) {
                    val err = "Insufficient device storage space for call recording"
                    DeveloperLogger.warning(TAG, err)
                    _recordingState.value = CallRecordingState.Error(err)
                    return@withContext Result.failure(IllegalStateException(err))
                }

                val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                val formattedDate = dateFormat.format(Date(startedAt))

                val safeIdentifier = when {
                    !contactName.isNullOrBlank() && !contactName.equals("Unknown Caller", ignoreCase = true) -> {
                        contactName.trim().replace(Regex("[^a-zA-Z0-9 _-]"), "_").trim { it == '_' || it == '-' || it.isWhitespace() }
                    }
                    !phoneNumber.isNullOrBlank() -> {
                        phoneNumber.trim().replace(Regex("[^a-zA-Z0-9+_-]"), "_").trim { it == '_' || it == '-' || it.isWhitespace() }
                    }
                    else -> "Call_${callId.replace(Regex("[^a-zA-Z0-9_-]"), "_").trim { it == '_' || it == '-' }}"
                }.ifEmpty { "Call_$callId" }

                val recordingId = "rec_${UUID.randomUUID()}"
                val targetFileName = "Callog_${safeIdentifier}_${formattedDate}.m4a"

                // Use app-controlled storage for low-level MediaRecorder capture to prevent Scoped Storage EACCES
                val tempFile = recordingStorageManager.getTemporaryRecordingFile(recordingId)
                if (tempFile.exists()) {
                    try { tempFile.delete() } catch (_: Exception) {}
                }

                val (recorder, activeSource) = startMediaRecorderWithFallback(tempFile)
                val sourceName = getAudioSourceName(activeSource)
                mediaRecorder = recorder
                currentTempFile = tempFile
                currentTargetFileName = targetFileName
                recordingStartTimeRealtime = SystemClock.elapsedRealtime()

                val session = RecordingSession(
                    recordingId = recordingId,
                    callId = callId,
                    phoneNumber = phoneNumber,
                    contactName = contactName,
                    personId = personId,
                    simSlot = simSlot,
                    filePath = tempFile.absolutePath,
                    startedAt = startedAt,
                    mimeType = "audio/mp4",
                    audioSource = activeSource,
                    audioSourceName = sourceName
                )
                currentSession = session
                _recordingState.value = CallRecordingState.Recording(0L, session)

                DeveloperLogger.info(TAG, "Recording started successfully with audio source $sourceName ($activeSource): tempPath=${tempFile.absolutePath}, targetName=$targetFileName, session=$recordingId")
                startTicker(session)

                Result.success(session)
            } catch (e: Exception) {
                DeveloperLogger.error(TAG, "Failed to initialize/start call recorder: ${e.message}", e)
                releaseRecorderInternal()
                currentTempFile?.let { try { it.delete() } catch (_: Exception) {} }
                currentTempFile = null
                currentTargetFileName = null
                currentSession = null
                _recordingState.value = CallRecordingState.Error("Failed to start recorder: ${e.message}")
                Result.failure(e)
            }
        }
    }

    override suspend fun stop(): Result<RecordingSession> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val session = currentSession
            val recorder = mediaRecorder
            val tempFile = currentTempFile ?: session?.filePath?.let { File(it) }

            if (session == null) {
                DeveloperLogger.info(TAG, "Stop called but no active recording session found.")
                releaseRecorderInternal()
                _recordingState.value = CallRecordingState.Idle
                return@withContext Result.failure(IllegalStateException("No active recording session"))
            }

            _recordingState.value = CallRecordingState.Stopping
            DeveloperLogger.info(TAG, "Stopping call recording session: ${session.recordingId}")

            tickerJob?.cancel()
            tickerJob = null

            try {
                // Ensure at least 800ms of recording to prevent MediaRecorder stop failed (-1007)
                val elapsedMs = SystemClock.elapsedRealtime() - recordingStartTimeRealtime
                if (elapsedMs in 1..800) {
                    delay(800 - elapsedMs)
                }

                try {
                    recorder?.stop()
                } catch (e: RuntimeException) {
                    DeveloperLogger.warning(TAG, "MediaRecorder stop failed (possibly too short duration): ${e.message}")
                }
                releaseRecorderInternal()

                if (tempFile != null && tempFile.exists() && tempFile.length() > 0) {
                    val endedAt = System.currentTimeMillis()
                    val actualDurationMs = getActualAudioDuration(tempFile.absolutePath).takeIf { it > 0 }
                        ?: (endedAt - session.startedAt).coerceAtLeast(1000L)

                    val targetFileName = currentTargetFileName ?: run {
                        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                        val formattedDate = dateFormat.format(Date(session.startedAt))
                        val safeId = when {
                            !session.contactName.isNullOrBlank() && !session.contactName.equals("Unknown Caller", ignoreCase = true) -> {
                                session.contactName.trim().replace(Regex("[^a-zA-Z0-9 _-]"), "_").trim { it == '_' || it == '-' || it.isWhitespace() }
                            }
                            !session.phoneNumber.isNullOrBlank() -> {
                                session.phoneNumber.trim().replace(Regex("[^a-zA-Z0-9+_-]"), "_").trim { it == '_' || it == '-' || it.isWhitespace() }
                            }
                            else -> "Call_${session.callId.replace(Regex("[^a-zA-Z0-9_-]"), "_").trim { it == '_' || it == '-' }}"
                        }.ifEmpty { "Call_${session.callId}" }
                        "Callog_${safeId}_${formattedDate}.m4a"
                    }

                    val callerIdForTitle = session.contactName?.takeIf { it.isNotBlank() } ?: session.phoneNumber ?: session.callId

                    // Finalize: copies/publishes tempFile to dedicated folder (e.g. Recordings/Callog),
                    // indexes in MediaStore, notifies MediaScanner, and deletes tempFile
                    val finalizedFile = recordingStorageManager.finalizeRecordingFile(
                        tempFile = tempFile,
                        targetFileName = targetFileName,
                        callerIdentifier = callerIdForTitle,
                        startedAt = session.startedAt,
                        durationMillis = actualDurationMs
                    )

                    val finalizedSession = session.copy(
                        filePath = finalizedFile.absolutePath,
                        endedAt = endedAt,
                        durationMillis = actualDurationMs
                    )

                    _recordingState.value = CallRecordingState.Saved(
                        recordingId = finalizedSession.recordingId,
                        filePath = finalizedSession.filePath,
                        durationMillis = finalizedSession.durationMillis,
                        audioSource = finalizedSession.audioSource,
                        audioSourceName = finalizedSession.audioSourceName
                    )

                    DeveloperLogger.info(
                        TAG,
                        "Recording finalized and saved to dedicated folder: file=${finalizedSession.filePath}, size=${finalizedFile.length()} bytes, duration=${finalizedSession.durationMillis} ms"
                    )

                    currentSession = null
                    currentTempFile = null
                    currentTargetFileName = null
                    Result.success(finalizedSession)
                } else {
                    DeveloperLogger.warning(TAG, "Recording file empty or not found: ${tempFile?.absolutePath}")
                    _recordingState.value = CallRecordingState.Error("Recording file was empty or not generated")
                    currentSession = null
                    currentTempFile = null
                    currentTargetFileName = null
                    Result.failure(IllegalStateException("Recording file missing or empty"))
                }
            } catch (e: Exception) {
                DeveloperLogger.error(TAG, "Error stopping recorder: ${e.message}", e)
                releaseRecorderInternal()
                _recordingState.value = CallRecordingState.Error("Error finalizing recording: ${e.message}")
                currentSession = null
                currentTempFile = null
                currentTargetFileName = null
                Result.failure(e)
            }
        }
    }

    override suspend fun cancel() = mutex.withLock {
        withContext(Dispatchers.IO) {
            DeveloperLogger.info(TAG, "Cancelling active recording session.")
            tickerJob?.cancel()
            tickerJob = null
            try {
                mediaRecorder?.stop()
            } catch (_: Exception) {}
            releaseRecorderInternal()

            val tempFile = currentTempFile ?: currentSession?.filePath?.let { File(it) }
            tempFile?.let { file ->
                try {
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (_: Exception) {}
            }
            currentSession = null
            currentTempFile = null
            currentTargetFileName = null
            _recordingState.value = CallRecordingState.Idle
        }
    }

    private fun startTicker(session: RecordingSession) {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(500)
                val elapsed = (SystemClock.elapsedRealtime() - recordingStartTimeRealtime).coerceAtLeast(0L)
                if (_recordingState.value is CallRecordingState.Recording) {
                    _recordingState.value = CallRecordingState.Recording(elapsed, session)
                }
            }
        }
    }

    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    fun getAudioSourceName(source: Int): String = when (source) {
        MediaRecorder.AudioSource.VOICE_CALL -> "VOICE_CALL (Direct 2-Way)"
        MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION (VoIP / Echo-Cancelled Full Duplex)"
        MediaRecorder.AudioSource.MIC -> "MIC (Acoustic Near-End / Speakerphone)"
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
        MediaRecorder.AudioSource.DEFAULT -> "DEFAULT"
        else -> "Source #$source"
    }

    /**
     * Tries starting MediaRecorder with candidate audio sources in order of 2-way recording fidelity and OEM compatibility:
     * 1. VOICE_CALL (Direct 2-way telephony uplink + downlink stream)
     * 2. VOICE_COMMUNICATION (VoIP full-duplex with AEC acoustic coupling)
     * 3. MIC (Near-end microphone, speakerphone-coupled)
     * 4. VOICE_RECOGNITION
     * 5. DEFAULT
     */
    private fun startMediaRecorderWithFallback(tempFile: File): Pair<MediaRecorder, Int> {
        val candidateSources = listOf(
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.DEFAULT
        )

        var lastException: Exception? = null
        for (source in candidateSources) {
            val sourceName = getAudioSourceName(source)
            var recorder: MediaRecorder? = null
            try {
                DeveloperLogger.info(TAG, "Attempting to initialize MediaRecorder with audio source: $sourceName ($source)")
                recorder = createMediaRecorder()
                recorder.setAudioSource(source)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(96000)
                recorder.setAudioSamplingRate(44100)
                recorder.setAudioChannels(1)
                recorder.setOutputFile(tempFile.absolutePath)

                recorder.prepare()
                recorder.start()
                DeveloperLogger.info(TAG, "MediaRecorder successfully started with audio source: $sourceName ($source)")
                return Pair(recorder, source)
            } catch (e: Exception) {
                DeveloperLogger.warning(TAG, "AudioSource $sourceName ($source) failed to start: ${e.message}")
                lastException = e
                try {
                    recorder?.reset()
                    recorder?.release()
                } catch (_: Exception) {}
                if (tempFile.exists()) {
                    try { tempFile.delete() } catch (_: Exception) {}
                }
            }
        }
        throw lastException ?: IllegalStateException("All candidate MediaRecorder audio sources failed to start")
    }

    private fun getActualAudioDuration(filePath: String): Long {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val timeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            timeStr?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            try {
                retriever?.release()
            } catch (_: Exception) {}
        }
    }

    private fun releaseRecorderInternal() {
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
    }
}
