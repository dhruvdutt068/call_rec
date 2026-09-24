package com.example.callog.data.crm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO mapping to the external CRM `lead_communications` table for multi-call logging per lead.
 */
@Serializable
data class ClientLeadCommunicationDto(
    @SerialName("id") val id: String? = null,
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("lead_name") val leadName: String? = null,
    @SerialName("communication_type") val communicationType: String = "CALL",
    @SerialName("call_type") val callType: String,
    @SerialName("duration") val duration: Int,
    @SerialName("agent_phone") val agentPhone: String? = null,
    @SerialName("agent_name") val agentName: String? = null,
    @SerialName("recording_url") val recordingUrl: String? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("call_id") val callId: Long? = null,
    @SerialName("created_at") val createdAt: String
)
