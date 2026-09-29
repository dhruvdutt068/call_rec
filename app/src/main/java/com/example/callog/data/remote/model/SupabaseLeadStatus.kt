package com.example.callog.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object for Supabase `public.lead_statuses` table.
 */
@Serializable
data class SupabaseLeadStatus(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("counts_as_active") val countsAsActive: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)
