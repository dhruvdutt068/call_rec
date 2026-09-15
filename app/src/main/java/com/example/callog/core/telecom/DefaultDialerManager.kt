package com.example.callog.core.telecom

import android.content.Context
import android.content.Intent
import com.example.callog.sim.SimInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level manager coordinating default phone/dialer role status and outgoing call dispatching.
 */
@Singleton
class DefaultDialerManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val telecomDialerManager: TelecomDialerManager
) {

    fun isDefaultDialer(): Boolean {
        return TelecomRoleHelper.isDefaultDialer(context)
    }

    fun isRoleAvailable(): Boolean {
        return TelecomRoleHelper.isRoleAvailable(context)
    }

    fun createRequestDialerRoleIntent(): Intent? {
        return TelecomRoleHelper.createRequestDialerRoleIntent(context)
    }

    fun placeCall(phoneNumber: String, simInfo: SimInfo? = null): Result<Unit> {
        return telecomDialerManager.placeCall(phoneNumber, simInfo)
    }
}
