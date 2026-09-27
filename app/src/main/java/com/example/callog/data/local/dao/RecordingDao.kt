package com.example.callog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.callog.data.local.entity.RecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings_research ORDER BY lastModified DESC")
    fun getAllRecordingsFlow(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings_research ORDER BY lastModified DESC")
    suspend fun getAllRecordings(): List<RecordingEntity>

    @Query("SELECT * FROM recordings_research WHERE id = :id LIMIT 1")
    suspend fun getRecordingById(id: Long): RecordingEntity?

    @Query("SELECT * FROM recordings_research WHERE filePath = :filePath LIMIT 1")
    suspend fun getRecordingByPath(filePath: String): RecordingEntity?

    @Query("SELECT * FROM recordings_research WHERE checksum = :checksum LIMIT 1")
    suspend fun getRecordingByChecksum(checksum: String): RecordingEntity?

    @Query("SELECT * FROM recordings_research WHERE matchedCallId = :callId LIMIT 1")
    suspend fun getRecordingByCallId(callId: Long): RecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecordings(recordings: List<RecordingEntity>)

    @Update
    suspend fun updateRecording(recording: RecordingEntity)

    @Query("DELETE FROM recordings_research")
    suspend fun clearAllRecordings()
}
