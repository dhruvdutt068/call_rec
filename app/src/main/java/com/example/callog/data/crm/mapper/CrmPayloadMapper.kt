package com.example.callog.data.crm.mapper

import com.example.callog.core.utils.PhoneNumberNormalizer
import com.example.callog.data.crm.model.ClientCallsLogDto
import com.example.callog.data.crm.model.ClientLeadCommunicationDto
import com.example.callog.domain.crm.model.CrmCallEvent
import com.example.callog.domain.crm.model.CrmLeadSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Mapper responsible for translating domain integration events into client CRM-specific payloads.
 * Crucially isolates phone formatting required by the external CRM system from Callog's internal domain.
 */
object CrmPayloadMapper {

    private fun formatTimestampUtc(epochMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(epochMs))
    }

    private fun parseTimestampUtc(isoString: String?): Long? {
        if (isoString.isNullOrBlank()) return null
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(isoString)?.time
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Formats phone number for external CRM indexing/matching.
     * Raw Callog canonical phone strings remain intact in Callog core.
     */
    fun toCrmPhoneNumber(rawPhoneNumber: String): String {
        val digitsOnly = rawPhoneNumber.replace(Regex("[^0-9+]"), "")
        return digitsOnly.ifBlank { rawPhoneNumber.trim() }
    }

    fun toClientCallsLogDto(event: CrmCallEvent): ClientCallsLogDto {
        val crmPhone = toCrmPhoneNumber(event.rawPhoneNumber)
        val isoTimestamp = formatTimestampUtc(event.timestampEpochMs)
        return ClientCallsLogDto(
            phoneNumber = crmPhone,
            leadName = event.contactName?.ifBlank { null },
            lastMessageAt = isoTimestamp,
            lastMessageText = "Call: ${event.callType} (${event.durationSeconds}s)"
        )
    }

    fun toClientLeadCommunicationDto(event: CrmCallEvent): ClientLeadCommunicationDto {
        val crmPhone = toCrmPhoneNumber(event.rawPhoneNumber)
        val agentPhone = toCrmPhoneNumber(event.salespersonPhone)
        val isoTimestamp = formatTimestampUtc(event.timestampEpochMs)
        return ClientLeadCommunicationDto(
            id = null,
            phoneNumber = crmPhone,
            leadName = event.contactName,
            communicationType = "CALL",
            callType = event.callType,
            duration = event.durationSeconds,
            agentPhone = agentPhone,
            agentName = event.salespersonName,
            recordingUrl = event.recordingCloudUrl,
            notes = event.notes,
            callId = event.callId,
            createdAt = isoTimestamp
        )
    }

    fun toDomainLeadSummary(dto: ClientCallsLogDto): CrmLeadSummary {
        val metadata = mutableMapOf<String, String>()
        dto.bhk?.let { metadata["bhk"] = it }
        dto.locality?.let { metadata["locality"] = it }
        dto.timeline?.let { metadata["timeline"] = it }
        dto.purpose?.let { metadata["purpose"] = it }
        dto.campaignName?.let { metadata["campaign"] = it }
        dto.brokerName?.let { metadata["broker"] = it }

        return CrmLeadSummary(
            externalLeadId = dto.phoneNumber,
            rawPhoneNumber = dto.phoneNumber,
            displayName = dto.leadName,
            journeyStage = dto.journeyStage,
            status = dto.botStatus,
            assignedAgent = dto.brokerName,
            lastInteractionAt = parseTimestampUtc(dto.lastMessageAt),
            metadata = metadata
        )
    }
}
