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
            val dto = CrmPayloadMapper.toClientLeadCommunicationDto(event)
            val result = remoteDataSource.insertLeadCommunication(dto)
            if (result.isSuccess) {
                DeveloperLogger.success("CRM_EVENT_PUBLISHED", "Logged callId=${event.callId} to lead_communications")
            } else {
                DeveloperLogger.warning("CRM_EVENT_FAILED", "Failed logging callId=${event.callId} to lead_communications: ${result.exceptionOrNull()?.message}")
            }
            result
        } catch (e: Exception) {
            DeveloperLogger.error("CRM_EVENT_EXCEPTION", "Error publishing call event: ${e.message}", exception = e)
            Result.failure(e)
        }
    }

    override suspend fun publishBatchCallEvents(events: List<CrmCallEvent>): Result<Unit> = withContext(Dispatchers.IO) {
        if (events.isEmpty()) return@withContext Result.success(Unit)
        try {
            val dtos = events.map { CrmPayloadMapper.toClientLeadCommunicationDto(it) }
            val result = remoteDataSource.insertBatchLeadCommunications(dtos)
            if (result.isSuccess) {
                DeveloperLogger.success("CRM_BATCH_PUBLISHED", "Batch logged ${events.size} calls to lead_communications")
            } else {
                DeveloperLogger.warning("CRM_BATCH_FAILED", "Failed batch logging calls to lead_communications: ${result.exceptionOrNull()?.message}")
            }
            result
        } catch (e: Exception) {
            DeveloperLogger.error("CRM_BATCH_EXCEPTION", "Error publishing batch call events: ${e.message}", exception = e)
            Result.failure(e)
        }
    }

    override fun observeLeadByPhone(rawPhoneNumber: String): Flow<CrmLeadSummary?> = flow {
        val result = findLeadByPhone(rawPhoneNumber)
        emit(result.getOrNull())
    }.flowOn(Dispatchers.IO)
}
