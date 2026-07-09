package com.example.callog.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.callog.data.local.dao.CallDao
import com.example.callog.data.local.dao.ReminderDao
import com.example.callog.data.local.entity.CallEntity
import com.example.callog.data.local.entity.ReminderEntity

@Database(
    entities = [
        CallEntity::class,
        ReminderEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class CallVaultDatabase : RoomDatabase() {
    abstract fun callDao(): CallDao
    abstract fun reminderDao(): ReminderDao
}
