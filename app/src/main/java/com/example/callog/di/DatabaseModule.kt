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

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): CallVaultDatabase {
        return Room.databaseBuilder(
            context,
            CallVaultDatabase::class.java,
            Constants.DATABASE_NAME
        ).addMigrations(MIGRATION_5_6)
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
}
