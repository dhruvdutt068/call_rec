package com.example.callog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recording_logs")
data class RecordingLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val scanId: String,
    val fileName: String,
    val path: String,
    val parser: String,
    val phoneExtracted: String?,
    val timestampExtracted: Long?,
    val candidateCount: Int,
    val matchedCallId: Long?,
    val status: String, // MATCHED, UNMATCHED, PARSER_FAILED, SAVE_FAILED
    val reason: String?,
    val createdAt: Long = System.currentTimeMillis()
)
