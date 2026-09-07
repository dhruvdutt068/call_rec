package com.example.callog.data.local.dao

import androidx.room.*
import com.example.callog.data.local.entity.LeadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LeadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLead(lead: LeadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeads(leads: List<LeadEntity>)

    @Update
    suspend fun updateLead(lead: LeadEntity)

    @Query("SELECT * FROM leads WHERE id = :id")
    suspend fun getLeadById(id: String): LeadEntity?

    @Query("SELECT * FROM leads WHERE personId = :personId LIMIT 1")
    fun getLeadByPersonIdFlow(personId: String): Flow<LeadEntity?>

    @Query("SELECT * FROM leads WHERE personId = :personId LIMIT 1")
    suspend fun getLeadByPersonId(personId: String): LeadEntity?

    @Query("SELECT * FROM leads ORDER BY updatedAt DESC")
    fun getAllLeadsFlow(): Flow<List<LeadEntity>>

    @Query("SELECT * FROM leads ORDER BY updatedAt DESC")
    suspend fun getAllLeads(): List<LeadEntity>

    @Query("SELECT * FROM leads WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingLeads(): List<LeadEntity>

    @Query("UPDATE leads SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("UPDATE leads SET status = :status, updatedAt = :updatedAt, syncStatus = 'PENDING' WHERE personId = :personId")
    suspend fun updateStatus(personId: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE leads SET priority = :priority, updatedAt = :updatedAt, syncStatus = 'PENDING' WHERE personId = :personId")
    suspend fun updatePriority(personId: String, priority: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE leads SET notes = :notes, updatedAt = :updatedAt, syncStatus = 'PENDING' WHERE personId = :personId")
    suspend fun updateNotes(personId: String, notes: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE leads SET feedback = :feedback, feedbackRating = :rating, updatedAt = :updatedAt, syncStatus = 'PENDING' WHERE personId = :personId")
    suspend fun updateFeedback(personId: String, feedback: String, rating: Int?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE leads SET nextFollowUpAt = :nextFollowUpAt, updatedAt = :updatedAt, syncStatus = 'PENDING' WHERE personId = :personId")
    suspend fun updateFollowUp(personId: String, nextFollowUpAt: Long?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE leads SET isArchived = 1, archivedAt = :archivedAt, updatedAt = :updatedAt, syncStatus = 'PENDING' WHERE personId = :personId")
    suspend fun archiveLead(personId: String, archivedAt: Long = System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())
}
