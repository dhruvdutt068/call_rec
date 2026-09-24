package com.example.callog.data.crm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object mapping directly to the client's external CRM `calls_log` table.
 * NOTE: In client's schema, `phone_number` is the Primary Key representing lead aggregated state.
 */
@Serializable
data class ClientCallsLogDto(
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("lead_name") val leadName: String? = null,
    @SerialName("journey_stage") val journeyStage: String? = null,
    @SerialName("bot_status") val botStatus: String? = null,
    @SerialName("pause_reason") val pauseReason: String? = null,
    @SerialName("bhk") val bhk: String? = null,
    @SerialName("locality") val locality: String? = null,
    @SerialName("budget_min") val budgetMin: Double? = null,
    @SerialName("budget_max") val budgetMax: Double? = null,
    @SerialName("timeline") val timeline: String? = null,
    @SerialName("purpose") val purpose: String? = null,
    @SerialName("amenities") val amenities: String? = null,
    @SerialName("requirements_complete") val requirementsComplete: Boolean? = null,
    @SerialName("pdf_status") val pdfStatus: String? = null,
    @SerialName("pdf_sent_at") val pdfSentAt: String? = null,
    @SerialName("meeting_scheduled_at") val meetingScheduledAt: String? = null,
    @SerialName("campaign_name") val campaignName: String? = null,
    @SerialName("broker_name") val brokerName: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("last_message_text") val lastMessageText: String? = null
)
