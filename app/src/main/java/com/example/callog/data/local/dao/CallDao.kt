package com.example.callog.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.callog.data.local.entity.CallEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallDao {
    @Query("SELECT * FROM calls ORDER BY timestamp DESC")
    fun getAllCallsFlow(): Flow<List<CallEntity>>

    @Query("SELECT * FROM calls ORDER BY timestamp DESC")
    suspend fun getAllCalls(): List<CallEntity>

    @Query("SELECT * FROM calls WHERE id = :id LIMIT 1")
    suspend fun getCallById(id: Long): CallEntity?

    @Query("SELECT * FROM calls WHERE id = :id LIMIT 1")
    fun getCallByIdFlow(id: Long): Flow<CallEntity?>

    @Query("SELECT * FROM calls WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavoriteCallsFlow(): Flow<List<CallEntity>>

    @Query("SELECT * FROM calls WHERE recordingPath IS NOT NULL ORDER BY timestamp DESC")
    fun getCallsWithRecordingsFlow(): Flow<List<CallEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCall(call: CallEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCalls(calls: List<CallEntity>)

    @Update
    suspend fun updateCall(call: CallEntity)

    @Delete
    suspend fun deleteCall(call: CallEntity)

    @Query("DELETE FROM calls")
    suspend fun deleteAllCalls()

    @Query("SELECT * FROM calls WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingCalls(): List<CallEntity>

    @Query("UPDATE calls SET syncStatus = :syncStatus WHERE id = :callId")
    suspend fun updateSyncStatus(callId: Long, syncStatus: String)

    @Query("UPDATE calls SET syncStatus = 'PENDING'")
    suspend fun resetAllSyncStatus()
}
