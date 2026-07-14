package com.example.callog.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.callog.data.local.dao.CallDao
import com.example.callog.data.local.dao.ReminderDao
import com.example.callog.data.local.dao.SalesCallDao
import com.example.callog.data.local.dao.TracebackDao
import com.example.callog.data.local.dao.SyncLogDao
import com.example.callog.data.local.entity.CallEntity
import com.example.callog.data.local.entity.ReminderEntity
import com.example.callog.data.local.entity.SalesCallEntity
import com.example.callog.data.local.entity.TracebackEntity
import com.example.callog.data.local.entity.SyncLogEntity

@Database(
    entities = [
        CallEntity::class,
        ReminderEntity::class,
        TracebackEntity::class,
        SalesCallEntity::class,
        SyncLogEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class CallVaultDatabase : RoomDatabase() {
    abstract fun callDao(): CallDao
    abstract fun reminderDao(): ReminderDao
    abstract fun tracebackDao(): TracebackDao
    abstract fun salesCallDao(): SalesCallDao
    abstract fun syncLogDao(): SyncLogDao
}

