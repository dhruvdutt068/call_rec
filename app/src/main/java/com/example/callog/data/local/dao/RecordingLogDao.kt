package com.example.callog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.callog.data.local.entity.RecordingLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: RecordingLogEntity): Long

    @Query("SELECT * FROM recording_logs ORDER BY createdAt DESC")
    fun getAllLogsFlow(): Flow<List<RecordingLogEntity>>

    @Query("DELETE FROM recording_logs")
    suspend fun clearAllLogs()
}
