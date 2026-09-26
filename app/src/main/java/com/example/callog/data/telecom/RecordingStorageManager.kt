package com.example.callog.data.telecom

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.StatFs
import com.example.callog.core.diagnostics.DeveloperLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingStorageManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "RecordingStorageMgr"
        private const val PREFS_NAME = "firebase_config_prefs"
        private const val KEY_CUSTOM_RECORDING_PATH = "custom_recording_path"
        const val DEDICATED_FOLDER_NAME = "Callog"
        private const val MIN_STORAGE_REQUIRED_BYTES = 10 * 1024 * 1024L // 10 MB minimum
    }

    /**
     * Resolves the dedicated directory for storing call recordings.
     * Order of priority:
     * 1. Valid user-configured custom directory (via Settings)
     * 2. Dedicated public phone directory: /storage/emulated/0/Recordings/Callog
     * 3. Fallback to public Music/Callog
     * 4. Resilient fail-safe fallback: App-specific external storage
     */
    fun getDedicatedRecordingDirectory(): File {
        // 1. Check custom path configured by user in Settings
        val customPath = getCustomRecordingPath()
        if (customPath.isNotBlank()) {
            try {
                val customDir = File(customPath.trim())
                if ((customDir.exists() || customDir.mkdirs()) && customDir.canWrite()) {
                    DeveloperLogger.info(TAG, "Using user-configured dedicated recording directory: ${customDir.absolutePath}")
                    return customDir
                } else {
                    DeveloperLogger.warning(TAG, "Configured custom recording path is not writable: $customPath, falling back to default dedicated folder.")
                }
            } catch (e: Exception) {
                DeveloperLogger.warning(TAG, "Failed to access custom recording directory ($customPath): ${e.message}")
            }
        }

        // 2. Dedicated public directory: Environment.DIRECTORY_RECORDINGS/Callog
        try {
            val publicRecordings = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS)
            val callogDir = File(publicRecordings, DEDICATED_FOLDER_NAME)
            if (callogDir.exists() || callogDir.mkdirs()) {
                if (callogDir.canWrite()) {
                    DeveloperLogger.info(TAG, "Using default dedicated public recording directory: ${callogDir.absolutePath}")
                    return callogDir
                }
            }
        } catch (e: Exception) {
            DeveloperLogger.warning(TAG, "Could not access public Recordings/$DEDICATED_FOLDER_NAME directory: ${e.message}")
        }

        // 3. Fallback: Environment.DIRECTORY_MUSIC/Callog
        try {
            val publicMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val musicCallogDir = File(publicMusic, DEDICATED_FOLDER_NAME)
            if (musicCallogDir.exists() || musicCallogDir.mkdirs()) {
                if (musicCallogDir.canWrite()) {
                    DeveloperLogger.info(TAG, "Using public Music/$DEDICATED_FOLDER_NAME directory: ${musicCallogDir.absolutePath}")
                    return musicCallogDir
                }
            }
        } catch (e: Exception) {
            DeveloperLogger.warning(TAG, "Could not access public Music/$DEDICATED_FOLDER_NAME directory: ${e.message}")
        }

        // 4. Fail-safe: App-specific external storage
        val externalAppDir = context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)
        if (externalAppDir != null && (externalAppDir.exists() || externalAppDir.mkdirs())) {
            DeveloperLogger.info(TAG, "Using app external storage dedicated directory: ${externalAppDir.absolutePath}")
            return externalAppDir
        }

        // 5. Ultimate fallback: internal filesDir
        val internalDir = File(context.filesDir, "recordings")
        if (!internalDir.exists()) {
            internalDir.mkdirs()
        }
        DeveloperLogger.warning(TAG, "Using internal app directory for recordings: ${internalDir.absolutePath}")
        return internalDir
    }

    /**
     * Checks if the target storage directory has sufficient space to record audio.
     */
    fun hasAvailableStorage(targetDir: File = getDedicatedRecordingDirectory(), minBytes: Long = MIN_STORAGE_REQUIRED_BYTES): Boolean {
        return try {
            val stat = StatFs(targetDir.absolutePath)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            availableBytes >= minBytes
        } catch (e: Exception) {
            DeveloperLogger.warning(TAG, "Failed to read storage StatFs for ${targetDir.absolutePath}: ${e.message}")
            true
        }
    }

    /**
     * Creates a temporary working file in app-controlled storage where MediaRecorder
     * is guaranteed to have direct low-level write access without Scoped Storage denials.
     */
    fun getTemporaryRecordingFile(recordingId: String): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)
            ?: context.filesDir
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        return File(baseDir, "temp_rec_${recordingId}.m4a")
    }

    /**
     * Finalizes the recording: copies/moves the audio file into the dedicated directory
     * on phone storage, registers it in MediaStore, and triggers MediaScanner.
     * Returns the finalized File.
     */
    fun finalizeRecordingFile(
        tempFile: File,
        targetFileName: String,
        callerIdentifier: String,
        startedAt: Long,
        durationMillis: Long
    ): File {
        val targetDir = getDedicatedRecordingDirectory()
        val targetFile = File(targetDir, targetFileName)

        val customPath = getCustomRecordingPath()
        val isCustom = customPath.isNotBlank()

        var finalFile: File? = null

        // 1. If targeting standard public storage on Android Q+, publish via MediaStore
        if (!isCustom && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val uri = publishToMediaStore(
                sourceFile = tempFile,
                displayName = targetFileName,
                title = "Callog $callerIdentifier",
                timestamp = startedAt,
                durationMs = durationMillis
            )
            if (uri != null) {
                val resolvedFile = getFileFromUri(uri, targetFile)
                if (resolvedFile.exists() && resolvedFile.length() > 0) {
                    finalFile = resolvedFile
                    try {
                        tempFile.delete()
                    } catch (_: Exception) {}
                    DeveloperLogger.info(TAG, "Recording published via MediaStore to: ${resolvedFile.absolutePath}")
                }
            }
        }

        // 2. Direct copy fallback (for custom directory, older Android, or if MediaStore insertion was unavailable)
        if (finalFile == null) {
            try {
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                tempFile.copyTo(targetFile, overwrite = true)
                finalFile = targetFile
                try {
                    tempFile.delete()
                } catch (_: Exception) {}
                DeveloperLogger.info(TAG, "Recording copied directly to dedicated directory: ${targetFile.absolutePath}")
            } catch (e: Exception) {
                DeveloperLogger.warning(TAG, "Direct copy to dedicated directory (${targetFile.absolutePath}) failed: ${e.message}. Keeping app external file.")
                finalFile = tempFile
            }
        }

        // Broadcast to OS media scanner
        notifyMediaScanner(finalFile)

        return finalFile
    }

    /**
     * Resolves the real filesystem File from a MediaStore Uri if available, otherwise returning the fallback.
     */
    fun getFileFromUri(uri: android.net.Uri, fallbackFile: File): File {
        return try {
            val projection = arrayOf(android.provider.MediaStore.Audio.Media.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media.DATA)
                    if (idx != -1) {
                        val path = cursor.getString(idx)
                        if (!path.isNullOrBlank()) {
                            val resolved = File(path)
                            if (resolved.exists() && resolved.length() > 0) {
                                return resolved
                            }
                        }
                    }
                }
            }
            if (fallbackFile.exists() && fallbackFile.length() > 0) fallbackFile else fallbackFile
        } catch (e: Exception) {
            DeveloperLogger.warning(TAG, "Could not resolve file path from uri ($uri): ${e.message}")
            fallbackFile
        }
    }

    /**
     * Publishes the recording into the Android MediaStore Audio collection.
     */
    fun publishToMediaStore(
        sourceFile: File,
        displayName: String,
        title: String,
        timestamp: Long,
        durationMs: Long
    ): android.net.Uri? {
        return try {
            val resolver = context.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(android.provider.MediaStore.Audio.Media.TITLE, title)
                put(android.provider.MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(android.provider.MediaStore.Audio.Media.DATE_ADDED, timestamp / 1000L)
                put(android.provider.MediaStore.Audio.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000L)
                put(android.provider.MediaStore.Audio.Media.DURATION, durationMs)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_RECORDINGS}/$DEDICATED_FOLDER_NAME")
                    put(android.provider.MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                values.clear()
                values.put(android.provider.MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            DeveloperLogger.info(TAG, "Successfully published recording to MediaStore: $uri")
            uri
        } catch (e: Exception) {
            DeveloperLogger.warning(TAG, "Failed to publish recording to MediaStore: ${e.message}")
            null
        }
    }

    /**
     * Notifies Android's MediaScannerConnection so newly created recordings
     * in the dedicated directory are immediately indexed by the OS, system file
     * manager, and media players.
     */
    fun notifyMediaScanner(file: File) {
        try {
            if (file.exists() && file.length() > 0) {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf("audio/mp4", "audio/m4a"),
                    { path, uri ->
                        DeveloperLogger.info(TAG, "MediaScanner successfully indexed call recording: path=$path, uri=$uri")
                    }
                )
            }
        } catch (e: Exception) {
            DeveloperLogger.warning(TAG, "MediaScanner notification failed for ${file.absolutePath}: ${e.message}")
        }
    }

    /**
     * Returns the user-configured custom recording path from SharedPreferences, or empty string.
     */
    fun getCustomRecordingPath(): String {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_CUSTOM_RECORDING_PATH, "") ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Sets or resets the custom recording path.
     */
    fun setCustomRecordingPath(path: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_CUSTOM_RECORDING_PATH, path.trim()).apply()
            DeveloperLogger.info(TAG, "Updated custom recording path: $path")
        } catch (e: Exception) {
            DeveloperLogger.error(TAG, "Failed to set custom recording path: ${e.message}", e)
        }
    }

    /**
     * Clears the custom recording path to revert back to default dedicated phone directory.
     */
    fun resetToDefaultDedicatedDirectory() {
        setCustomRecordingPath("")
    }

    /**
     * Returns human-readable label of current dedicated directory.
     */
    fun getDedicatedDirectoryDisplayPath(): String {
        val custom = getCustomRecordingPath()
        return if (custom.isNotBlank()) {
            custom
        } else {
            val defaultDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS),
                DEDICATED_FOLDER_NAME
            )
            defaultDir.absolutePath
        }
    }
}
