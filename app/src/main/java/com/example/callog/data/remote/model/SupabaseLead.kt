package com.example.callog.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseLead(
    @SerialName("id") val id: String,
    @SerialName("person_id") val personId: String,
    @SerialName("status") val status: String,
    @SerialName("priority") val priority: String,
    @SerialName("feedback") val feedback: String? = null,
    @SerialName("feedback_rating") val feedbackRating: Int? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("owner_id") val ownerId: String? = null,
    @SerialName("source") val source: String,
    @SerialName("next_follow_up_at") val nextFollowUpAt: String? = null,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)
