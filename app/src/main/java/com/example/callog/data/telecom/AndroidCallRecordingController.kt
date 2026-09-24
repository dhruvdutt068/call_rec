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
    @param:ApplicationContext private val context: Context
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
                val outputDir = getRecordingDirectory()
                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                }

                val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                val formattedDate = dateFormat.format(Date(startedAt))

                val safeIdentifier = when {
                    !contactName.isNullOrBlank() && !contactName.equals("Unknown Caller", ignoreCase = true) -> {
                        contactName.trim().replace(Regex("[^a-zA-Z0-9 _-]"), "_")
                    }
                    !phoneNumber.isNullOrBlank() -> {
                        phoneNumber.trim().replace(Regex("[^a-zA-Z0-9+_-]"), "_")
                    }
                    else -> "Call_${callId.replace(Regex("[^a-zA-Z0-9_-]"), "_")}"
                }

                val recordingId = "rec_${UUID.randomUUID()}"
                val outputFile = File(outputDir, "Callog_${safeIdentifier}_${formattedDate}.m4a")

                val recorder = createMediaRecorder()
                setupRecorder(recorder, outputFile)

                recorder.prepare()
                recorder.start()
                mediaRecorder = recorder

                recordingStartTimeRealtime = SystemClock.elapsedRealtime()

                val session = RecordingSession(
                    recordingId = recordingId,
                    callId = callId,
                    phoneNumber = phoneNumber,
                    contactName = contactName,
                    personId = personId,
                    simSlot = simSlot,
                    filePath = outputFile.absolutePath,
                    startedAt = startedAt,
                    mimeType = "audio/mp4"
                )
                currentSession = session
                _recordingState.value = CallRecordingState.Recording(0L, session)

                DeveloperLogger.info(TAG, "Recording started successfully: path=${outputFile.absolutePath}, session=$recordingId")
                startTicker(session)

                Result.success(session)
            } catch (e: Exception) {
                DeveloperLogger.error(TAG, "Failed to initialize/start call recorder: ${e.message}", e)
                releaseRecorderInternal()
                _recordingState.value = CallRecordingState.Error("Failed to start recorder: ${e.message}")
                Result.failure(e)
            }
        }
    }

    override suspend fun stop(): Result<RecordingSession> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val session = currentSession
            val recorder = mediaRecorder

            if (session == null && recorder == null) {
                DeveloperLogger.info(TAG, "Stop called but no active recording session found.")
                _recordingState.value = CallRecordingState.Idle
                return@withContext Result.failure(IllegalStateException("No active recording session"))
            }

            _recordingState.value = CallRecordingState.Stopping
            DeveloperLogger.info(TAG, "Stopping call recording session: ${session?.recordingId}")

            tickerJob?.cancel()
            tickerJob = null

            try {
                try {
                    recorder?.stop()
                } catch (e: RuntimeException) {
                    DeveloperLogger.warning(TAG, "MediaRecorder stop failed (possibly too short duration): ${e.message}")
                }
                releaseRecorderInternal()

                val file = session?.filePath?.let { File(it) }
                if (file != null && file.exists() && file.length() > 0) {
                    val endedAt = System.currentTimeMillis()
                    val actualDurationMs = getActualAudioDuration(file.absolutePath).takeIf { it > 0 }
                        ?: (endedAt - session.startedAt).coerceAtLeast(1000L)

                    val finalizedSession = session.copy(
                        endedAt = endedAt,
                        durationMillis = actualDurationMs
                    )

                    _recordingState.value = CallRecordingState.Saved(
                        recordingId = finalizedSession.recordingId,
                        filePath = finalizedSession.filePath,
                        durationMillis = finalizedSession.durationMillis
                    )

                    DeveloperLogger.info(
                        TAG,
                        "Recording finalized: file=${finalizedSession.filePath}, size=${file.length()} bytes, duration=${finalizedSession.durationMillis} ms"
                    )

                    currentSession = null
                    Result.success(finalizedSession)
                } else {
                    DeveloperLogger.warning(TAG, "Recording file empty or not found: ${session?.filePath}")
                    _recordingState.value = CallRecordingState.Error("Recording file was empty or not generated")
                    currentSession = null
                    Result.failure(IllegalStateException("Recording file missing or empty"))
                }
            } catch (e: Exception) {
                DeveloperLogger.error(TAG, "Error stopping recorder: ${e.message}", e)
                releaseRecorderInternal()
                _recordingState.value = CallRecordingState.Error("Error finalizing recording: ${e.message}")
                currentSession = null
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

            currentSession?.filePath?.let { path ->
                try {
                    val file = File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (_: Exception) {}
            }
            currentSession = null
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

    private fun getRecordingDirectory(): File {
        val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)
        if (externalDir != null && (externalDir.exists() || externalDir.mkdirs())) {
            return externalDir
        }
        val internalDir = File(context.filesDir, "recordings")
        if (!internalDir.exists()) {
            internalDir.mkdirs()
        }
        return internalDir
    }

    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    private fun setupRecorder(recorder: MediaRecorder, outputFile: File) {
        try {
            // VOICE_COMMUNICATION enables acoustic echo cancellation and is optimized for two-way VoIP / call capture on modern Android
            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        } catch (e: Exception) {
            DeveloperLogger.warning(TAG, "VOICE_COMMUNICATION audio source failed, falling back to MIC: ${e.message}")
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        }

        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioEncodingBitRate(96000)
        recorder.setAudioSamplingRate(44100)
        recorder.setAudioChannels(1)
        recorder.setOutputFile(outputFile.absolutePath)
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
