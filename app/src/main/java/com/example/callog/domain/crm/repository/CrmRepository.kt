package com.example.callog.domain.crm.repository

import com.example.callog.domain.crm.model.CrmLeadSummary
import kotlinx.coroutines.flow.Flow

/**
 * Domain-level contract for CRM integration operations.
 * Pure Kotlin interface free of any database, HTTP, Supabase, or Android dependencies.
 */
interface CrmRepository {
    /**
     * Finds a CRM lead summary matching the given raw phone number.
     * Number normalization/formatting for CRM matching is handled internally by the adapter.
     */
    suspend fun findLeadByPhone(rawPhoneNumber: String): Result<CrmLeadSummary?>

    /**
     * Observes real-time lead updates for a canonical Person ID if available.
     */
    fun observeLeadByPhone(rawPhoneNumber: String): Flow<CrmLeadSummary?>
}
