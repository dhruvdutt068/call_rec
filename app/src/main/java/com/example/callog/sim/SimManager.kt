package com.example.callog.sim

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CallLog
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SimManager — authoritative SIM card discovery and preference manager.
 *
 * Responsibilities:
 *  - Read all active SIM subscriptions via [SubscriptionManager].
 *  - Persist the user-selected Business SIM via SharedPreferences.
 *  - Detect SIM card changes between app launches and suspend sync when changed.
 *  - Resolve which subscription ID a given call log entry belongs to, using
 *    multiple fallback strategies for cross-OEM compatibility.
 */
@Singleton
class SimManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "SIM_INFO"
        private const val PREFS_NAME = "sim_filtering_prefs"
    }

    // ─────────────────────────────────────────────────────
    //  Permissions
    // ─────────────────────────────────────────────────────

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

    // ─────────────────────────────────────────────────────
    //  SIM Discovery
    // ─────────────────────────────────────────────────────

    /**
     * Returns all currently active SIM subscriptions on the device.
     * Logs each SIM's details to Logcat under tag [TAG] for easy debugging.
     */
    fun getInstalledSims(): List<SimInfo> {
        if (!hasPermission()) return emptyList()

        val subscriptionManager = context
            .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return emptyList()

        return try {
            val list = subscriptionManager.activeSubscriptionInfoList ?: return emptyList()
            list.map { info -> info.toSimInfo() }.also { sims ->
                sims.forEach { sim ->
                    Log.d(TAG, buildString {
                        appendLine("──── SIM Detected ────")
                        appendLine("  Subscription ID : ${sim.subscriptionId}")
                        appendLine("  Slot Index      : ${sim.slotIndex}")
                        appendLine("  Carrier         : ${sim.carrierName}")
                        appendLine("  Display Name    : ${sim.displayName}")
                        appendLine("  Phone Number    : ${sim.phoneNumber}")
                        appendLine("  Active          : ${sim.isActive}")
                    })
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException reading SIM list: ${e.message}")
            emptyList()
        }
    }

    // Kept for compatibility — delegates to getInstalledSims()
    fun getActiveSims(): List<SimInfo> = getInstalledSims()

    private fun SubscriptionInfo.toSimInfo() = SimInfo(
        subscriptionId = subscriptionId,
        slotIndex = simSlotIndex,
        carrierName = carrierName?.toString() ?: "Unknown",
        displayName = displayName?.toString() ?: "SIM ${simSlotIndex + 1}",
        phoneNumber = readPhoneNumber(this),
        isActive = true
    )

    /**
     * Reads the phone number for a SIM subscription using the best available API.
     *
     * - Android 13+ (API 33): uses [SubscriptionManager.getPhoneNumber] (most reliable).
     * - Older: falls back to [SubscriptionInfo.number] (often blank for Indian carriers).
     */
    private fun readPhoneNumber(info: SubscriptionInfo): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as? SubscriptionManager
            try {
                val number = sm?.getPhoneNumber(info.subscriptionId)
                if (!number.isNullOrBlank()) return number
            } catch (_: Exception) { /* fall through */ }
        }
        @Suppress("DEPRECATION")
        val raw = info.number
        return if (raw.isNullOrBlank()) "Not exposed by carrier" else raw
    }

    // ─────────────────────────────────────────────────────
    //  Subscription ID → Call Log Mapping
    // ─────────────────────────────────────────────────────

    /**
     * Resolves the subscription ID for a call log entry using the PHONE_ACCOUNT_ID and
     * component name recorded by Android's call log.
     *
     * Strategy (in order):
     * 1. API 30+: [TelephonyManager.getSubscriptionId] via [PhoneAccountHandle]
     * 2. Integer parse: some OEMs write the sub ID directly as a string
     * 3. Slot index or substring match against active subscriptions
     */
    fun getSubscriptionIdFromHandle(
        phoneAccountId: String?,
        phoneAccountComponentName: String?
    ): Int {
        if (phoneAccountId.isNullOrEmpty()) return SubscriptionManager.INVALID_SUBSCRIPTION_ID

        // Strategy 1: API 30+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val component = phoneAccountComponentName
                ?.let { ComponentName.unflattenFromString(it) }
            if (component != null) {
                try {
                    val handle = android.telecom.PhoneAccountHandle(component, phoneAccountId)
                    val subId = tm?.getSubscriptionId(handle)
                    if (subId != null && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        return subId
                    }
                } catch (_: Exception) { /* fall through */ }
            }
        }

        // Strategy 2: direct integer
        try {
            val parsed = phoneAccountId.toInt()
            if (getInstalledSims().any { it.subscriptionId == parsed }) return parsed
        } catch (_: NumberFormatException) { /* not an integer */ }

        // Strategy 3: slot index / substring
        for (sim in getInstalledSims()) {
            if (phoneAccountId == sim.slotIndex.toString() ||
                phoneAccountId.contains(sim.subscriptionId.toString())) {
                return sim.subscriptionId
            }
        }

        return SubscriptionManager.INVALID_SUBSCRIPTION_ID
    }

    // ─────────────────────────────────────────────────────
    //  SIM Change Detection
    // ─────────────────────────────────────────────────────

    /**
     * Called at every app startup. Computes a fingerprint of the active SIM set
     * and compares with the stored value. If they differ, sync is suspended until
     * the user reconfigures the Business SIM.
     */
    fun checkSimChanges() {
        if (!hasPermission()) return
        val sims = getInstalledSims()
        if (sims.isEmpty()) return

        val fingerprint = sims.sortedBy { it.subscriptionId }
            .joinToString(",") { "${it.subscriptionId}:${it.slotIndex}" }

        val prefs = prefs()
        val stored = prefs.getString("active_sim_fingerprint", "") ?: ""

        when {
            stored.isEmpty() -> {
                // First launch — store fingerprint, no suspension
                prefs.edit().putString("active_sim_fingerprint", fingerprint).apply()
            }
            stored != fingerprint -> {
                prefs.edit().putBoolean("sync_suspended_due_to_sim_change", true).apply()
                Log.w(TAG, "SIM_CHANGE_DETECTED — old: $stored | new: $fingerprint")
            }
        }
    }

    fun isSyncSuspendedDueToSimChange(): Boolean =
        prefs().getBoolean("sync_suspended_due_to_sim_change", false)

    fun clearSimChangeSuspension() {
        prefs().edit().putBoolean("sync_suspended_due_to_sim_change", false).apply()
    }

    // ─────────────────────────────────────────────────────
    //  Phone Account Mapping Support Check
    // ─────────────────────────────────────────────────────

    fun checkPhoneAccountMappingSupport(): Boolean {
        if (!hasPermission()) return false
        return try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.PHONE_ACCOUNT_ID),
                "${CallLog.Calls.PHONE_ACCOUNT_ID} IS NOT NULL AND ${CallLog.Calls.PHONE_ACCOUNT_ID} != ''",
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 15"
            )
            cursor?.use {
                val supported = it.count > 0
                setMappingSupported(supported)
                supported
            } ?: false
        } catch (_: Exception) {
            setMappingSupported(false)
            false
        }
    }

    fun isMappingSupported(): Boolean =
        prefs().getBoolean("supports_phone_account_mapping", false)

    private fun setMappingSupported(supported: Boolean) {
        prefs().edit().putBoolean("supports_phone_account_mapping", supported).apply()
    }

    // ─────────────────────────────────────────────────────
    //  Selected Business SIM — Getters & Setters
    // ─────────────────────────────────────────────────────

    fun getSelectedSubscriptionId(): Int =
        prefs().getInt("selected_subscription_id", SubscriptionManager.INVALID_SUBSCRIPTION_ID)

    fun getSelectedSlotIndex(): Int = prefs().getInt("selected_slot", -1)
    fun getSelectedCarrierName(): String = prefs().getString("selected_carrier", "") ?: ""
    fun getSelectedDisplayName(): String = prefs().getString("selected_display_name", "") ?: ""
    fun getSelectedPhoneNumber(): String = prefs().getString("selected_phone_number", "") ?: ""
    fun getDetectionMethod(): String = prefs().getString("detection_method", "MANUAL") ?: "MANUAL"

    fun setDetectionMethod(method: String) {
        prefs().edit().putString("detection_method", method).apply()
    }

    fun saveSelectedSim(sim: SimInfo) {
        val sims = getInstalledSims()
        val fingerprint = sims.sortedBy { it.subscriptionId }
            .joinToString(",") { "${it.subscriptionId}:${it.slotIndex}" }

        prefs().edit()
            .putInt("selected_subscription_id", sim.subscriptionId)
            .putInt("selected_slot", sim.slotIndex)
            .putString("selected_carrier", sim.carrierName)
            .putString("selected_display_name", sim.displayName)
            .putString("selected_phone_number", sim.phoneNumber)
            .putBoolean("sync_suspended_due_to_sim_change", false)
            .putString("active_sim_fingerprint", fingerprint)
            .apply()

        Log.i(TAG, buildString {
            appendLine("Business SIM Saved:")
            appendLine("  Carrier: ${sim.carrierName}")
            appendLine("  Subscription ID: ${sim.subscriptionId}")
            appendLine("  Slot: ${sim.slotIndex}")
        })
    }

    fun saveSyncAll() {
        prefs().edit()
            .putInt("selected_subscription_id", -999) // sentinel for "sync all"
            .putString("selected_carrier", "ALL")
            .putString("selected_display_name", "All SIMs")
            .putBoolean("sync_suspended_due_to_sim_change", false)
            .apply()
    }

    fun resetSimConfiguration() {
        prefs().edit()
            .putInt("selected_subscription_id", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            .putInt("selected_slot", -1)
            .putString("selected_carrier", "")
            .putString("selected_display_name", "")
            .putString("selected_phone_number", "")
            .putBoolean("sync_suspended_due_to_sim_change", false)
            .putString("active_sim_fingerprint", "")
            .apply()
    }

    // ─────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
