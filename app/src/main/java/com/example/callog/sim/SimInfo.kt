package com.example.callog.sim

/**
 * Represents a physical or virtual SIM card installed in the device.
 *
 * @param subscriptionId  Android SubscriptionManager subscription ID — unique per SIM slot.
 * @param slotIndex       Physical SIM slot (0 = SIM 1, 1 = SIM 2).
 * @param carrierName     Network operator name (e.g. "Airtel", "Jio").
 * @param displayName     User-visible SIM label (from system settings).
 * @param phoneNumber     MSISDN if exposed by the carrier. Null / "Not exposed by carrier" otherwise.
 * @param isActive        Whether this SIM slot currently has an active subscription.
 */
data class SimInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val carrierName: String,
    val displayName: String,
    val phoneNumber: String,
    val isActive: Boolean = true
)