package com.example.callog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.callog.data.local.entity.LeadNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LeadNoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: LeadNoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<LeadNoteEntity>)

    @Update
    suspend fun updateNote(note: LeadNoteEntity)

    @Query("SELECT * FROM lead_notes WHERE leadId = :leadId ORDER BY createdAt DESC")
    fun getNotesForLeadFlow(leadId: Long): Flow<List<LeadNoteEntity>>

    @Query("SELECT * FROM lead_notes WHERE leadId = :leadId ORDER BY createdAt DESC")
    suspend fun getNotesForLead(leadId: Long): List<LeadNoteEntity>

    @Query("SELECT * FROM lead_notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): LeadNoteEntity?

    @Query("DELETE FROM lead_notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    @Query("DELETE FROM lead_notes")
    suspend fun clearAll()
}
