package com.example.callog.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.data.worker.SyncWorker
import com.example.callog.domain.call.CallDirection
import com.example.callog.domain.call.CallState
import com.example.callog.domain.service.CallSessionManager
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Secondary BroadcastReceiver for Telephony phone state changes.
 *
 * Dedicated strictly to:
 * 1. Post-call detection & triggering background sync / recording scan on IDLE.
 * 2. Secondary/legacy device fallback signals without overriding active InCallService live sessions.
 */
@AndroidEntryPoint
class CallReceiver : BroadcastReceiver() {
    private val TAG = "CallReceiver"

    @Inject
    lateinit var callSessionManager: CallSessionManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
            Log.d(TAG, "Phone state broadcast received: $state, number: $incomingNumber")
            DeveloperLogger.info("CALL_RECEIVER_SIGNAL", "Telephony broadcast: state=$state")

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    callSessionManager.onTelephonyCallStateChanged(
                        state = CallState.RINGING,
                        number = incomingNumber,
                        direction = CallDirection.INCOMING
                    )
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    callSessionManager.onTelephonyCallStateChanged(
                        state = CallState.ACTIVE,
                        number = incomingNumber,
                        direction = CallDirection.UNKNOWN
                    )
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    callSessionManager.onTelephonyCallStateChanged(
                        state = CallState.DISCONNECTED,
                        number = incomingNumber,
                        direction = CallDirection.UNKNOWN
                    )
                    Log.d(TAG, "Call ended (IDLE state). Triggering background sync with 5 seconds delay.")
                    DeveloperLogger.info("CALL_RECEIVER_POST_CALL", "Post-call IDLE detected: Enqueuing SyncWorker.")
                    triggerBackgroundSync(context)
                }
            }
        }
    }

    private fun triggerBackgroundSync(context: Context) {
        try {
            val workManager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build()

            // Enqueue work to run immediately in background (after the delay)
            workManager.enqueueUniqueWork(
                "one_time_sync_work_receiver",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue SyncWorker", e)
        }
    }
}
