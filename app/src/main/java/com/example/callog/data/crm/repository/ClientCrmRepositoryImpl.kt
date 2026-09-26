package com.example.callog.data.crm.repository

import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.data.crm.mapper.CrmPayloadMapper
import com.example.callog.data.crm.remote.ClientCrmRemoteDataSource
import com.example.callog.domain.crm.model.CrmCallEvent
import com.example.callog.domain.crm.model.CrmLeadSummary
import com.example.callog.domain.crm.repository.CrmRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientCrmRepositoryImpl @Inject constructor(
    private val remoteDataSource: ClientCrmRemoteDataSource
) : CrmRepository {

    override suspend fun findLeadByPhone(rawPhoneNumber: String): Result<CrmLeadSummary?> = withContext(Dispatchers.IO) {
        if (rawPhoneNumber.isBlank()) return@withContext Result.success(null)
        val crmPhone = CrmPayloadMapper.toCrmPhoneNumber(rawPhoneNumber)
        val remoteResult = remoteDataSource.findLeadByPhone(crmPhone)
        remoteResult.map { dto ->
            dto?.let { CrmPayloadMapper.toDomainLeadSummary(it) }
        }
    }

    override suspend fun publishCallEvent(event: CrmCallEvent): Result<Unit> = withContext(Dispatchers.IO) {
        // Disabled: Client CRM tables (calls_log, lead_communications) syncing is turned off
        DeveloperLogger.info("CRM_EVENT_SKIPPED", "External CRM publishing is disabled for callId=${event.callId}")
        Result.success(Unit)
    }

    override suspend fun publishBatchCallEvents(events: List<CrmCallEvent>): Result<Unit> = withContext(Dispatchers.IO) {
        if (events.isEmpty()) return@withContext Result.success(Unit)
        // Disabled: Client CRM tables (calls_log, lead_communications) syncing is turned off
        DeveloperLogger.info("CRM_BATCH_SKIPPED", "External CRM batch publishing is disabled (${events.size} events skipped)")
        Result.success(Unit)
    }

    override fun observeLeadByPhone(rawPhoneNumber: String): Flow<CrmLeadSummary?> = flow {
        val result = findLeadByPhone(rawPhoneNumber)
        emit(result.getOrNull())
    }.flowOn(Dispatchers.IO)
}
