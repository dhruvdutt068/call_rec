package com.example.callog.data.crm.mapper

/**
 * Mapper responsible for translating domain integration events into client CRM-specific payloads.
 * Crucially isolates phone formatting required by external CRM matching from Callog's internal domain.
 */
object CrmPayloadMapper {

    /**
     * Formats phone number for external CRM indexing/matching.
     * Raw Callog canonical phone strings remain intact in Callog core.
     */
    fun toCrmPhoneNumber(rawPhoneNumber: String): String {
        val digitsOnly = rawPhoneNumber.replace(Regex("[^0-9+]"), "")
        return digitsOnly.ifBlank { rawPhoneNumber.trim() }
    }
}
