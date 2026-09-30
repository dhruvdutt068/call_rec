package com.example.callog.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.callog.data.remote.model.SupabaseLeadNote

@Entity(
    tableName = "lead_notes",
    indices = [
        Index(value = ["leadId"]),
        Index(value = ["createdAt"])
    ]
)
data class LeadNoteEntity(
    @PrimaryKey val id: Long,
    val leadId: Long,
    val noteText: String,
    val authorName: String,
    val authorRole: String = "rep",
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    fun toRemote(): SupabaseLeadNote = SupabaseLeadNote(
        id = id,
        leadId = leadId,
        noteText = noteText,
        authorName = authorName,
        authorRole = authorRole,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromRemote(dto: SupabaseLeadNote): LeadNoteEntity = LeadNoteEntity(
            id = dto.id ?: 0L,
            leadId = dto.leadId,
            noteText = dto.noteText,
            authorName = dto.authorName,
            authorRole = dto.authorRole,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt
        )
    }
}
