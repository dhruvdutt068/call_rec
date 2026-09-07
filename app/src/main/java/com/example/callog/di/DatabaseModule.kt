package com.example.callog.di

import android.content.Context
import androidx.room.Room
import com.example.callog.core.constants.Constants
import com.example.callog.data.local.dao.CallDao
import com.example.callog.data.local.dao.ReminderDao
import com.example.callog.data.local.dao.SalesCallDao
import com.example.callog.data.local.dao.TracebackDao
import com.example.callog.data.local.dao.SyncLogDao
import com.example.callog.data.local.database.CallVaultDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE calls ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE calls ADD COLUMN uploadedAt INTEGER")
            db.execSQL("ALTER TABLE calls ADD COLUMN syncError TEXT")
            db.execSQL("ALTER TABLE calls ADD COLUMN lastAttempt INTEGER")
        }
    }

    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `recordings` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                    `filePath` TEXT NOT NULL, 
                    `fileName` TEXT NOT NULL, 
                    `fileSize` INTEGER NOT NULL, 
                    `duration` INTEGER NOT NULL, 
                    `lastModified` INTEGER NOT NULL, 
                    `phoneExtracted` TEXT, 
                    `timestampExtracted` INTEGER, 
                    `matchedCallId` INTEGER, 
                    `matchStatus` TEXT NOT NULL, 
                    `uploadStatus` TEXT NOT NULL, 
                    `cloudUrl` TEXT, 
                    `parser` TEXT, 
                    `reason` TEXT
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recordings_filePath` ON `recordings` (`filePath`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recordings_matchedCallId` ON `recordings` (`matchedCallId`)")
            
            db.execSQL("""
                INSERT OR IGNORE INTO recordings (filePath, fileName, fileSize, duration, lastModified, phoneExtracted, timestampExtracted, matchedCallId, matchStatus, uploadStatus, cloudUrl, parser, reason)
                SELECT 
                    recordingPath AS filePath,
                    recordingPath AS fileName, 
                    0 AS fileSize,
                    duration AS duration,
                    timestamp AS lastModified,
                    number AS phoneExtracted,
                    timestamp AS timestampExtracted,
                    id AS matchedCallId,
                    'MATCHED' AS matchStatus,
                    COALESCE(recordingUploadStatus, 'PENDING') AS uploadStatus,
                    recordingPath AS cloudUrl,
                    'Legacy' AS parser,
                    'Imported from call logs' AS reason
                FROM calls 
                WHERE recordingPath IS NOT NULL AND recordingPath != ''
            """.trimIndent())
        }
    }

    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Create calls_research table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `calls_research` (
                    `id` INTEGER PRIMARY KEY NOT NULL, 
                    `name` TEXT, 
                    `number` TEXT NOT NULL, 
                    `duration` INTEGER NOT NULL, 
                    `timestamp` INTEGER NOT NULL, 
                    `callType` TEXT NOT NULL, 
                    `recordingPath` TEXT, 
                    `phoneAccountId` TEXT, 
                    `phoneAccountComponentName` TEXT, 
                    `isFavorite` INTEGER NOT NULL, 
                    `notes` TEXT, 
                    `tags` TEXT, 
                    `syncStatus` TEXT NOT NULL, 
                    `recordingLocalPath` TEXT, 
                    `recordingCloudPath` TEXT, 
                    `recordingUploadStatus` TEXT NOT NULL, 
                    `recordingUploadedAt` INTEGER, 
                    `recordingUrl` TEXT, 
                    `retryCount` INTEGER NOT NULL, 
                    `uploadedAt` INTEGER, 
                    `syncError` TEXT, 
                    `lastAttempt` INTEGER
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_calls_research_number` ON `calls_research` (`number`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_calls_research_timestamp` ON `calls_research` (`timestamp`)")

            // Copy data from calls to calls_research
            try {
                db.execSQL("""
                    INSERT OR IGNORE INTO calls_research (
                        id, name, number, duration, timestamp, callType, recordingPath, 
                        phoneAccountId, phoneAccountComponentName, isFavorite, notes, tags, 
                        syncStatus, recordingLocalPath, recordingCloudPath, recordingUploadStatus, 
                        recordingUploadedAt, recordingUrl, retryCount, uploadedAt, syncError, lastAttempt
                    ) SELECT 
                        id, name, number, duration, timestamp, callType, recordingPath, 
                        phoneAccountId, phoneAccountComponentName, isFavorite, notes, tags, 
                        syncStatus, recordingLocalPath, recordingCloudPath, recordingUploadStatus, 
                        recordingUploadedAt, recordingUrl, retryCount, uploadedAt, syncError, lastAttempt
                    FROM calls
                """.trimIndent())
            } catch (e: Exception) {
                android.util.Log.e("DatabaseModule", "Failed to migrate calls to calls_research", e)
            }

             // 2. Create recordings_research table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `recordings_research` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                    `filePath` TEXT NOT NULL, 
                    `fileName` TEXT NOT NULL, 
                    `fileSize` INTEGER NOT NULL, 
                    `duration` INTEGER NOT NULL, 
                    `lastModified` INTEGER NOT NULL, 
                    `phoneExtracted` TEXT, 
                    `contactExtracted` TEXT, 
                    `timestampExtracted` INTEGER, 
                    `matchedCallId` INTEGER, 
                    `matchStatus` TEXT NOT NULL, 
                    `uploadStatus` TEXT NOT NULL, 
                    `cloudUrl` TEXT, 
                    `parser` TEXT, 
                    `reason` TEXT
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recordings_research_filePath` ON `recordings_research` (`filePath`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recordings_research_matchedCallId` ON `recordings_research` (`matchedCallId`)")

            // Copy data from recordings to recordings_research
            try {
                db.execSQL("""
                    INSERT OR IGNORE INTO recordings_research (
                        id, filePath, fileName, fileSize, duration, lastModified, 
                        phoneExtracted, contactExtracted, timestampExtracted, matchedCallId, matchStatus, 
                        uploadStatus, cloudUrl, parser, reason
                    ) SELECT 
                        id, filePath, fileName, fileSize, duration, lastModified, 
                        phoneExtracted, NULL AS contactExtracted, timestampExtracted, matchedCallId, matchStatus, 
                        uploadStatus, cloudUrl, parser, reason
                    FROM recordings
                """.trimIndent())
            } catch (e: Exception) {
                android.util.Log.e("DatabaseModule", "Failed to migrate recordings to recordings_research", e)
            }

            // 3. Re-create reminders table to point foreign key to calls_research
            db.execSQL("DROP INDEX IF EXISTS `index_reminders_callId`")
            db.execSQL("DROP TABLE IF EXISTS `reminders_old`")
            db.execSQL("ALTER TABLE `reminders` RENAME TO `reminders_old`")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `reminders` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                    `callId` INTEGER NOT NULL, 
                    `reminderTime` INTEGER NOT NULL, 
                    `isCompleted` INTEGER NOT NULL, 
                    `notes` TEXT, 
                    FOREIGN KEY(`callId`) REFERENCES `calls_research`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_callId` ON `reminders` (`callId`)")

            // Copy data from reminders_old to reminders
            try {
                db.execSQL("""
                    INSERT OR IGNORE INTO `reminders` (id, callId, reminderTime, isCompleted, notes)
                    SELECT id, callId, reminderTime, isCompleted, notes FROM `reminders_old`
                """.trimIndent())
                db.execSQL("DROP TABLE IF EXISTS `reminders_old`")
            } catch (e: Exception) {
                android.util.Log.e("DatabaseModule", "Failed to migrate reminders table", e)
            }
        }
    }

    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Devices Table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `devices` (
                    `id` TEXT NOT NULL,
                    `deviceName` TEXT NOT NULL,
                    `devicePhone` TEXT NOT NULL,
                    `deviceIdentifier` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `lastSyncAt` INTEGER,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_devices_deviceIdentifier` ON `devices` (`deviceIdentifier`)")

            // 2. People Table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `people` (
                    `id` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `companyName` TEXT,
                    `notes` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `syncStatus` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_people_displayName` ON `people` (`displayName`)")

            // 3. Phone Numbers Table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `phone_numbers` (
                    `id` TEXT NOT NULL,
                    `personId` TEXT NOT NULL,
                    `phoneNumber` TEXT NOT NULL,
                    `normalizedNumber` TEXT NOT NULL,
                    `phoneType` TEXT NOT NULL,
                    `isPrimary` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `syncStatus` TEXT NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`personId`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_phone_numbers_personId` ON `phone_numbers` (`personId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_phone_numbers_normalizedNumber` ON `phone_numbers` (`normalizedNumber`)")

            // 4. Contact Aliases Table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `contact_aliases` (
                    `id` TEXT NOT NULL,
                    `personId` TEXT NOT NULL,
                    `deviceId` TEXT NOT NULL,
                    `androidContactId` TEXT NOT NULL,
                    `aliasName` TEXT NOT NULL,
                    `phoneNumber` TEXT NOT NULL,
                    `normalizedNumber` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `syncStatus` TEXT NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`personId`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`deviceId`) REFERENCES `devices`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_contact_aliases_personId` ON `contact_aliases` (`personId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_contact_aliases_deviceId` ON `contact_aliases` (`deviceId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_contact_aliases_aliasName` ON `contact_aliases` (`aliasName`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_contact_aliases_normalizedNumber` ON `contact_aliases` (`normalizedNumber`)")
        }
    }

    private val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL("ALTER TABLE `calls_research` ADD COLUMN `personId` TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_calls_research_personId` ON `calls_research` (`personId`)")
                db.execSQL("ALTER TABLE `sales_calls` ADD COLUMN `personId` TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_calls_personId` ON `sales_calls` (`personId`)")
            } catch (e: Exception) {
                android.util.Log.e("DatabaseModule", "Failed to migrate database to v17", e)
            }
        }
    }

    private val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `leads` (
                        `id` TEXT NOT NULL,
                        `personId` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `priority` TEXT NOT NULL,
                        `feedback` TEXT,
                        `feedbackRating` INTEGER,
                        `notes` TEXT,
                        `ownerId` TEXT,
                        `source` TEXT NOT NULL,
                        `nextFollowUpAt` INTEGER,
                        `isArchived` INTEGER NOT NULL,
                        `archivedAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`personId`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_leads_personId` ON `leads` (`personId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_leads_status` ON `leads` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_leads_priority` ON `leads` (`priority`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_leads_nextFollowUpAt` ON `leads` (`nextFollowUpAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_leads_updatedAt` ON `leads` (`updatedAt`)")
            } catch (e: Exception) {
                android.util.Log.e("DatabaseModule", "Failed to migrate database to v18", e)
            }
        }
    }

    private val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                // 1. Create conversations table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `conversations` (
                        `id` TEXT NOT NULL,
                        `personId` TEXT NOT NULL,
                        `whatsappNumber` TEXT NOT NULL,
                        `status` TEXT NOT NULL DEFAULT 'AI_HANDLING',
                        `assignedUserId` TEXT,
                        `assignedUserName` TEXT,
                        `lastMessage` TEXT,
                        `lastMessageAt` INTEGER NOT NULL,
                        `handoverReason` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `syncStatus` TEXT NOT NULL DEFAULT 'PENDING',
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`personId`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_conversations_personId` ON `conversations` (`personId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversations_whatsappNumber` ON `conversations` (`whatsappNumber`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversations_status` ON `conversations` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversations_assignedUserId` ON `conversations` (`assignedUserId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversations_updatedAt` ON `conversations` (`updatedAt`)")

                // 2. Create conversation_messages table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `conversation_messages` (
                        `id` TEXT NOT NULL,
                        `conversationId` TEXT NOT NULL,
                        `senderType` TEXT NOT NULL,
                        `senderName` TEXT NOT NULL,
                        `messageText` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `deliveryStatus` TEXT NOT NULL DEFAULT 'SENT',
                        `syncStatus` TEXT NOT NULL DEFAULT 'PENDING',
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_messages_conversationId` ON `conversation_messages` (`conversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_messages_timestamp` ON `conversation_messages` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_messages_senderType` ON `conversation_messages` (`senderType`)")
            } catch (e: Exception) {
                android.util.Log.e("DatabaseModule", "Failed to migrate database to v19", e)
            }
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): CallVaultDatabase {
        return Room.databaseBuilder(
            context,
            CallVaultDatabase::class.java,
            Constants.DATABASE_NAME
        ).addMigrations(MIGRATION_5_6, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19)
         .fallbackToDestructiveMigration()
         .build()
     }

    @Provides
    fun provideCallDao(db: CallVaultDatabase): CallDao {
        return db.callDao()
    }

    @Provides
    fun provideReminderDao(db: CallVaultDatabase): ReminderDao {
        return db.reminderDao()
    }

    @Provides
    fun provideTracebackDao(db: CallVaultDatabase): TracebackDao {
        return db.tracebackDao()
    }

    @Provides
    fun provideSalesCallDao(db: CallVaultDatabase): SalesCallDao {
        return db.salesCallDao()
    }

    @Provides
    fun provideSyncLogDao(db: CallVaultDatabase): SyncLogDao {
        return db.syncLogDao()
    }

    @Provides
    fun provideRecordingLogDao(db: CallVaultDatabase): com.example.callog.data.local.dao.RecordingLogDao {
        return db.recordingLogDao()
    }

    @Provides
    fun provideRecordingDao(db: CallVaultDatabase): com.example.callog.data.local.dao.RecordingDao {
        return db.recordingDao()
    }

    @Provides
    fun providePersonDao(db: CallVaultDatabase): com.example.callog.data.local.dao.PersonDao {
        return db.personDao()
    }

    @Provides
    fun provideLeadDao(db: CallVaultDatabase): com.example.callog.data.local.dao.LeadDao {
        return db.leadDao()
    }

    @Provides
    fun provideConversationDao(db: CallVaultDatabase): com.example.callog.data.local.dao.ConversationDao {
        return db.conversationDao()
    }
}
