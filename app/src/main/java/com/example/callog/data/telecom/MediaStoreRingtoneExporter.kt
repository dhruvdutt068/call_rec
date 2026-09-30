package com.example.callog.data.telecom

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.domain.model.LeadStatus
import com.example.callog.domain.repository.RingtoneRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports private app-internal ringtone audio files to the system MediaStore.
 *
 * Ensures that Samsung InCallUI (which runs as a separate system process UID)
 * can resolve and play custom contact ringtones via ContactsContract.Contacts.CUSTOM_RINGTONE
 * without suffering Scoped Storage SecurityExceptions.
 */
@Singleton
class MediaStoreRingtoneExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val ringtoneRepository: RingtoneRepository
) {
    companion object {
        private const val TAG = "MediaStoreRingtoneExporter"
        private const val CALLOG_RINGTONE_PREFIX = "Callog CRM"
    }

    /**
     * Resolves a publicly readable ringtone Content URI suitable for ContactsContract.
     * If the configured ringtone is a private file, exports it to MediaStore.Audio.Media.
     */
    fun getExportedRingtoneUri(status: LeadStatus?): Uri {
        val defaultSystemUri = ringtoneRepository.default()
        val configuredUri = when (status) {
            LeadStatus.HOT -> ringtoneRepository.hotLead()
            LeadStatus.WARM -> ringtoneRepository.warmLead()
            LeadStatus.COLD -> ringtoneRepository.coldLead()
            LeadStatus.NEW -> ringtoneRepository.newLead()
            LeadStatus.CUSTOMER -> ringtoneRepository.customer()
            else -> null
        } ?: return defaultSystemUri

        // If it's already a public MediaStore content URI, Samsung InCallUI can read it directly
        if (configuredUri.scheme == "content" && configuredUri.authority == "media") {
            return configuredUri
        }

        // If it's a file URI or private content URI, export to MediaStore
        return try {
            exportFileToMediaStore(configuredUri, status ?: LeadStatus.HOT) ?: defaultSystemUri
        } catch (e: Exception) {
            DeveloperLogger.error("MEDIA_STORE_EXPORT_ERROR", "Failed exporting ringtone for $status: ${e.message}", e)
            defaultSystemUri
        }
    }

    private fun exportFileToMediaStore(sourceUri: Uri, status: LeadStatus): Uri? {
        val file = if (sourceUri.scheme == "file") {
            File(sourceUri.path ?: return null)
        } else {
            // If it's a private content URI, copy it to a temp file first
            val temp = File(context.cacheDir, "export_tmp_${status.name}.audio")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            temp
        }

        if (!file.exists() || file.length() <= 0L) {
            Log.w(TAG, "Source ringtone file missing or empty: ${file.absolutePath}")
            return null
        }

        val targetTitle = "$CALLOG_RINGTONE_PREFIX ${status.name.lowercase().replaceFirstChar { it.uppercase() }}"
        val fileName = "callog_lead_${status.name.lowercase()}.mp3"

        // 1. Check if already exported and exists in MediaStore
        val existingUri = findExistingRingtoneInMediaStore(targetTitle)
        if (existingUri != null) {
            DeveloperLogger.info("MEDIA_STORE_ALREADY_EXISTS", "Found existing MediaStore ringtone for $status: $existingUri")
            return existingUri
        }

        // 2. Insert new entry in MediaStore
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, targetTitle)
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.IS_RINGTONE, 1)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, 0)
            put(MediaStore.Audio.Media.IS_ALARM, 0)
            put(MediaStore.Audio.Media.IS_MUSIC, 0)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Ringtones/Callog")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val newUri = context.contentResolver.insert(collection, values) ?: return null

        try {
            FileInputStream(file).use { input ->
                context.contentResolver.openOutputStream(newUri)?.use { output ->
                    input.copyTo(output)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(newUri, updateValues, null, null)
            }

            DeveloperLogger.success(
                "MEDIA_STORE_EXPORT_SUCCESS",
                "Exported CRM ringtone for $status to public MediaStore: $newUri"
            )
            return newUri
        } catch (e: Exception) {
            context.contentResolver.delete(newUri, null, null)
            throw e
        }
    }

    private fun findExistingRingtoneInMediaStore(title: String): Uri? {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE)
        val selection = "${MediaStore.Audio.Media.TITLE} = ?"
        val selectionArgs = arrayOf(title)

        return try {
            context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                    if (idIdx != -1) {
                        val id = cursor.getLong(idIdx)
                        ContentUris.withAppendedId(collection, id)
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying MediaStore for $title", e)
            null
        }
    }
}
