package com.example.callog.core.telecom

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.sim.SimInfo
import com.example.callog.sim.SimRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data structure linking a physical SimInfo with Android Telecom's PhoneAccountHandle.
 */
data class SimAccountMapping(
    val simInfo: SimInfo,
    val phoneAccountHandle: PhoneAccountHandle?
)

/**
 * Manages outgoing call dispatching via Android TelecomManager,
 * ensuring accurate Dual-SIM PhoneAccountHandle routing.
 */
@Singleton
open class TelecomDialerManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val simRepository: SimRepository
) {
    private val telecomManager by lazy {
        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    }

    private val telephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    }

    /**
     * Checks whether Callog holds the CALL_PHONE runtime permission.
     */
    open fun hasCallPhonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Resolves all available SIM cards paired with their respective PhoneAccountHandles.
     */
    open fun getSimAccountMappings(): List<SimAccountMapping> {
        val installedSims = simRepository.getInstalledSims()
        val accounts = getCallCapableAccounts()

        return installedSims.map { sim ->
            val matchingHandle = findMatchingPhoneAccount(sim, accounts)
            SimAccountMapping(sim, matchingHandle)
        }
    }

    /**
     * Places an outgoing call to the specified phone number using the chosen SIM.
     *
     * @param phoneNumber Destination phone number (e.g. "+91 98765 43210")
     * @param simInfo Target SIM card or null for default system routing
     * @return Result indicating success or error
     */
    open fun placeCall(phoneNumber: String, simInfo: SimInfo?): Result<Unit> {
        val cleanNumber = phoneNumber.trim()
        if (cleanNumber.isBlank()) {
            return Result.failure(IllegalArgumentException("Phone number cannot be blank"))
        }

        val uri = Uri.fromParts("tel", cleanNumber, null)
        val handle = simInfo?.let { resolvePhoneAccountForSim(it) }

        val extras = Bundle().apply {
            if (handle != null) {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            }
        }

        return try {
            if (hasCallPhonePermission() && telecomManager != null) {
                DeveloperLogger.info(
                    "TELECOM_PLACE_CALL",
                    "Placing direct call to $cleanNumber on SIM ${simInfo?.displayName ?: "DEFAULT"} (Handle=${handle?.id})"
                )
                telecomManager?.placeCall(uri, extras)
                Result.success(Unit)
            } else {
                // Fallback to system dialer if permission missing
                DeveloperLogger.warning(
                    "TELECOM_FALLBACK_DIAL",
                    "CALL_PHONE permission missing or TelecomManager null. Launching ACTION_DIAL intent."
                )
                val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    if (handle != null) {
                        putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                    }
                }
                context.startActivity(dialIntent)
                Result.success(Unit)
            }
        } catch (e: SecurityException) {
            DeveloperLogger.error("TELECOM_PLACE_CALL", "SecurityException placing call: ${e.message}")
            // Attempt ACTION_DIAL fallback
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
                Result.success(Unit)
            } catch (fallbackError: Exception) {
                Result.failure(fallbackError)
            }
        } catch (e: Exception) {
            DeveloperLogger.error("TELECOM_PLACE_CALL", "Exception placing call: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Resolves the Telecom PhoneAccountHandle matching the given SimInfo.
     */
    open fun resolvePhoneAccountForSim(sim: SimInfo): PhoneAccountHandle? {
        val accounts = getCallCapableAccounts()
        return findMatchingPhoneAccount(sim, accounts)
    }

    private fun getCallCapableAccounts(): List<PhoneAccountHandle> {
        return try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                telecomManager?.callCapablePhoneAccounts ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            DeveloperLogger.warning("TELECOM_DIALER", "Failed to get callCapablePhoneAccounts: ${e.message}")
            emptyList()
        }
    }

    private fun findMatchingPhoneAccount(
        sim: SimInfo,
        accounts: List<PhoneAccountHandle>
    ): PhoneAccountHandle? {
        if (accounts.isEmpty()) return null

        // Strategy 1: API 30+ TelephonyManager getSubscriptionId match
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && telephonyManager != null) {
            for (handle in accounts) {
                try {
                    val subId = telephonyManager?.getSubscriptionId(handle)
                    if (subId == sim.subscriptionId && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        return handle
                    }
                } catch (_: Exception) {}
            }
        }

        // Strategy 2: Match by PhoneAccountHandle.id containing subscription ID or slot index
        for (handle in accounts) {
            val handleId = handle.id ?: continue
            if (handleId == sim.subscriptionId.toString() ||
                handleId == sim.slotIndex.toString() ||
                handleId.contains(sim.subscriptionId.toString())) {
                return handle
            }
        }

        // Strategy 3: Fallback by index if counts align
        return accounts.getOrNull(sim.slotIndex)
    }
}
