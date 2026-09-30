package com.example.callog.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.callog.data.remote.model.SupabaseLeadStatus

@Entity(
    tableName = "lead_statuses",
    indices = [
        Index(value = ["sortOrder"])
    ]
)
data class LeadStatusEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val countsAsActive: Boolean = false,
    val createdAt: String? = null
) {
    fun toRemote(): SupabaseLeadStatus = SupabaseLeadStatus(
        id = id,
        name = name,
        sortOrder = sortOrder,
        isActive = isActive,
        countsAsActive = countsAsActive,
        createdAt = createdAt
    )

    companion object {
        fun fromRemote(dto: SupabaseLeadStatus): LeadStatusEntity = LeadStatusEntity(
            id = dto.id,
            name = dto.name,
            sortOrder = dto.sortOrder,
            isActive = dto.isActive,
            countsAsActive = dto.countsAsActive,
            createdAt = dto.createdAt
        )
    }
}
