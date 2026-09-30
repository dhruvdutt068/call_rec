package com.example.callog.data.crm.repository

import com.example.callog.data.local.dao.LeadMasterDao
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
    private val leadMasterDao: LeadMasterDao
) : CrmRepository {

    override suspend fun findLeadByPhone(rawPhoneNumber: String): Result<CrmLeadSummary?> = withContext(Dispatchers.IO) {
        if (rawPhoneNumber.isBlank()) return@withContext Result.success(null)
        val digits = rawPhoneNumber.filter { it.isDigit() }
        val mobileDouble = digits.toDoubleOrNull()
        val last10Double = if (digits.length >= 10) digits.takeLast(10).toDoubleOrNull() else null

        val lead = (last10Double?.let { leadMasterDao.getLeadMasterByMobile(it) })
            ?: (mobileDouble?.let { leadMasterDao.getLeadMasterByMobile(it) })

        if (lead != null) {
            Result.success(
                CrmLeadSummary(
                    externalLeadId = lead.id.toString(),
                    rawPhoneNumber = rawPhoneNumber,
                    displayName = lead.leadName,
                    journeyStage = lead.fkLeadStage?.toString(),
                    status = lead.fkLeadStatus?.toString(),
                    assignedAgent = null,
                    lastInteractionAt = null,
                    metadata = emptyMap()
                )
            )
        } else {
            Result.success(null)
        }
    }

    override fun observeLeadByPhone(rawPhoneNumber: String): Flow<CrmLeadSummary?> = flow {
        val result = findLeadByPhone(rawPhoneNumber)
        emit(result.getOrNull())
    }.flowOn(Dispatchers.IO)
}
