package com.example.callog.core.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.callog.data.local.dao.SyncLogDao
import com.example.callog.data.local.entity.SyncLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

object DeveloperLogger {
    var isDeveloperModeEnabled: Boolean = false
    private var syncLogDao: SyncLogDao? = null
    private var appVersion: String = "1.0.0"
    private var currentSyncId: String = ""

    fun init(dao: SyncLogDao, context: Context) {
        this.syncLogDao = dao
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            appVersion = packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            // ignore
        }
    }

    fun startNewSession(): String {
        currentSyncId = UUID.randomUUID().toString()
        return currentSyncId
    }

    fun getSyncId(): String {
        if (currentSyncId.isEmpty()) {
            currentSyncId = UUID.randomUUID().toString()
        }
        return currentSyncId
    }

    private fun log(
        level: String,
        event: String,
        status: String,
        message: String,
        exception: String? = null,
        stacktrace: String? = null,
        network: String = "UNKNOWN",
        durationMs: Long = 0
    ) {
        // Log to Logcat
        val tag = "DevLogger"
        val logContent = "[$event] $status - $message"
        if (level == "ERROR") {
            Log.e(tag, logContent)
        } else {
            Log.d(tag, logContent)
        }

        val dao = syncLogDao ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                dao.insertLog(
                    SyncLogEntity(
                        syncId = getSyncId(),
                        timestamp = System.currentTimeMillis(),
                        level = level,
                        event = event,
                        status = status,
                        message = message,
                        exception = exception,
                        stacktrace = stacktrace,
                        network = network,
                        durationMs = durationMs,
                        appVersion = appVersion,
                        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                        androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                    )
                )
            } catch (e: Exception) {
                Log.e("DevLogger", "Failed to write log to local database", e)
            }
        }
    }

    fun info(event: String, message: String, network: String = "UNKNOWN") {
        log("INFO", event, "STARTED", message, network = network)
    }

    fun success(event: String, message: String, durationMs: Long = 0, network: String = "UNKNOWN") {
        log("SUCCESS", event, "SUCCESS", message, durationMs = durationMs, network = network)
    }

    fun warning(event: String, message: String, network: String = "UNKNOWN") {
        log("WARNING", event, "SUCCESS", message, network = network)
    }

    fun error(event: String, message: String, exception: Throwable? = null, network: String = "UNKNOWN") {
        val excMsg = exception?.message ?: exception?.toString()
        val trace = exception?.stackTraceToString()
        log("ERROR", event, "FAILED", message, exception = excMsg, stacktrace = trace, network = network)
    }
}
