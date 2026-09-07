package com.example.callog.data.repository

import com.example.callog.data.local.dao.LeadDao
import com.example.callog.data.local.entity.LeadEntity
import com.example.callog.domain.model.Lead
import com.example.callog.domain.model.LeadPriority
import com.example.callog.domain.model.LeadSource
import com.example.callog.domain.model.LeadStatus
import com.example.callog.domain.repository.LeadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LeadRepositoryImpl @Inject constructor(
    private val leadDao: LeadDao
) : LeadRepository {

    override fun getLeadForPersonFlow(personId: String): Flow<Lead?> {
        return leadDao.getLeadByPersonIdFlow(personId).map { it?.toDomain() }
    }

    override suspend fun getLeadForPerson(personId: String): Lead? = withContext(Dispatchers.IO) {
        leadDao.getLeadByPersonId(personId)?.toDomain()
    }

    override suspend fun getLeadById(id: String): Lead? = withContext(Dispatchers.IO) {
        leadDao.getLeadById(id)?.toDomain()
    }

    override fun getAllLeadsFlow(): Flow<List<Lead>> {
        return leadDao.getAllLeadsFlow().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getAllLeads(): List<Lead> = withContext(Dispatchers.IO) {
        leadDao.getAllLeads().map { it.toDomain() }
    }

    override suspend fun saveLead(lead: Lead) = withContext(Dispatchers.IO) {
        leadDao.insertLead(lead.toEntity())
    }

    override suspend fun getOrCreateLeadForPerson(personId: String): Lead = withContext(Dispatchers.IO) {
        val existing = leadDao.getLeadByPersonId(personId)
        if (existing != null) {
            return@withContext existing.toDomain()
        }
        val newLead = Lead(
            id = "LEAD_" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase(),
            personId = personId,
            status = LeadStatus.UNKNOWN,
            priority = LeadPriority.MEDIUM,
            source = LeadSource.MANUAL,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING"
        )
        leadDao.insertLead(newLead.toEntity())
        newLead
    }

    override suspend fun updateLeadStatus(personId: String, status: LeadStatus) = withContext(Dispatchers.IO) {
        val existing = leadDao.getLeadByPersonId(personId)
        if (existing == null) {
            getOrCreateLeadForPerson(personId)
        }
        leadDao.updateStatus(personId, status.name)
    }

    override suspend fun updateLeadPriority(personId: String, priority: LeadPriority) = withContext(Dispatchers.IO) {
        val existing = leadDao.getLeadByPersonId(personId)
        if (existing == null) {
            getOrCreateLeadForPerson(personId)
        }
        leadDao.updatePriority(personId, priority.name)
    }

    override suspend fun updateLeadNotes(personId: String, notes: String) = withContext(Dispatchers.IO) {
        val existing = leadDao.getLeadByPersonId(personId)
        if (existing == null) {
            getOrCreateLeadForPerson(personId)
        }
        leadDao.updateNotes(personId, notes)
    }

    override suspend fun updateLeadFeedback(
        personId: String,
        feedback: String,
        rating: Int?
    ) = withContext(Dispatchers.IO) {
        val existing = leadDao.getLeadByPersonId(personId)
        if (existing == null) {
            getOrCreateLeadForPerson(personId)
        }
        leadDao.updateFeedback(personId, feedback, rating)
    }

    override suspend fun updateLeadFollowUp(personId: String, nextFollowUpAt: Long?) = withContext(Dispatchers.IO) {
        val existing = leadDao.getLeadByPersonId(personId)
        if (existing == null) {
            getOrCreateLeadForPerson(personId)
        }
        leadDao.updateFollowUp(personId, nextFollowUpAt)
    }

    override suspend fun archiveLead(personId: String) = withContext(Dispatchers.IO) {
        leadDao.archiveLead(personId)
    }
}

private fun LeadEntity.toDomain(): Lead {
    return Lead(
        id = id,
        personId = personId,
        status = LeadStatus.fromString(status),
        priority = LeadPriority.fromString(priority),
        feedback = feedback,
        feedbackRating = feedbackRating,
        notes = notes,
        ownerId = ownerId,
        source = LeadSource.fromString(source),
        nextFollowUpAt = nextFollowUpAt,
        isArchived = isArchived,
        archivedAt = archivedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = syncStatus
    )
}

private fun Lead.toEntity(): LeadEntity {
    return LeadEntity(
        id = id,
        personId = personId,
        status = status.name,
        priority = priority.name,
        feedback = feedback,
        feedbackRating = feedbackRating,
        notes = notes,
        ownerId = ownerId,
        source = source.name,
        nextFollowUpAt = nextFollowUpAt,
        isArchived = isArchived,
        archivedAt = archivedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = syncStatus
    )
}
