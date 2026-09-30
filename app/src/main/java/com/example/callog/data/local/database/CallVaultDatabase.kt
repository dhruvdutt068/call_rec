package com.example.callog.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.callog.data.local.dao.*
import com.example.callog.data.local.entity.*

@Database(
    entities = [
        CallEntity::class,
        ReminderEntity::class,
        TracebackEntity::class,
        SalesCallEntity::class,
        SyncLogEntity::class,
        RecordingLogEntity::class,
        RecordingEntity::class,
        DeviceEntity::class,
        PersonEntity::class,
        PhoneNumberEntity::class,
        ContactAliasEntity::class,
        LeadEntity::class,
        ConversationEntity::class,
        ConversationMessageEntity::class,
        PresetEntity::class,
        LeadMasterEntity::class,
        LeadStageEntity::class,
        LeadStatusEntity::class,
        LeadTaskEntity::class,
        LeadNoteEntity::class
    ],
    version = 25,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CallVaultDatabase : RoomDatabase() {
    abstract fun callDao(): CallDao
    abstract fun reminderDao(): ReminderDao
    abstract fun tracebackDao(): TracebackDao
    abstract fun salesCallDao(): SalesCallDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun recordingLogDao(): RecordingLogDao
    abstract fun recordingDao(): RecordingDao
    abstract fun personDao(): PersonDao
    abstract fun leadDao(): LeadDao
    abstract fun conversationDao(): ConversationDao
    abstract fun presetDao(): PresetDao
    abstract fun leadMasterDao(): LeadMasterDao
    abstract fun leadStageDao(): LeadStageDao
    abstract fun leadStatusDao(): LeadStatusDao
    abstract fun leadTaskDao(): LeadTaskDao
    abstract fun leadNoteDao(): LeadNoteDao
}
