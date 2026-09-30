package com.example.callog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.callog.data.local.entity.LeadMasterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LeadMasterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeadMaster(lead: LeadMasterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeadsMaster(leads: List<LeadMasterEntity>)

    @Update
    suspend fun updateLeadMaster(lead: LeadMasterEntity)

    @Query("SELECT * FROM leads_master WHERE id = :id LIMIT 1")
    suspend fun getLeadMasterById(id: Long): LeadMasterEntity?

    @Query("SELECT * FROM leads_master WHERE id = :id LIMIT 1")
    fun getLeadMasterByIdFlow(id: Long): Flow<LeadMasterEntity?>

    @Query("SELECT * FROM leads_master ORDER BY updatedAt DESC")
    fun getAllLeadsMasterFlow(): Flow<List<LeadMasterEntity>>

    @Query("SELECT * FROM leads_master ORDER BY updatedAt DESC")
    suspend fun getAllLeadsMaster(): List<LeadMasterEntity>

    @Query("SELECT * FROM leads_master WHERE fkLeadStage = :stageId ORDER BY updatedAt DESC")
    fun getLeadsMasterByStageFlow(stageId: Long): Flow<List<LeadMasterEntity>>

    @Query("SELECT * FROM leads_master WHERE fkLeadStage = :stageId ORDER BY updatedAt DESC")
    suspend fun getLeadsMasterByStage(stageId: Long): List<LeadMasterEntity>

    @Query("UPDATE leads_master SET fkLeadStage = :newStageId, updatedAt = :updatedAt, syncStatus = :syncStatus WHERE id = :id")
    suspend fun updateLeadStage(id: Long, newStageId: Long, updatedAt: String, syncStatus: String = "PENDING")

    @Query("SELECT * FROM leads_master WHERE leadMobile = :mobile LIMIT 1")
    suspend fun getLeadMasterByMobile(mobile: Double): LeadMasterEntity?

    @Query("DELETE FROM leads_master WHERE id = :id")
    suspend fun deleteLeadMaster(id: Long)

    @Query("DELETE FROM leads_master")
    suspend fun clearAll()
}
