package com.example.callog.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "devices",
    indices = [
        Index(value = ["deviceIdentifier"], unique = true)
    ]
)
data class DeviceEntity(
    @PrimaryKey val id: String,
    val deviceName: String,
    val devicePhone: String,
    val deviceIdentifier: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastSyncAt: Long? = null
)
