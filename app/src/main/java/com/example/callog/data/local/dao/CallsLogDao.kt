package com.example.callog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.callog.data.local.entity.CallsLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallsLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallsLog(callsLog: CallsLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallsLogs(callsLogs: List<CallsLogEntity>)

    @Update
    suspend fun updateCallsLog(callsLog: CallsLogEntity)

    @Query("SELECT * FROM calls_log WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getCallsLogByPhone(phoneNumber: String): CallsLogEntity?

    @Query("SELECT * FROM calls_log WHERE phoneNumber = :phoneNumber LIMIT 1")
    fun getCallsLogByPhoneFlow(phoneNumber: String): Flow<CallsLogEntity?>

    @Query("SELECT * FROM calls_log ORDER BY updatedAt DESC")
    fun getAllCallsLogsFlow(): Flow<List<CallsLogEntity>>

    @Query("SELECT * FROM calls_log ORDER BY updatedAt DESC")
    suspend fun getAllCallsLogs(): List<CallsLogEntity>

    @Query("DELETE FROM calls_log WHERE phoneNumber = :phoneNumber")
    suspend fun deleteCallsLog(phoneNumber: String)

    @Query("DELETE FROM calls_log")
    suspend fun clearAll()
}
