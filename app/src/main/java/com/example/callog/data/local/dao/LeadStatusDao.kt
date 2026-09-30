package com.example.callog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.callog.data.local.entity.LeadStatusEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LeadStatusDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeadStatus(status: LeadStatusEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeadStatuses(statuses: List<LeadStatusEntity>)

    @Query("SELECT * FROM lead_statuses ORDER BY sortOrder ASC")
    fun getAllLeadStatusesFlow(): Flow<List<LeadStatusEntity>>

    @Query("SELECT * FROM lead_statuses ORDER BY sortOrder ASC")
    suspend fun getAllLeadStatuses(): List<LeadStatusEntity>

    @Query("DELETE FROM lead_statuses")
    suspend fun clearAll()
}
