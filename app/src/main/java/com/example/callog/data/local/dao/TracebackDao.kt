package com.example.callog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.callog.data.local.entity.TracebackEntity

@Dao
interface TracebackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTraceback(traceback: TracebackEntity)

    @Query("SELECT * FROM tracebacks ORDER BY timestamp DESC")
    suspend fun getAllTracebacks(): List<TracebackEntity>

    @Query("SELECT * FROM tracebacks WHERE callLogId = :callLogId LIMIT 1")
    suspend fun getTracebackByCallId(callLogId: Long): TracebackEntity?
}
