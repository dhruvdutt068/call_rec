package com.example.callog.data.telecom

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.callog.CallVaultApp
import com.example.callog.R
import com.example.callog.domain.call.CallDirection
import com.example.callog.domain.call.CallSessionState
import com.example.callog.domain.call.CallState
import com.example.callog.presentation.call.InCallActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class CallNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val NOTIFICATION_ID = 2001
        const val EXTRA_CALL_ID = "extra_call_id"

        const val ACTION_ANSWER = "com.example.callog.ACTION_ANSWER_CALL"
        const val ACTION_REJECT = "com.example.callog.ACTION_REJECT_CALL"
        const val ACTION_DISCONNECT = "com.example.callog.ACTION_DISCONNECT_CALL"
    }

    private val notificationManager by lazy {
        try {
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Posts or updates the incoming call heads-up notification with Full Screen Intent.
     */
    open fun showIncomingCallNotification(session: CallSessionState) {
        val fullScreenIntent = Intent(context, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerIntent = Intent(ACTION_ANSWER).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_CALL_ID, session.callId)
        }
        val answerPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rejectIntent = Intent(ACTION_REJECT).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_CALL_ID, session.callId)
        }
        val rejectPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = session.callerDisplayName.ifBlank { session.phoneNumber }
        val leadBadge = if (session.crmStatus != com.example.callog.domain.model.LeadStatus.UNKNOWN) {
            " • ${session.crmStatus.name} (${session.priority.name})"
        } else ""

        val simText = session.simInfo?.let { " [SIM ${it.slotIndex + 1}]" } ?: ""

        val builder = NotificationCompat.Builder(context, CallVaultApp.CALLS_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Incoming Call$simText: $title")
            .setContentText("${session.phoneNumber}$leadBadge")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(
                android.R.drawable.ic_menu_call,
                "Answer",
                answerPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Decline",
                rejectPendingIntent
            )

        notificationManager?.notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * Posts or updates the active call ongoing notification.
     */
    open fun showActiveCallNotification(session: CallSessionState) {
        val inCallIntent = Intent(context, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val inCallPendingIntent = PendingIntent.getActivity(
            context,
            0,
            inCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(ACTION_DISCONNECT).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_CALL_ID, session.callId)
        }
        val disconnectPendingIntent = PendingIntent.getBroadcast(
            context,
            3,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = session.callerDisplayName.ifBlank { session.phoneNumber }
        val min = session.durationSeconds / 60
        val sec = session.durationSeconds % 60
        val durationFormatted = "%02d:%02d".format(min, sec)

        val builder = NotificationCompat.Builder(context, CallVaultApp.CALLS_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Call with $title ($durationFormatted)")
            .setContentText(session.phoneNumber)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(inCallPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "End Call",
                disconnectPendingIntent
            )

        notificationManager?.notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * Cancels the call notification when call finishes.
     */
    open fun cancelCallNotification() {
        notificationManager?.cancel(NOTIFICATION_ID)
    }
}
