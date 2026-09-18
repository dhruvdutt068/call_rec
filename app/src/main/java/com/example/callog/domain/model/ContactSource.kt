package com.example.callog.domain.model

/**
 * Distinguishes between remote/cached CRM contacts (CLOUD) and local address book contacts (DEVICE).
 * Architectural rule: Cloud contacts and Device contacts are strictly isolated sources.
 */
enum class ContactSource {
    CLOUD,
    DEVICE
}
