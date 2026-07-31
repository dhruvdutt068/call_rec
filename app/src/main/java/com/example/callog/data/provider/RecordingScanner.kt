package com.example.callog.data.provider

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.callog.core.constants.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoveredRecording(
    val filePath: String,
    val phoneNumber: String?,
    val contactName: String?,
    val timestamp: Long,
    val durationMs: Long,
    val fileSize: Long,
    val lastModified: Long,
    val parserName: String
)

@Singleton
class RecordingScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun scanRecordings(): List<DiscoveredRecording> {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            Log.w("RecordingScanner", "Permission not granted to read audio files")
            return emptyList()
        }

        val list = mutableListOf<DiscoveredRecording>()

        // 1. Scan custom directory if configured
        val prefs = context.getSharedPreferences("firebase_config_prefs", Context.MODE_PRIVATE)
        val customPath = prefs.getString("custom_recording_path", "") ?: ""
        if (customPath.isNotEmpty()) {
            try {
                val directory = File(customPath)
                if (directory.exists() && directory.isDirectory) {
                    val files = directory.listFiles()
                    Log.d("RecordingScanner", "Scanning custom directory: $customPath, found ${files?.size ?: 0} files")
                    files?.forEach { file ->
                        if (file.isFile) {
                            val ext = file.extension.lowercase()
                            if (ext in Constants.SUPPORTED_RECORDING_EXTENSIONS) {
                                val displayName = file.name
                                Log.d("RecordingScanner", "Scanning file: $displayName")
                                val metadata = RecordingParserChain.parse(displayName, file.lastModified())
                                Log.d("RecordingScanner", "Parsed successfully: Phone = ${metadata.phoneNumber}, ContactName = ${metadata.contactName}, Timestamp = ${metadata.timestamp}, Parser = ${metadata.parserName}")
                                
                                val duration = getRecordingDuration(file.absolutePath)
                                list.add(
                                    DiscoveredRecording(
                                        filePath = file.absolutePath,
                                        phoneNumber = metadata.phoneNumber,
                                        contactName = metadata.contactName,
                                        timestamp = metadata.timestamp ?: file.lastModified(),
                                        durationMs = duration,
                                        fileSize = file.length(),
                                        lastModified = file.lastModified(),
                                        parserName = metadata.parserName
                                    )
                                )
                                Log.d("RecordingScanner", "Manually discovered recording: ${file.absolutePath}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RecordingScanner", "Error scanning custom directory: $customPath", e)
            }
        }

        // 2. Query all audio files via MediaStore
        val projection = arrayOf(
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.SIZE
        )

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )

            Log.d("RecordingScanner", "Querying MediaStore, cursor size = ${cursor?.count ?: 0}")

            cursor?.use { c ->
                val dataIdx = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                val nameIdx = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val dateAddedIdx = c.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedIdx = c.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                val sizeIdx = c.getColumnIndex(MediaStore.Audio.Media.SIZE)

                while (c.moveToNext()) {
                    val filePath = if (dataIdx != -1) c.getString(dataIdx) ?: "" else ""
                    val displayName = if (nameIdx != -1) c.getString(nameIdx) ?: "" else ""
                    val dateAdded = if (dateAddedIdx != -1) c.getLong(dateAddedIdx) else 0L
                    val dateModified = if (dateModifiedIdx != -1) c.getLong(dateModifiedIdx) else 0L
                    val fileSize = if (sizeIdx != -1) c.getLong(sizeIdx) else 0L

                    if (filePath.isEmpty() || displayName.isEmpty()) continue

                    val isRecording = isCallRecordingFile(filePath, displayName)
                    if (isRecording) {
                        Log.d("RecordingScanner", "Scanning file: $displayName")
                        val file = File(filePath)
                        val lastMod = if (dateModified > 0) dateModified * 1000L else if (file.exists()) file.lastModified() else dateAdded * 1000L
                        val metadata = RecordingParserChain.parse(displayName, lastMod)
                        Log.d("RecordingScanner", "Parsed successfully: Phone = ${metadata.phoneNumber}, ContactName = ${metadata.contactName}, Timestamp = ${metadata.timestamp}, Parser = ${metadata.parserName}")
                        
                        val duration = getRecordingDuration(filePath)
                        list.add(
                            DiscoveredRecording(
                                filePath = filePath,
                                phoneNumber = metadata.phoneNumber,
                                contactName = metadata.contactName,
                                timestamp = metadata.timestamp ?: lastMod,
                                durationMs = duration,
                                fileSize = if (fileSize > 0) fileSize else if (file.exists()) file.length() else 0L,
                                lastModified = lastMod,
                                parserName = metadata.parserName
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RecordingScanner", "Error querying MediaStore for audio recordings", e)
        }

        val distinctList = list.distinctBy { it.filePath }
        Log.w("RecordingScanner", "Scanned and found ${distinctList.size} unique matching recording files")
        return distinctList
    }

    private fun isCallRecordingFile(filePath: String, displayName: String): Boolean {
        // 1. Check if folder path contains call recording folders
        val pathLower = filePath.lowercase()
        val inRecordingFolder = Constants.RECORDING_DIRECTORIES.any { dir ->
            pathLower.contains("/${dir.lowercase()}/") || pathLower.endsWith("/${dir.lowercase()}")
        }
        if (inRecordingFolder) return true

        // 2. Check if file extension is supported
        val ext = displayName.substringAfterLast(".", "").lowercase()
        if (ext !in Constants.SUPPORTED_RECORDING_EXTENSIONS) return false

        // 3. Check display name hints
        val nameLower = displayName.lowercase()
        if (nameLower.contains("call") || nameLower.contains("record") || nameLower.contains("recorder")) {
            return true
        }

        return false
    }

    private fun getRecordingDuration(filePath: String): Long {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val timeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            return timeStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            return 0L
        } finally {
            try {
                retriever?.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
