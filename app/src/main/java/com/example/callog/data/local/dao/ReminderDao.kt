package com.example.callog.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.example.callog.data.local.entity.CallEntity
import com.example.callog.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

data class ReminderWithCall(
    @Embedded val reminder: ReminderEntity,
    @Relation(
        parentColumn = "callId",
        entityColumn = "id"
    )
    val call: CallEntity
)

@Dao
interface ReminderDao {
    @Transaction
    @Query("SELECT * FROM reminders ORDER BY reminderTime ASC")
    fun getAllRemindersFlow(): Flow<List<ReminderWithCall>>

    @Transaction
    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY reminderTime ASC")
    fun getPendingRemindersFlow(): Flow<List<ReminderWithCall>>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE callId = :callId")
    suspend fun deleteRemindersForCall(callId: Long)
}
