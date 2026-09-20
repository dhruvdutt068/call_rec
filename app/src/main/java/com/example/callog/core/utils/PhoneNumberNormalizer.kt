package com.example.callog.core.utils

object PhoneNumberNormalizer {
    /**
     * Normalizes phone numbers consistently for identity matching across devices.
     * Strips non-digit characters and extracts standard canonical national subscriber number (last 10 digits).
     */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val digitsOnly = raw.replace(Regex("[^0-9]"), "")
        return if (digitsOnly.length >= 10) {
            digitsOnly.takeLast(10)
        } else {
            digitsOnly
        }
    }

    /**
     * Masks phone numbers for secure diagnostic logging (e.g. ******4531).
     */
    fun mask(phone: String?): String {
        if (phone.isNullOrBlank()) return "<blank>"
        val normalized = normalize(phone)
        return if (normalized.length <= 4) {
            "****"
        } else {
            "*".repeat(normalized.length - 4) + normalized.takeLast(4)
        }
    }
}
