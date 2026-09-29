package com.example.callog.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object for Supabase `public.lead_tasks` table.
 */
@Serializable
data class SupabaseLeadTask(
    @SerialName("id") val id: Long? = null,
    @SerialName("lead_id") val leadId: Long,
    @SerialName("task_type") val taskType: String = "call",
    @SerialName("title") val title: String = "",
    @SerialName("fk_assignee") val fkAssignee: Long? = null,
    @SerialName("due_at") val dueAt: String,
    @SerialName("status") val status: String = "pending",
    @SerialName("outcome") val outcome: String = "",
    @SerialName("source") val source: String = "auto",
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("completed_by_name") val completedByName: String = "",
    @SerialName("completed_by_role") val completedByRole: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
