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
            val manager = getSystemService(NotificationManager::class.java)

            val reminderChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Callback Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Used for callback reminders scheduled for call history logs."
            }

            val callsChannel = NotificationChannel(
                CALLS_NOTIFICATION_CHANNEL_ID,
                "Incoming & Active Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Displays live in-call controls, caller ID, and incoming call heads-up notifications."
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val missedCallsChannel = NotificationChannel(
                MISSED_CALLS_NOTIFICATION_CHANNEL_ID,
                "Missed Calls",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies of missed incoming calls with quick call-back actions."
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            manager.createNotificationChannel(reminderChannel)
            manager.createNotificationChannel(callsChannel)
            manager.createNotificationChannel(missedCallsChannel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "call_vault_reminders"
        const val CALLS_NOTIFICATION_CHANNEL_ID = "callog_incoming_calls"
        const val MISSED_CALLS_NOTIFICATION_CHANNEL_ID = "callog_missed_calls"
    }
}
