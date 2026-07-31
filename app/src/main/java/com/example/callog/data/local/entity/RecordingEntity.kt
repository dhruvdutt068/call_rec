package com.example.callog.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recordings_research",
    indices = [
        Index(value = ["filePath"], unique = true),
        Index(value = ["matchedCallId"])
    ]
)
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val duration: Long, // in seconds
    val lastModified: Long,
    val phoneExtracted: String?,
    val contactExtracted: String? = null,
    val timestampExtracted: Long?,
    val matchedCallId: Long?,
    val matchStatus: MatchStatus,
    val uploadStatus: UploadStatus,
    val cloudUrl: String? = null,
    val parser: String? = null,
    val reason: String? = null
)
