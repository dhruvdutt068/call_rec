package com.example.callog.data.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.callog.domain.call.CallAction
import com.example.callog.domain.service.CallSessionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CallNotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallNotificationReceiver"
    }

    @Inject
    lateinit var callSessionManager: CallSessionManager

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(CallNotificationManager.EXTRA_CALL_ID) ?: return
        val action = intent.action ?: return

        Log.i(TAG, "Received call notification action: $action for call $callId")

        when (action) {
            CallNotificationManager.ACTION_ANSWER -> {
                callSessionManager.executeAction(CallAction.Answer(callId))
            }
            CallNotificationManager.ACTION_REJECT -> {
                callSessionManager.executeAction(CallAction.Reject(callId))
            }
            CallNotificationManager.ACTION_DISCONNECT -> {
                callSessionManager.executeAction(CallAction.Disconnect(callId))
            }
            CallNotificationManager.ACTION_TOGGLE_MUTE -> {
                callSessionManager.executeAction(CallAction.ToggleMute(callId))
            }
            CallNotificationManager.ACTION_TOGGLE_SPEAKER -> {
                callSessionManager.executeAction(CallAction.ToggleSpeaker(callId))
            }
        }
    }
}
