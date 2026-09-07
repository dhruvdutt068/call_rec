package com.example.callog.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "leads",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["personId"], unique = true),
        Index(value = ["status"]),
        Index(value = ["priority"]),
        Index(value = ["nextFollowUpAt"]),
        Index(value = ["updatedAt"])
    ]
)
data class LeadEntity(
    @PrimaryKey val id: String,
    val personId: String,
    val status: String = "UNKNOWN",
    val priority: String = "MEDIUM",
    val feedback: String? = null,
    val feedbackRating: Int? = null,
    val notes: String? = null,
    val ownerId: String? = null,
    val source: String = "MANUAL",
    val nextFollowUpAt: Long? = null,
    val isArchived: Boolean = false,
    val archivedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING"
)
