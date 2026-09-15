package com.example.callog.data.telecom

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person as CompatPerson
import com.example.callog.CallVaultApp
import com.example.callog.R
import com.example.callog.domain.call.CallSessionState
import com.example.callog.domain.model.LeadPriority
import com.example.callog.domain.model.LeadStatus
import com.example.callog.presentation.call.InCallActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class CallNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        const val NOTIFICATION_ID_CALL = 2001
        const val NOTIFICATION_ID_MISSED_BASE = 3000
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"

        const val ACTION_ANSWER = "com.example.callog.ACTION_ANSWER_CALL"
        const val ACTION_REJECT = "com.example.callog.ACTION_REJECT_CALL"
        const val ACTION_DISCONNECT = "com.example.callog.ACTION_DISCONNECT_CALL"
        const val ACTION_TOGGLE_MUTE = "com.example.callog.ACTION_TOGGLE_MUTE"
        const val ACTION_TOGGLE_SPEAKER = "com.example.callog.ACTION_TOGGLE_SPEAKER"
        const val ACTION_CALL_BACK = "com.example.callog.ACTION_CALL_BACK"
    }

    private val notificationManager by lazy {
        try {
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Posts or updates the incoming call heads-up notification with Full Screen Intent and CallStyle.
     */
    open fun showIncomingCallNotification(session: CallSessionState) {
        val fullScreenIntent = Intent(context, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("call_id", session.callId)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            100,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerIntent = Intent(context, CallNotificationActionReceiver::class.java).apply {
            action = ACTION_ANSWER
            putExtra(EXTRA_CALL_ID, session.callId)
        }
        val answerPendingIntent = PendingIntent.getBroadcast(
            context,
            101,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rejectIntent = Intent(context, CallNotificationActionReceiver::class.java).apply {
            action = ACTION_REJECT
            putExtra(EXTRA_CALL_ID, session.callId)
        }
        val rejectPendingIntent = PendingIntent.getBroadcast(
            context,
            102,
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callerName = session.callerDisplayName.ifBlank { session.phoneNumber }
        val callerPerson = CompatPerson.Builder()
            .setName(callerName)
            .setUri(if (session.phoneNumber.isNotBlank()) "tel:${session.phoneNumber}" else null)
            .setImportant(session.crmStatus == LeadStatus.HOT || session.priority == LeadPriority.URGENT)
            .build()

        val crmBadge = buildCrmSubtitle(session)
        val simText = session.simInfo?.let { " • SIM ${it.slotIndex + 1}" } ?: ""

        val builder = NotificationCompat.Builder(context, CallVaultApp.CALLS_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(callerName)
            .setContentText(if (crmBadge.isNotBlank()) "$crmBadge$simText" else session.phoneNumber)
            .setSubText(if (crmBadge.isNotBlank()) session.phoneNumber else null)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callStyle = NotificationCompat.CallStyle.forIncomingCall(
                callerPerson,
                rejectPendingIntent,
                answerPendingIntent
            )
            builder.setStyle(callStyle)
        } else {
            builder.addAction(
                android.R.drawable.ic_menu_call,
                "Answer",
                answerPendingIntent
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Decline",
                rejectPendingIntent
            )
        }

        notificationManager?.notify(NOTIFICATION_ID_CALL, builder.build())
    }

    /**
     * Posts or updates the active ongoing call notification with live chronometer and in-line controls.
     */
    open fun showActiveCallNotification(session: CallSessionState) {
        val inCallIntent = Intent(context, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("call_id", session.callId)
        }
        val inCallPendingIntent = PendingIntent.getActivity(
            context,
            200,
            inCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(context, CallNotificationActionReceiver::class.java).apply {
            action = ACTION_DISCONNECT
            putExtra(EXTRA_CALL_ID, session.callId)
        }
        val disconnectPendingIntent = PendingIntent.getBroadcast(
            context,
            201,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleMuteIntent = Intent(context, CallNotificationActionReceiver::class.java).apply {
            action = ACTION_TOGGLE_MUTE
            putExtra(EXTRA_CALL_ID, session.callId)
        }
        val toggleMutePendingIntent = PendingIntent.getBroadcast(
            context,
            202,
            toggleMuteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleSpeakerIntent = Intent(context, CallNotificationActionReceiver::class.java).apply {
            action = ACTION_TOGGLE_SPEAKER
            putExtra(EXTRA_CALL_ID, session.callId)
        }
        val toggleSpeakerPendingIntent = PendingIntent.getBroadcast(
            context,
            203,
            toggleSpeakerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callerName = session.callerDisplayName.ifBlank { session.phoneNumber }
        val callerPerson = CompatPerson.Builder()
            .setName(callerName)
            .setUri(if (session.phoneNumber.isNotBlank()) "tel:${session.phoneNumber}" else null)
            .build()

        val crmBadge = buildCrmSubtitle(session)
        val startTime = if (session.connectTimeMillis > 0) session.connectTimeMillis else System.currentTimeMillis()

        val builder = NotificationCompat.Builder(context, CallVaultApp.CALLS_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(callerName)
            .setContentText(if (crmBadge.isNotBlank()) "$crmBadge • ${session.phoneNumber}" else session.phoneNumber)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(inCallPendingIntent)
            .setUsesChronometer(true)
            .setWhen(startTime)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callStyle = NotificationCompat.CallStyle.forOngoingCall(
                callerPerson,
                disconnectPendingIntent
            )
            builder.setStyle(callStyle)

            // Add quick secondary actions
            val muteTitle = if (session.isMuted) "Unmute" else "Mute"
            builder.addAction(
                android.R.drawable.ic_lock_silent_mode,
                muteTitle,
                toggleMutePendingIntent
            )

            val speakerTitle = if (session.isSpeakerOn) "Earpiece" else "Speaker"
            builder.addAction(
                android.R.drawable.stat_sys_speakerphone,
                speakerTitle,
                toggleSpeakerPendingIntent
            )
        } else {
            val muteTitle = if (session.isMuted) "Unmute" else "Mute"
            builder.addAction(
                android.R.drawable.ic_lock_silent_mode,
                muteTitle,
                toggleMutePendingIntent
            )

            val speakerTitle = if (session.isSpeakerOn) "Earpiece" else "Speaker"
            builder.addAction(
                android.R.drawable.stat_sys_speakerphone,
                speakerTitle,
                toggleSpeakerPendingIntent
            )

            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "End Call",
                disconnectPendingIntent
            )
        }

        notificationManager?.notify(NOTIFICATION_ID_CALL, builder.build())
    }

    /**
     * Posts a missed call notification with one-tap Call Back and Send Message actions.
     */
    open fun showMissedCallNotification(session: CallSessionState) {
        val missedNotificationId = NOTIFICATION_ID_MISSED_BASE + (session.phoneNumber.hashCode() and 0x7FFF)

        // Call back action
        val callBackIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${session.phoneNumber}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val callBackPendingIntent = PendingIntent.getActivity(
            context,
            301,
            callBackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Send SMS action
        val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${session.phoneNumber}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val smsPendingIntent = PendingIntent.getActivity(
            context,
            302,
            smsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callerName = session.callerDisplayName.ifBlank { session.phoneNumber }
        val crmBadge = buildCrmSubtitle(session)

        val builder = NotificationCompat.Builder(context, CallVaultApp.MISSED_CALLS_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Missed Call: $callerName")
            .setContentText(if (crmBadge.isNotBlank()) "$crmBadge • ${session.phoneNumber}" else session.phoneNumber)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_call,
                "Call Back",
                callBackPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_send,
                "Message",
                smsPendingIntent
            )

        notificationManager?.notify(missedNotificationId, builder.build())
    }

    /**
     * Cancels active/incoming call notification.
     */
    open fun cancelCallNotification() {
        notificationManager?.cancel(NOTIFICATION_ID_CALL)
    }

    private fun buildCrmSubtitle(session: CallSessionState): String {
        val parts = mutableListOf<String>()
        session.companyName?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        if (session.crmStatus != LeadStatus.UNKNOWN) {
            val emoji = when (session.crmStatus) {
                LeadStatus.HOT -> "🔥"
                LeadStatus.WARM -> "⚡"
                LeadStatus.COLD -> "❄️"
                else -> "📌"
            }
            parts.add("$emoji ${session.crmStatus.name}")
        }
        return parts.joinToString(" • ")
    }
}
