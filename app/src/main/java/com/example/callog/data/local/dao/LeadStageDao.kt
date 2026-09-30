package com.example.callog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.callog.data.local.entity.LeadStageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LeadStageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeadStage(stage: LeadStageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeadStages(stages: List<LeadStageEntity>)

    @Query("SELECT * FROM lead_stages ORDER BY sortOrder ASC")
    fun getAllLeadStagesFlow(): Flow<List<LeadStageEntity>>

    @Query("SELECT * FROM lead_stages ORDER BY sortOrder ASC")
    suspend fun getAllLeadStages(): List<LeadStageEntity>

    @Query("SELECT * FROM lead_stages WHERE id = :id LIMIT 1")
    suspend fun getLeadStageById(id: Long): LeadStageEntity?

    @Query("DELETE FROM lead_stages")
    suspend fun clearAll()
}
