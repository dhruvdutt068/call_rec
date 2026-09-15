package com.example.callog.data.repository

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import com.example.callog.domain.repository.RingtoneRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getUriFromPref(key: String): Uri? {
        val uriString = prefs().getString(key, null)
        return if (!uriString.isNullOrBlank()) {
            try {
                Uri.parse(uriString)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse ringtone URI for key: $key", e)
                null
            }
        } else {
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

    override fun setRingtoneForPerson(personId: String, uri: Uri?) {
        val key = "$KEY_PREFIX_PERSON$personId"
        if (uri == null) {
            prefs().edit().remove(key).apply()
        } else {
            prefs().edit().putString(key, uri.toString()).apply()
        }
    }

    override fun setRingtoneForLeadHot(uri: Uri?) {
        if (uri == null) {
            prefs().edit().remove(KEY_LEAD_HOT).apply()
        } else {
            prefs().edit().putString(KEY_LEAD_HOT, uri.toString()).apply()
        }
    }

    override fun setRingtoneForLeadWarm(uri: Uri?) {
        if (uri == null) {
            prefs().edit().remove(KEY_LEAD_WARM).apply()
        } else {
            prefs().edit().putString(KEY_LEAD_WARM, uri.toString()).apply()
        }
    }

    override fun setRingtoneForLeadCold(uri: Uri?) {
        if (uri == null) {
            prefs().edit().remove(KEY_LEAD_COLD).apply()
        } else {
            prefs().edit().putString(KEY_LEAD_COLD, uri.toString()).apply()
        }
    }

    override fun setRingtoneForCustomer(uri: Uri?) {
        if (uri == null) {
            prefs().edit().remove(KEY_CUSTOMER).apply()
        } else {
            prefs().edit().putString(KEY_CUSTOMER, uri.toString()).apply()
        }
    }
}
