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
        try {
            // 1. Upsert high-level aggregated status in client calls_log
            val callsLogDto = CrmPayloadMapper.toClientCallsLogDto(event)
            remoteDataSource.upsertCallsLog(callsLogDto)

            // 2. Insert granular interaction event into lead_communications
            val commDto = CrmPayloadMapper.toClientLeadCommunicationDto(event)
            remoteDataSource.insertLeadCommunication(commDto)

            DeveloperLogger.info("CRM_EVENT_PUBLISHED", "Published call event for callId=${event.callId}")
            Result.success(Unit)
        } catch (e: Exception) {
            DeveloperLogger.error("CRM_PUBLISH_FAILED", "Failed publishing call event for callId=${event.callId}", exception = e)
            Result.failure(e)
        }
    }

    override suspend fun publishBatchCallEvents(events: List<CrmCallEvent>): Result<Unit> = withContext(Dispatchers.IO) {
        if (events.isEmpty()) return@withContext Result.success(Unit)
        try {
            // 1. Group events by phone number to upsert the latest state into calls_log
            val latestByPhone = events.groupBy { CrmPayloadMapper.toCrmPhoneNumber(it.rawPhoneNumber) }
                .mapValues { (_, callEvents) -> callEvents.maxByOrNull { it.timestampEpochMs }!! }

            for ((_, latestEvent) in latestByPhone) {
                val callsLogDto = CrmPayloadMapper.toClientCallsLogDto(latestEvent)
                remoteDataSource.upsertCallsLog(callsLogDto)
            }

            // 2. Batch insert all communication interactions into lead_communications
            val commDtos = events.map { CrmPayloadMapper.toClientLeadCommunicationDto(it) }
            remoteDataSource.insertBatchLeadCommunications(commDtos)

            DeveloperLogger.success("CRM_BATCH_SYNCED", "Successfully synced ${events.size} call events to Client CRM.")
            Result.success(Unit)
        } catch (e: Exception) {
            DeveloperLogger.error("CRM_BATCH_FAILED", "Failed syncing batch call events to Client CRM", exception = e)
            Result.failure(e)
        }
    }

    override fun observeLeadByPhone(rawPhoneNumber: String): Flow<CrmLeadSummary?> = flow {
        val result = findLeadByPhone(rawPhoneNumber)
        emit(result.getOrNull())
    }.flowOn(Dispatchers.IO)
}
