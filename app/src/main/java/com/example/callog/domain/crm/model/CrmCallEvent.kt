package com.example.callog.domain.crm.model

/**
 * Technical integration event published when a call interaction event occurs in Callog.
 */
data class CrmCallEvent(
    val callId: Long,
    val personId: String? = null,
    val rawPhoneNumber: String,
    val contactName: String? = null,
    val salespersonPhone: String,
    val salespersonName: String? = null,
    val callType: String, // INCOMING, OUTGOING, MISSED, REJECTED
    val durationSeconds: Int,
    val timestampEpochMs: Long,
    val recordingCloudUrl: String? = null,
    val simSlot: Int? = null,
    val notes: String? = null
)
