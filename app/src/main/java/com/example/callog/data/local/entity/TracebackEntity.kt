package com.example.callog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracebacks")
data class TracebackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerPhone: String,
    val ownerName: String,
    val callerType: String,
    val phoneNumber: String,
    val callerName: String,
    val hasRecording: Boolean,
    val callLogId: Long,
    val recordingUrl: String,
    val timestamp: Long = System.currentTimeMillis()
)
