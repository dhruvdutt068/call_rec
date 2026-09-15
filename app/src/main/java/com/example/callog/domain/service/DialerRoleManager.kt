package com.example.callog.domain.service

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import androidx.activity.result.contract.ActivityResultContract
import com.example.callog.core.diagnostics.DeveloperLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service managing Android Default Dialer Role status, queries, and system requests.
 * Supports Android 10+ (API 29+) RoleManager and legacy TelecomManager fallbacks.
 */
@Singleton
class DialerRoleManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        const val ROLE_DIALER_REQUEST_CODE = 4001
    }

    private val _isDefaultDialer = MutableStateFlow(isRoleHeld())
    val isDefaultDialer: StateFlow<Boolean> = _isDefaultDialer.asStateFlow()

    /**
     * Re-queries the OS to check if Callog is currently the default phone application.
     */
    fun refreshRoleStatus(): Boolean {
        val held = isRoleHeld()
        _isDefaultDialer.value = held
        DeveloperLogger.info(
            "DIALER_ROLE_STATUS",
            "Checked default dialer status: isHeld=$held (SDK=${Build.VERSION.SDK_INT})"
        )
        return held
    }

    /**
     * Direct query against RoleManager / TelecomManager.
     */
    fun isRoleHeld(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            }
        }

        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        return telecomManager?.defaultDialerPackage == context.packageName
    }

    /**
     * Returns whether the device supports requesting the default dialer role.
     */
    fun isRoleAvailable(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            return roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true
        }
        return true
    }

    /**
     * Creates an Intent to prompt the user to make Callog the default dialer.
     */
    fun createRequestRoleIntent(): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            }
        }

        @Suppress("DEPRECATION")
        return Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
            putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
        }
    }

    /**
     * ActivityResultContract for Compose rememberLauncherForActivityResult.
     */
    fun createActivityResultContract(): ActivityResultContract<Unit, Boolean> {
        return object : ActivityResultContract<Unit, Boolean>() {
            override fun createIntent(context: Context, input: Unit): Intent {
                return createRequestRoleIntent() ?: Intent()
            }

            override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
                val success = resultCode == Activity.RESULT_OK || isRoleHeld()
                _isDefaultDialer.value = success
                return success
            }
        }
    }
}
