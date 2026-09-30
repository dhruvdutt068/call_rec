package com.example.callog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.callog.data.local.entity.LeadTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LeadTaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: LeadTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<LeadTaskEntity>)

    @Update
    suspend fun updateTask(task: LeadTaskEntity)

    @Query("SELECT * FROM lead_tasks WHERE leadId = :leadId ORDER BY dueAt ASC")
    fun getTasksForLeadFlow(leadId: Long): Flow<List<LeadTaskEntity>>

    @Query("SELECT * FROM lead_tasks WHERE leadId = :leadId ORDER BY dueAt ASC")
    suspend fun getTasksForLead(leadId: Long): List<LeadTaskEntity>

    @Query("SELECT * FROM lead_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): LeadTaskEntity?

    @Query("DELETE FROM lead_tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("DELETE FROM lead_tasks")
    suspend fun clearAll()
}
