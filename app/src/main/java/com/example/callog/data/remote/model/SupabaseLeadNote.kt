package com.example.callog.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object for Supabase `public.lead_notes` table.
 */
@Serializable
data class SupabaseLeadNote(
    @SerialName("id") val id: Long? = null,
    @SerialName("lead_id") val leadId: Long,
    @SerialName("note_text") val noteText: String,
    @SerialName("author_name") val authorName: String,
    @SerialName("author_role") val authorRole: String = "rep",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
