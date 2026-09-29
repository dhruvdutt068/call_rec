package com.example.callog.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object for Supabase `public.leads_master` table.
 * Primary entity representing enterprise CRM leads.
 */
@Serializable
data class SupabaseLeadMaster(
    @SerialName("id") val id: Long,
    @SerialName("fk_hash_key") val fkHashKey: String? = null,
    @SerialName("lead_name") val leadName: String? = null,
    @SerialName("lead_mobile") val leadMobile: Double? = null,
    @SerialName("fk_lead_status") val fkLeadStatus: Long? = null,
    @SerialName("fk_lead_stage") val fkLeadStage: Long? = null,
    @SerialName("fk_presales_owner") val fkPresalesOwner: Long? = null,
    @SerialName("fk_sales_owner") val fkSalesOwner: Long? = null,
    @SerialName("fk_next_action") val fkNextAction: Long? = null,
    @SerialName("next_action_at") val nextActionAt: String? = null,
    @SerialName("last_wa_contacted_at") val lastWaContactedAt: String? = null,
    @SerialName("last_call_contacted_at") val lastCallContactedAt: String? = null,
    @SerialName("requirement_captured") val requirementCaptured: Boolean? = null,
    @SerialName("requirement_captured_at") val requirementCapturedAt: String? = null,
    @SerialName("options_shared") val optionsShared: Boolean? = null,
    @SerialName("options_shared_at") val optionsSharedAt: String? = null,
    @SerialName("visit_scheduled_at") val visitScheduledAt: String? = null,
    @SerialName("visit_status") val visitStatus: Boolean? = null,
    @SerialName("lead_source") val leadSource: String? = null,
    @SerialName("fk_lead_source") val fkLeadSource: Long? = null,
    @SerialName("fk_lead_communication_id") val fkLeadCommunicationId: Long? = null,
    @SerialName("ad_id") val adId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
