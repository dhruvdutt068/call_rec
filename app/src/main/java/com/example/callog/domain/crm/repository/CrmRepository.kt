package com.example.callog.domain.crm.repository

import com.example.callog.domain.crm.model.CrmCallEvent
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
     * Publishes a single call interaction event to the external CRM.
     */
    suspend fun publishCallEvent(event: CrmCallEvent): Result<Unit>

    /**
     * Publishes a batch of call interaction events to the external CRM in an offline-safe transaction.
     */
    suspend fun publishBatchCallEvents(events: List<CrmCallEvent>): Result<Unit>

    /**
     * Observes real-time lead updates for a canonical Person ID if available.
     */
    fun observeLeadByPhone(rawPhoneNumber: String): Flow<CrmLeadSummary?>
}
