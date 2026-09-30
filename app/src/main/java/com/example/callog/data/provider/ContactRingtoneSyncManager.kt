package com.example.callog.data.provider

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.data.local.dao.LeadStatusDao
import com.example.callog.data.local.entity.LeadMasterEntity
import com.example.callog.data.telecom.MediaStoreRingtoneExporter
import com.example.callog.domain.model.LeadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages two-way synchronization between CRM leads and Android's native ContactsContract.
 *
 * Injects MediaStore-exported custom ringtones directly into ContactsContract.Contacts.CUSTOM_RINGTONE
 * so Samsung's native Phone app rings with the designated CRM tone natively without runtime in-app audio races.
 */
@Singleton
class ContactRingtoneSyncManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaStoreRingtoneExporter: MediaStoreRingtoneExporter,
    private val leadStatusDao: LeadStatusDao
) {
    companion object {
        private const val TAG = "ContactRingtoneSyncManager"
        const val ACCOUNT_TYPE = "com.example.callog"
        const val ACCOUNT_NAME = "Callog CRM"
    }

    /**
     * Fast check (<5ms) to determine if a phone number already exists in device contacts.
     */
    fun isContactAlreadyInDevice(phoneNumber: String): Boolean {
        if (phoneNumber.isBlank()) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber.trim()))
        val projection = arrayOf(ContactsContract.PhoneLookup._ID)

        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Error checking PhoneLookup for $phoneNumber", e)
            false
        }
    }

    /**
     * Resolves the domain LeadStatus from the foreign key `fkLeadStatus`.
     */
    suspend fun resolveLeadStatus(fkLeadStatus: Long?, fkLeadStage: Long?): LeadStatus = withContext(Dispatchers.IO) {
        if (fkLeadStatus == null) return@withContext LeadStatus.UNKNOWN
        val allStatuses = try {
            leadStatusDao.getAllLeadStatuses()
        } catch (_: Exception) {
            emptyList()
        }
        val match = allStatuses.find { it.id == fkLeadStatus }
        if (match != null) {
            LeadStatus.fromString(match.name)
        } else {
            // Fallback heuristics based on ID or default
            when (fkLeadStatus) {
                1L -> LeadStatus.HOT
                2L -> LeadStatus.WARM
                3L -> LeadStatus.COLD
                4L -> LeadStatus.NEW
                else -> LeadStatus.CUSTOMER
            }
        }
    }

    /**
     * Proactively syncs a list of CRM leads to Android ContactsContract with their respective custom ringtones.
     */
    suspend fun syncAllLeadsToContacts(leads: List<LeadMasterEntity>) = withContext(Dispatchers.IO) {
        if (leads.isEmpty()) return@withContext
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            DeveloperLogger.warning("CONTACT_SYNC_SKIPPED", "Missing READ_CONTACTS or WRITE_CONTACTS permission for sync.")
            return@withContext
        }

        DeveloperLogger.info("CONTACT_SYNC_STARTED", "Starting contact ringtone sync for ${leads.size} leads.")
        var updatedCount = 0
        var insertedCount = 0

        for (lead in leads) {
            val phoneStr = lead.leadMobile?.toLong()?.toString()?.trim() ?: continue
            if (phoneStr.isBlank()) continue

            try {
                val leadStatus = resolveLeadStatus(lead.fkLeadStatus, lead.fkLeadStage)
                val ringtoneUri = mediaStoreRingtoneExporter.getExportedRingtoneUri(leadStatus)

                val existingContactId = getExistingContactId(phoneStr)
                if (existingContactId != null) {
                    // Update existing contact's ringtone
                    updateContactRingtone(existingContactId, ringtoneUri)
                    updatedCount++
                } else {
                    // Insert new CRM contact with custom ringtone
                    val displayName = lead.leadName?.ifBlank { null } ?: "Lead #${lead.id}"
                    insertCrmContact(displayName, phoneStr, ringtoneUri)
                    insertedCount++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed syncing lead ${lead.id} ($phoneStr): ${e.message}")
            }
        }

        DeveloperLogger.success(
            "CONTACT_SYNC_COMPLETED",
            "Finished contact ringtone sync: $updatedCount updated, $insertedCount inserted."
        )
    }

    /**
     * Inserts or updates a single lead on-demand (e.g. during an incoming call from an unsynced lead).
     */
    suspend fun insertOrUpdateSingleLead(lead: LeadMasterEntity): Uri? = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext null
        }

        val phoneStr = lead.leadMobile?.toLong()?.toString()?.trim() ?: return@withContext null
        val leadStatus = resolveLeadStatus(lead.fkLeadStatus, lead.fkLeadStage)
        val ringtoneUri = mediaStoreRingtoneExporter.getExportedRingtoneUri(leadStatus)

        try {
            val existingContactId = getExistingContactId(phoneStr)
            if (existingContactId != null) {
                updateContactRingtone(existingContactId, ringtoneUri)
            } else {
                val displayName = lead.leadName?.ifBlank { null } ?: "Lead #${lead.id}"
                insertCrmContact(displayName, phoneStr, ringtoneUri)
            }
            DeveloperLogger.info("SINGLE_LEAD_SYNCED", "Single lead synced to contacts: $phoneStr -> $ringtoneUri")
            ringtoneUri
        } catch (e: Exception) {
            DeveloperLogger.error("SINGLE_LEAD_SYNC_FAILED", "Failed syncing single lead $phoneStr", e)
            null
        }
    }

    private fun getExistingContactId(phoneNumber: String): Long? {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        val projection = arrayOf(ContactsContract.PhoneLookup._ID)

        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup._ID)
                    if (idIdx != -1) cursor.getLong(idIdx) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun updateContactRingtone(contactId: Long, ringtoneUri: Uri) {
        val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        val values = ContentValues().apply {
            put(ContactsContract.Contacts.CUSTOM_RINGTONE, ringtoneUri.toString())
        }
        context.contentResolver.update(contactUri, values, null, null)
    }

    private fun insertCrmContact(name: String, phoneNumber: String, ringtoneUri: Uri) {
        val ops = ArrayList<ContentProviderOperation>()

        // 1. Raw Contact
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, ACCOUNT_TYPE)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, ACCOUNT_NAME)
                .withValue(ContactsContract.RawContacts.CUSTOM_RINGTONE, ringtoneUri.toString())
                .build()
        )

        // 2. Structured Name
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build()
        )

        // 3. Phone Number
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_WORK)
                .build()
        )

        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }
}
