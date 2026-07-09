package com.example.callog.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "calls",
    indices = [
        Index(value = ["number"]),
        Index(value = ["timestamp"])
    ]
)
data class CallEntity(
    @PrimaryKey val id: Long,
    val name: String?,
    val number: String,
    val duration: Int, // in seconds
    val timestamp: Long, // epoch millisecond
    val callType: String, // INCOMING, OUTGOING, MISSED, REJECTED
    val recordingPath: String?,
    val isFavorite: Boolean = false,
    val notes: String? = null,
    val tags: String? = null, // Comma-separated list (e.g. "Work,Family")
    val syncStatus: String = "PENDING",
    val recordingLocalPath: String? = null,
    val recordingCloudPath: String? = null,
    val recordingUploadStatus: String = "PENDING", // PENDING, UPLOADING, SUCCESS, FAILED
    val recordingUploadedAt: Long? = null,
    val recordingUrl: String? = null
)
