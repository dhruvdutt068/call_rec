package com.example.callog.data.provider

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.callog.core.constants.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoveredRecording(
    val filePath: String,
    val phoneNumber: String,
    val timestamp: Long
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
                    files?.forEach { file ->
                        if (file.isFile) {
                            val ext = file.extension.lowercase()
                            if (ext in Constants.SUPPORTED_RECORDING_EXTENSIONS) {
                                val parsed = parseRecordingMetadata(file.name, file.lastModified())
                                if (parsed != null) {
                                    list.add(
                                        DiscoveredRecording(
                                            filePath = file.absolutePath,
                                            phoneNumber = parsed.first,
                                            timestamp = parsed.second
                                        )
                                    )
                                    Log.d("RecordingScanner", "Manually discovered recording: ${file.absolutePath}")
                                }
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
            MediaStore.Audio.Media.DATE_ADDED
        )

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )

            cursor?.use { c ->
                val dataIdx = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                val nameIdx = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val dateAddedIdx = c.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

                while (c.moveToNext()) {
                    val filePath = if (dataIdx != -1) c.getString(dataIdx) ?: "" else ""
                    val displayName = if (nameIdx != -1) c.getString(nameIdx) ?: "" else ""
                    val dateAdded = if (dateAddedIdx != -1) c.getLong(dateAddedIdx) else 0L

                    if (filePath.isEmpty() || displayName.isEmpty()) continue

                    val isRecording = isCallRecordingFile(filePath, displayName)
                    if (isRecording) {
                        val parsed = parseRecordingMetadata(displayName, dateAdded * 1000L)
                        if (parsed != null) {
                            list.add(
                                DiscoveredRecording(
                                    filePath = filePath,
                                    phoneNumber = parsed.first,
                                    timestamp = parsed.second
                                )
                            )
                        }
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

    private fun parseRecordingMetadata(displayName: String, fallbackTimestamp: Long): Pair<String, Long>? {
        val nameWithoutExt = displayName.substringBeforeLast(".")
        
        // Match Date-Time pattern: YYYY-MM-DD HH-MM-SS or YYYY-MM-DD HH_MM_SS
        val dateTimeRegex = Regex("(\\d{4})-(\\d{2})-(\\d{2})\\s+(\\d{2})[-_](\\d{2})[-_](\\d{2})")
        val matchResult = dateTimeRegex.find(nameWithoutExt)
        
        var timestamp: Long? = null
        var identifier = nameWithoutExt
        
        if (matchResult != null) {
            val dateTimeStr = matchResult.value
            timestamp = parseCustomDateTimeString(dateTimeStr)
            identifier = nameWithoutExt.substring(0, matchResult.range.first).trim()
        } else {
            // Check for simple token timestamp formats (e.g. YYYYMMDDHHMMSS or YYYYMMDD_HHMMSS)
            val simpleDateTimeRegex = Regex("\\d{8}[_\\s]?\\d{6}")
            val simpleMatch = simpleDateTimeRegex.find(nameWithoutExt)
            if (simpleMatch != null) {
                val cleanDateTime = simpleMatch.value.replace(Regex("[^0-9]"), "")
                timestamp = parseSimpleDateTimeString(cleanDateTime)
                identifier = nameWithoutExt.substring(0, simpleMatch.range.first).trim()
            }
        }

        // Clean trailing spaces and separators from identifier (e.g. "Chirag Pujara -")
        identifier = identifier.trim().removeSuffix("_").removeSuffix("-").trim()
        
        if (identifier.isEmpty()) return null

        return Pair(identifier, timestamp ?: fallbackTimestamp)
    }

    private fun parseCustomDateTimeString(dateTimeStr: String): Long? {
        return try {
            // Standardize separator for parsing (replace - or _ in time component with -)
            val cleanStr = dateTimeStr.replace("_", "-")
            val sdf = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US)
            sdf.timeZone = TimeZone.getDefault()
            sdf.parse(cleanStr)?.time
        } catch (e: Exception) {
            null
        }
    }

    private fun parseSimpleDateTimeString(dateTimeStr: String): Long? {
        return try {
            val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            sdf.timeZone = TimeZone.getDefault()
            sdf.parse(dateTimeStr)?.time
        } catch (e: Exception) {
            null
        }
    }
}
