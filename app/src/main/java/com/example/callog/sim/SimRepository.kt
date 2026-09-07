package com.example.callog.sim

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class SimRepository @Inject constructor(
    private val simManager: SimManager
) {
    open fun getInstalledSims(): List<SimInfo> {
        return simManager.getInstalledSims()
    }

    open fun resolveSubscriptionId(phoneAccountId: String?, componentName: String?): Int {
        return simManager.getSubscriptionIdFromHandle(phoneAccountId, componentName)
    }

    open fun getSimBySubscriptionId(subId: Int): SimInfo? {
        return simManager.getInstalledSims().find { it.subscriptionId == subId }
    }

    open fun getSimBySlotIndex(slotIndex: Int): SimInfo? {
        return simManager.getInstalledSims().find { it.slotIndex == slotIndex }
    }
}
