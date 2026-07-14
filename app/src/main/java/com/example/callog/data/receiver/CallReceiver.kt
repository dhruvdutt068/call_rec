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
import com.example.callog.data.worker.SyncWorker
import java.util.concurrent.TimeUnit

class CallReceiver : BroadcastReceiver() {
    private val TAG = "CallReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            Log.d(TAG, "Phone state changed: $state")
            if (state == TelephonyManager.EXTRA_STATE_IDLE) {
                Log.d(TAG, "Call ended (IDLE state). Triggering background sync with 5 seconds delay.")
                triggerBackgroundSync(context)
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
