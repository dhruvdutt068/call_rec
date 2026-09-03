package com.example.callog.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "phone_numbers",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["personId"]),
        Index(value = ["normalizedNumber"])
    ]
)
data class PhoneNumberEntity(
    @PrimaryKey val id: String,
    val personId: String,
    val phoneNumber: String,
    val normalizedNumber: String,
    val phoneType: String = "MOBILE",
    val isPrimary: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING"
)
