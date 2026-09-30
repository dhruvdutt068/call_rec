package com.example.callog.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.callog.data.remote.model.SupabaseLeadMaster

@Entity(
    tableName = "leads_master",
    indices = [
        Index(value = ["fkLeadStage"]),
        Index(value = ["fkLeadStatus"]),
        Index(value = ["leadMobile"]),
        Index(value = ["updatedAt"])
    ]
)
data class LeadMasterEntity(
    @PrimaryKey val id: Long,
    val fkHashKey: String? = null,
    val leadName: String? = null,
    val leadMobile: Double? = null,
    val fkLeadStatus: Long? = null,
    val fkLeadStage: Long? = null,
    val fkPresalesOwner: Long? = null,
    val fkSalesOwner: Long? = null,
    val fkNextAction: Long? = null,
    val nextActionAt: String? = null,
    val lastWaContactedAt: String? = null,
    val lastCallContactedAt: String? = null,
    val requirementCaptured: Boolean? = null,
    val requirementCapturedAt: String? = null,
    val optionsShared: Boolean? = null,
    val optionsSharedAt: String? = null,
    val visitScheduledAt: String? = null,
    val visitStatus: Boolean? = null,
    val leadSource: String? = null,
    val fkLeadSource: Long? = null,
    val fkLeadCommunicationId: Long? = null,
    val adId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val syncStatus: String = "SYNCED"
) {
    fun toRemote(): SupabaseLeadMaster = SupabaseLeadMaster(
        id = id,
        fkHashKey = fkHashKey,
        leadName = leadName,
        leadMobile = leadMobile,
        fkLeadStatus = fkLeadStatus,
        fkLeadStage = fkLeadStage,
        fkPresalesOwner = fkPresalesOwner,
        fkSalesOwner = fkSalesOwner,
        fkNextAction = fkNextAction,
        nextActionAt = nextActionAt,
        lastWaContactedAt = lastWaContactedAt,
        lastCallContactedAt = lastCallContactedAt,
        requirementCaptured = requirementCaptured,
        requirementCapturedAt = requirementCapturedAt,
        optionsShared = optionsShared,
        optionsSharedAt = optionsSharedAt,
        visitScheduledAt = visitScheduledAt,
        visitStatus = visitStatus,
        leadSource = leadSource,
        fkLeadSource = fkLeadSource,
        fkLeadCommunicationId = fkLeadCommunicationId,
        adId = adId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromRemote(dto: SupabaseLeadMaster, syncStatus: String = "SYNCED"): LeadMasterEntity = LeadMasterEntity(
            id = dto.id,
            fkHashKey = dto.fkHashKey,
            leadName = dto.leadName,
            leadMobile = dto.leadMobile,
            fkLeadStatus = dto.fkLeadStatus,
            fkLeadStage = dto.fkLeadStage,
            fkPresalesOwner = dto.fkPresalesOwner,
            fkSalesOwner = dto.fkSalesOwner,
            fkNextAction = dto.fkNextAction,
            nextActionAt = dto.nextActionAt,
            lastWaContactedAt = dto.lastWaContactedAt,
            lastCallContactedAt = dto.lastCallContactedAt,
            requirementCaptured = dto.requirementCaptured,
            requirementCapturedAt = dto.requirementCapturedAt,
            optionsShared = dto.optionsShared,
            optionsSharedAt = dto.optionsSharedAt,
            visitScheduledAt = dto.visitScheduledAt,
            visitStatus = dto.visitStatus,
            leadSource = dto.leadSource,
            fkLeadSource = dto.fkLeadSource,
            fkLeadCommunicationId = dto.fkLeadCommunicationId,
            adId = dto.adId,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            syncStatus = syncStatus
        )
    }
}
