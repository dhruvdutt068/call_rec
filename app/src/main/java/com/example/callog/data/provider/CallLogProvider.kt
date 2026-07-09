package com.example.callog.data.provider

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.example.callog.data.local.entity.CallEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallLogProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun fetchCallLogs(): List<CallEntity> {
        val callLogsList = mutableListOf<CallEntity>()
        
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALL_LOG
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE
        )

        // Query the content provider
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )

        cursor?.use { c ->
            val idIdx = c.getColumnIndex(CallLog.Calls._ID)
            val numberIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIdx = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val dateIdx = c.getColumnIndex(CallLog.Calls.DATE)
            val durationIdx = c.getColumnIndex(CallLog.Calls.DURATION)
            val typeIdx = c.getColumnIndex(CallLog.Calls.TYPE)

            var count = 0
            while (c.moveToNext() && count < 500) {
                val id = if (idIdx != -1) c.getLong(idIdx) else 0L
                val number = if (numberIdx != -1) c.getString(numberIdx) ?: "" else ""
                val cachedName = if (nameIdx != -1) c.getString(nameIdx) else null
                val timestamp = if (dateIdx != -1) c.getLong(dateIdx) else 0L
                val duration = if (durationIdx != -1) c.getInt(durationIdx) else 0
                val systemType = if (typeIdx != -1) c.getInt(typeIdx) else -1
                
                val callType = when (systemType) {
                    CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                    CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                    CallLog.Calls.MISSED_TYPE -> "MISSED"
                    CallLog.Calls.REJECTED_TYPE -> "REJECTED"
                    CallLog.Calls.BLOCKED_TYPE -> "BLOCKED"
                    else -> "UNKNOWN"
                }

                // Check if number is valid
                if (number.isNotEmpty()) {
                    callLogsList.add(
                        CallEntity(
                            id = id,
                            name = cachedName,
                            number = number,
                            duration = duration,
                            timestamp = timestamp,
                            callType = callType,
                            recordingPath = null // Resolved later
                        )
                    )
                    count++
                }
            }
        }
        
        return callLogsList
    }
}
