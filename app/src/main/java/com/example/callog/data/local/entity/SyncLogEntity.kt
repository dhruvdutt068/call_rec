package com.example.callog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String,
    val timestamp: Long,
    val level: String,          // INFO | WARNING | ERROR | SUCCESS
    val event: String,          // SYNC_STARTED | SUPABASE_UPLOAD | etc.
    val status: String,         // STARTED | SUCCESS | FAILED
    val message: String,
    val exception: String? = null,
    val stacktrace: String? = null,
    val network: String,
    val durationMs: Long = 0,
    val appVersion: String,
    val deviceModel: String,
    val androidVersion: String
)
