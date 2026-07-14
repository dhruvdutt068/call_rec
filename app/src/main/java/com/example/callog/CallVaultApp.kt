package com.example.callog

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.data.local.dao.SyncLogDao
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CallVaultApp : Application() {

    @Inject
    lateinit var syncLogDao: SyncLogDao

    override fun onCreate() {
        super.onCreate()
        DeveloperLogger.init(syncLogDao, this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Callback Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Used for callback reminders scheduled for call history logs."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "call_vault_reminders"
    }
}
