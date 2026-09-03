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
}
