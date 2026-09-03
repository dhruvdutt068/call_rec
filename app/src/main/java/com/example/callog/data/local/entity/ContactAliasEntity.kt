package com.example.callog.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contact_aliases",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["deviceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["personId"]),
        Index(value = ["deviceId"]),
        Index(value = ["aliasName"]),
        Index(value = ["normalizedNumber"])
    ]
)
data class ContactAliasEntity(
    @PrimaryKey val id: String,
    val personId: String,
    val deviceId: String,
    val androidContactId: String,
    val aliasName: String,
    val phoneNumber: String,
    val normalizedNumber: String,
    val createdAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING"
)
