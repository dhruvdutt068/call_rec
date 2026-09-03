package com.example.callog.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "people",
    indices = [
        Index(value = ["displayName"])
    ]
)
data class PersonEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val companyName: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING"
)
