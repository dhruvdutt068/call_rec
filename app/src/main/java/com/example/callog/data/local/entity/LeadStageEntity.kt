package com.example.callog.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.callog.data.remote.model.SupabaseLeadStage

@Entity(
    tableName = "lead_stages",
    indices = [
        Index(value = ["sortOrder"])
    ]
)
data class LeadStageEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val kind: String = "pipeline",
    val createdAt: String? = null
) {
    fun toRemote(): SupabaseLeadStage = SupabaseLeadStage(
        id = id,
        name = name,
        sortOrder = sortOrder,
        isActive = isActive,
        kind = kind,
        createdAt = createdAt
    )

    companion object {
        fun fromRemote(dto: SupabaseLeadStage): LeadStageEntity = LeadStageEntity(
            id = dto.id,
            name = dto.name,
            sortOrder = dto.sortOrder,
            isActive = dto.isActive,
            kind = dto.kind,
            createdAt = dto.createdAt
        )
    }
}
