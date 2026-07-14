package com.example.callog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.callog.data.local.entity.SyncLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncLogDao {
    @Insert
    suspend fun insertLog(log: SyncLogEntity)

    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<SyncLogEntity>>

    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC")
    suspend fun getAllLogs(): List<SyncLogEntity>

    @Query("DELETE FROM sync_logs")
    suspend fun clearLogs()

    @Query("DELETE FROM sync_logs WHERE timestamp < :cutoffTime")
    suspend fun deleteLogsOlderThan(cutoffTime: Long)

    @Query("DELETE FROM sync_logs WHERE id NOT IN (SELECT id FROM sync_logs ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun trimLogs(keepCount: Int)
}
