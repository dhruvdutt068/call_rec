package com.example.callog.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.callog.data.crm.model.ClientCallsLogDto

@Entity(
    tableName = "calls_log",
    indices = [
        Index(value = ["journeyStage"]),
        Index(value = ["botStatus"]),
        Index(value = ["updatedAt"])
    ]
)
data class CallsLogEntity(
    @PrimaryKey val phoneNumber: String,
    val leadName: String? = null,
    val journeyStage: String? = null,
    val botStatus: String? = null,
    val pauseReason: String? = null,
    val bhk: String? = null,
    val locality: String? = null,
    val budgetMin: Double? = null,
    val budgetMax: Double? = null,
    val timeline: String? = null,
    val purpose: String? = null,
    val amenities: String? = null,
    val requirementsComplete: Boolean? = null,
    val pdfStatus: String? = null,
    val pdfSentAt: String? = null,
    val meetingScheduledAt: String? = null,
    val campaignName: String? = null,
    val brokerName: String? = null,
    val lastMessageAt: String? = null,
    val lastMessageText: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    fun toRemote(): ClientCallsLogDto = ClientCallsLogDto(
        phoneNumber = phoneNumber,
        leadName = leadName,
        journeyStage = journeyStage,
        botStatus = botStatus,
        pauseReason = pauseReason,
        bhk = bhk,
        locality = locality,
        budgetMin = budgetMin,
        budgetMax = budgetMax,
        timeline = timeline,
        purpose = purpose,
        amenities = amenities,
        requirementsComplete = requirementsComplete,
        pdfStatus = pdfStatus,
        pdfSentAt = pdfSentAt,
        meetingScheduledAt = meetingScheduledAt,
        campaignName = campaignName,
        brokerName = brokerName,
        lastMessageAt = lastMessageAt,
        lastMessageText = lastMessageText
    )

    companion object {
        fun fromRemote(dto: ClientCallsLogDto, createdAt: String? = null, updatedAt: String? = null): CallsLogEntity = CallsLogEntity(
            phoneNumber = dto.phoneNumber,
            leadName = dto.leadName,
            journeyStage = dto.journeyStage,
            botStatus = dto.botStatus,
            pauseReason = dto.pauseReason,
            bhk = dto.bhk,
            locality = dto.locality,
            budgetMin = dto.budgetMin,
            budgetMax = dto.budgetMax,
            timeline = dto.timeline,
            purpose = dto.purpose,
            amenities = dto.amenities,
            requirementsComplete = dto.requirementsComplete,
            pdfStatus = dto.pdfStatus,
            pdfSentAt = dto.pdfSentAt,
            meetingScheduledAt = dto.meetingScheduledAt,
            campaignName = dto.campaignName,
            brokerName = dto.brokerName,
            lastMessageAt = dto.lastMessageAt,
            lastMessageText = dto.lastMessageText,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
