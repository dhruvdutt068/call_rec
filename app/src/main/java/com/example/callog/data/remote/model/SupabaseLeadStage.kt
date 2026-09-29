package com.example.callog.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object for Supabase `public.lead_stages` table.
 */
@Serializable
data class SupabaseLeadStage(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("kind") val kind: String = "pipeline",
    @SerialName("created_at") val createdAt: String? = null
)
