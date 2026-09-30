package com.example.callog.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.callog.data.remote.model.SupabaseLeadTask

@Entity(
    tableName = "lead_tasks",
    indices = [
        Index(value = ["leadId"]),
        Index(value = ["status"]),
        Index(value = ["dueAt"])
    ]
)
data class LeadTaskEntity(
    @PrimaryKey val id: Long,
    val leadId: Long,
    val taskType: String = "call",
    val title: String = "",
    val fkAssignee: Long? = null,
    val dueAt: String,
    val status: String = "pending",
    val outcome: String = "",
    val source: String = "auto",
    val completedAt: String? = null,
    val completedByName: String = "",
    val completedByRole: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    fun toRemote(): SupabaseLeadTask = SupabaseLeadTask(
        id = id,
        leadId = leadId,
        taskType = taskType,
        title = title,
        fkAssignee = fkAssignee,
        dueAt = dueAt,
        status = status,
        outcome = outcome,
        source = source,
        completedAt = completedAt,
        completedByName = completedByName,
        completedByRole = completedByRole,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromRemote(dto: SupabaseLeadTask): LeadTaskEntity = LeadTaskEntity(
            id = dto.id ?: 0L,
            leadId = dto.leadId,
            taskType = dto.taskType,
            title = dto.title,
            fkAssignee = dto.fkAssignee,
            dueAt = dto.dueAt,
            status = dto.status,
            outcome = dto.outcome,
            source = dto.source,
            completedAt = dto.completedAt,
            completedByName = dto.completedByName,
            completedByRole = dto.completedByRole,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt
        )
    }
}
