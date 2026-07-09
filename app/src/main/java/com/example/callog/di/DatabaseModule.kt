package com.example.callog.di

import android.content.Context
import androidx.room.Room
import com.example.callog.core.constants.Constants
import com.example.callog.data.local.dao.CallDao
import com.example.callog.data.local.dao.ReminderDao
import com.example.callog.data.local.database.CallVaultDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): CallVaultDatabase {
        return Room.databaseBuilder(
            context,
            CallVaultDatabase::class.java,
            Constants.DATABASE_NAME
        ).fallbackToDestructiveMigration()
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
}
