package com.example.callog.sim

import android.telephony.SubscriptionManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SimRepository — business-logic layer between the ViewModel and [SimManager].
 *
 * Keeps Android-specific [SimManager] calls out of the ViewModel, making the
 * feature independently testable and easy to extend (e.g., eSIM support).
 */
@Singleton
class SimRepository @Inject constructor(
    private val simManager: SimManager
) {

    // ─────────────────────────────────────────────────────
    //  SIM Discovery
    // ─────────────────────────────────────────────────────

    /** Returns all active SIM cards installed in the device. */
    fun getInstalledSims(): List<SimInfo> = simManager.getInstalledSims()

    /** Returns true if the device has at least one active SIM and READ_PHONE_STATE is granted. */
    fun hasActiveSims(): Boolean = simManager.getInstalledSims().isNotEmpty()

    // ─────────────────────────────────────────────────────
    //  Business SIM Preference
    // ─────────────────────────────────────────────────────

    /** Persists the selected Business SIM and clears any sync suspension flag. */
    fun saveBusinessSim(sim: SimInfo) = simManager.saveSelectedSim(sim)

    /** Configures the app to sync all SIMs (no filtering). */
    fun saveSyncAll() = simManager.saveSyncAll()

    /** Returns the currently saved Business SIM subscription ID, or [SubscriptionManager.INVALID_SUBSCRIPTION_ID]. */
    fun getSelectedSubscriptionId(): Int = simManager.getSelectedSubscriptionId()

    /** Returns a [SelectedSimSummary] representing the currently configured Business SIM. */
    fun getSelectedSimSummary(): SelectedSimSummary = SelectedSimSummary(
        subscriptionId = simManager.getSelectedSubscriptionId(),
        slotIndex = simManager.getSelectedSlotIndex(),
        carrierName = simManager.getSelectedCarrierName(),
        displayName = simManager.getSelectedDisplayName(),
        phoneNumber = simManager.getSelectedPhoneNumber()
    )

    /** Clears the Business SIM configuration entirely (resets to unconfigured state). */
    fun resetSimConfiguration() = simManager.resetSimConfiguration()

    // ─────────────────────────────────────────────────────
    //  SIM Change Detection
    // ─────────────────────────────────────────────────────

    /** Checks for SIM changes since last launch. Call on every app startup. */
    fun checkSimChanges() = simManager.checkSimChanges()

    /** Returns true if sync is suspended because installed SIMs changed since last launch. */
    fun isSyncSuspended(): Boolean = simManager.isSyncSuspendedDueToSimChange()

    /** Clears the sync suspension — called after user re-selects the Business SIM. */
    fun clearSyncSuspension() = simManager.clearSimChangeSuspension()

    // ─────────────────────────────────────────────────────
    //  Phone Account Mapping (per-call SIM detection)
    // ─────────────────────────────────────────────────────

    /**
     * Checks whether this device's call log exposes PHONE_ACCOUNT_ID, enabling
     * per-call SIM identification. Returns false on legacy devices/OEMs that omit it.
     */
    fun checkMappingSupport(): Boolean = simManager.checkPhoneAccountMappingSupport()

    /** Returns the subscription ID for a call log entry using all available fallback strategies. */
    fun resolveSubscriptionId(phoneAccountId: String?, componentName: String?): Int =
        simManager.getSubscriptionIdFromHandle(phoneAccountId, componentName)
}

/**
 * Lightweight summary of the currently saved Business SIM selection.
 * Used by the ViewModel and Settings UI to display current state.
 */
data class SelectedSimSummary(
    val subscriptionId: Int,
    val slotIndex: Int,
    val carrierName: String,
    val displayName: String,
    val phoneNumber: String
) {
    val isConfigured: Boolean
        get() = subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID

    val label: String
        get() = if (displayName.isNotEmpty()) displayName else carrierName

    override fun toString(): String =
        "$label (SIM ${slotIndex + 1}) · $phoneNumber"
}
