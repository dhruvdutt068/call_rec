package com.example.callog.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.domain.repository.RingtoneRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RingtoneRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : RingtoneRepository {

    companion object {
        private const val TAG = "RingtoneRepositoryImpl"
        private const val PREFS_NAME = "ringtone_settings_prefs"
        private const val KEY_PREFIX_PERSON = "ringtone_person_"
        private const val KEY_LEAD_HOT = "ringtone_lead_hot"
        private const val KEY_LEAD_WARM = "ringtone_lead_warm"
        private const val KEY_LEAD_COLD = "ringtone_lead_cold"
        private const val KEY_LEAD_NEW = "ringtone_lead_new"
        private const val KEY_CUSTOMER = "ringtone_customer_type"
        private const val KEY_TITLE_PREFIX = "ringtone_title_"
        private const val RINGTONES_DIR = "ringtones"
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getRingtonesDir(): File {
        return File(context.filesDir, RINGTONES_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    private fun getUriFromPref(key: String): Uri? {
        val uriString = prefs().getString(key, null)
        if (uriString.isNullOrBlank()) return null

        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (!file.exists() || !file.isFile || file.length() <= 0L || !file.canRead()) {
                    DeveloperLogger.warning(
                        "RINGTONE_FILE_CORRUPTED",
                        "Persisted ringtone file for $key is missing, empty, or unreadable: ${file.absolutePath}"
                    )
                    prefs().edit().remove(key).remove("${KEY_TITLE_PREFIX}$key").apply()
                    null
                } else {
                    uri
                }
            } else {
                uri
            }
        } catch (e: Exception) {
            DeveloperLogger.error("RINGTONE_PARSE_ERROR", "Failed to parse ringtone URI for key: $key", e)
            null
        }
    }

    override fun hotLead(): Uri? = getUriFromPref(KEY_LEAD_HOT)

    override fun warmLead(): Uri? = getUriFromPref(KEY_LEAD_WARM)

    override fun coldLead(): Uri? = getUriFromPref(KEY_LEAD_COLD)

    override fun newLead(): Uri? = getUriFromPref(KEY_LEAD_NEW)

    override fun customer(): Uri? = getUriFromPref(KEY_CUSTOMER)

    override fun personSpecific(personId: String): Uri? = getUriFromPref("$KEY_PREFIX_PERSON$personId")

    override fun default(): Uri {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: Uri.EMPTY
    }

    private fun isInternalRingtoneUri(uri: Uri): Boolean {
        if (uri.scheme != "file") return false
        val path = uri.path ?: return false
        val internalDir = getRingtonesDir().absolutePath
        return path.startsWith(internalDir)
    }

    /**
     * Imports an external audio URI into app-private storage atomically.
     */
    private suspend fun importAudioFile(targetKey: String, sourceUri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            DeveloperLogger.info("RINGTONE_IMPORT_STARTED", "Starting private storage import for $targetKey")
            importAudioFileSync(targetKey, sourceUri)
        }
    }

    private fun importAudioFileSync(targetKey: String, sourceUri: Uri): Uri {
        val dir = getRingtonesDir()
        cleanStaleTempFiles(dir)

        val destFile = File(dir, "${targetKey}.audio")
        val tempFile = File(dir, "${targetKey}_tmp_${System.currentTimeMillis()}.tmp")

        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: throw FileNotFoundException("Cannot open stream for URI: $sourceUri")

            var bytesCopied = 0L
            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    bytesCopied = input.copyTo(output)
                }
            }

            if (bytesCopied <= 0L || !tempFile.exists() || tempFile.length() <= 0L) {
                tempFile.delete()
                throw IOException("Imported audio file is empty (0 bytes copied)")
            }

            validateAudioFile(tempFile)

            // Atomically replace previous file
            if (destFile.exists()) {
                destFile.delete()
            }
            if (!tempFile.renameTo(destFile)) {
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }

            val internalUri = Uri.fromFile(destFile)
            val title = resolveUriTitle(sourceUri, destFile)

            prefs().edit()
                .putString(targetKey, internalUri.toString())
                .putString("${KEY_TITLE_PREFIX}$targetKey", title)
                .apply()

            DeveloperLogger.info(
                "RINGTONE_IMPORT_COMPLETED",
                "Successfully imported $targetKey to ${destFile.absolutePath} ($bytesCopied bytes, title='$title')"
            )
            return internalUri
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            DeveloperLogger.error("RINGTONE_IMPORT_FAILED", "Import failed for $targetKey from $sourceUri", e)
            throw e
        }
    }

    private fun validateAudioFile(file: File) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever check failed for ${file.name}, verifying size", e)
            if (file.length() < 128) {
                throw IOException("Audio file is too small or corrupt: ${file.length()} bytes")
            }
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private fun cleanStaleTempFiles(dir: File) {
        try {
            val now = System.currentTimeMillis()
            dir.listFiles { file -> file.name.endsWith(".tmp") && (now - file.lastModified() > 3600_000) }?.forEach {
                it.delete()
            }
        } catch (_: Exception) {}
    }

    private fun removeRingtone(key: String, fileName: String) {
        prefs().edit()
            .remove(key)
            .remove("${KEY_TITLE_PREFIX}$key")
            .apply()
        val file = File(getRingtonesDir(), fileName)
        if (file.exists()) {
            file.delete()
        }
    }

    private fun resolveUriTitle(sourceUri: Uri, fallbackFile: File): String {
        if (sourceUri.scheme == "content") {
            try {
                context.contentResolver.query(sourceUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            val name = cursor.getString(index)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        try {
            val ringtone = RingtoneManager.getRingtone(context, sourceUri)
            val title = ringtone?.getTitle(context)
            if (!title.isNullOrBlank() && !title.startsWith("content://") && !title.startsWith("file://")) {
                return title
            }
        } catch (_: Exception) {}

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(fallbackFile.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            retriever.release()
            if (!title.isNullOrBlank()) return title
        } catch (_: Exception) {}

        return sourceUri.lastPathSegment?.substringAfterLast('/') ?: "Custom Audio"
    }

    override fun getRingtoneTitle(uri: Uri?): String {
        if (uri == null) return "System Default"

        val keys = listOf(KEY_LEAD_HOT, KEY_LEAD_WARM, KEY_LEAD_COLD, KEY_LEAD_NEW, KEY_CUSTOMER)
        for (key in keys) {
            val storedUriStr = prefs().getString(key, null)
            if (storedUriStr == uri.toString()) {
                val title = prefs().getString("${KEY_TITLE_PREFIX}$key", null)
                if (!title.isNullOrBlank()) return title
            }
        }

        return try {
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.getTitle(context) ?: "Custom Audio"
        } catch (e: Exception) {
            "Custom Audio"
        }
    }

    override fun setRingtoneForPerson(personId: String, uri: Uri?) {
        val key = "$KEY_PREFIX_PERSON$personId"
        if (uri == null) {
            removeRingtone(key, "${key}.audio")
        } else {
            if (isInternalRingtoneUri(uri)) {
                prefs().edit().putString(key, uri.toString()).apply()
            } else {
                runCatching { importAudioFileSync(key, uri) }
            }
        }
    }

    override fun setRingtoneForLeadHot(uri: Uri?) {
        if (uri == null) {
            removeRingtone(KEY_LEAD_HOT, "${KEY_LEAD_HOT}.audio")
        } else {
            if (isInternalRingtoneUri(uri)) {
                prefs().edit().putString(KEY_LEAD_HOT, uri.toString()).apply()
            } else {
                runCatching { importAudioFileSync(KEY_LEAD_HOT, uri) }
            }
        }
    }

    override fun setRingtoneForLeadWarm(uri: Uri?) {
        if (uri == null) {
            removeRingtone(KEY_LEAD_WARM, "${KEY_LEAD_WARM}.audio")
        } else {
            if (isInternalRingtoneUri(uri)) {
                prefs().edit().putString(KEY_LEAD_WARM, uri.toString()).apply()
            } else {
                runCatching { importAudioFileSync(KEY_LEAD_WARM, uri) }
            }
        }
    }

    override fun setRingtoneForLeadCold(uri: Uri?) {
        if (uri == null) {
            removeRingtone(KEY_LEAD_COLD, "${KEY_LEAD_COLD}.audio")
        } else {
            if (isInternalRingtoneUri(uri)) {
                prefs().edit().putString(KEY_LEAD_COLD, uri.toString()).apply()
            } else {
                runCatching { importAudioFileSync(KEY_LEAD_COLD, uri) }
            }
        }
    }

    override fun setRingtoneForCustomer(uri: Uri?) {
        if (uri == null) {
            removeRingtone(KEY_CUSTOMER, "${KEY_CUSTOMER}.audio")
        } else {
            if (isInternalRingtoneUri(uri)) {
                prefs().edit().putString(KEY_CUSTOMER, uri.toString()).apply()
            } else {
                runCatching { importAudioFileSync(KEY_CUSTOMER, uri) }
            }
        }
    }

    override suspend fun importRingtoneForLeadHot(sourceUri: Uri): Result<Uri> =
        importAudioFile(KEY_LEAD_HOT, sourceUri)

    override suspend fun importRingtoneForLeadWarm(sourceUri: Uri): Result<Uri> =
        importAudioFile(KEY_LEAD_WARM, sourceUri)

    override suspend fun importRingtoneForLeadCold(sourceUri: Uri): Result<Uri> =
        importAudioFile(KEY_LEAD_COLD, sourceUri)

    override suspend fun importRingtoneForCustomer(sourceUri: Uri): Result<Uri> =
        importAudioFile(KEY_CUSTOMER, sourceUri)

    override suspend fun importRingtoneForPerson(personId: String, sourceUri: Uri): Result<Uri> =
        importAudioFile("$KEY_PREFIX_PERSON$personId", sourceUri)
}
