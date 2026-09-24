package com.example.callog.domain.crm.model

/**
 * Domain-level representation of a CRM Lead summary.
 * Callog domain only consumes what it requires for presentation and identity association.
 */
data class CrmLeadSummary(
    val externalLeadId: String,
    val rawPhoneNumber: String,
    val displayName: String? = null,
    val journeyStage: String? = null,
    val status: String? = null,
    val assignedAgent: String? = null,
    val lastInteractionAt: Long? = null,
    val metadata: Map<String, String> = emptyMap()
)
