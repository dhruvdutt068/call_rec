package com.example.callog.data.repository

import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.data.local.dao.LeadDao
import com.example.callog.data.local.dao.PersonDao
import com.example.callog.data.local.entity.LeadEntity
import com.example.callog.data.remote.SupabaseService
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
    private val leadDao: LeadDao,
    private val personDao: PersonDao? = null,
    private val supabaseService: SupabaseService? = null
) : LeadRepository {

    override fun getLeadForPersonFlow(personId: String): Flow<Lead?> {
        return leadDao.getLeadByPersonIdFlow(personId).map { it?.toDomain() }
    }

    override suspend fun getLeadForPerson(personId: String): Lead? = withContext(Dispatchers.IO) {
        val local = leadDao.getLeadByPersonId(personId)
        if (local != null) {
            return@withContext local.toDomain()
        }
        // If not found locally, try fetching from Supabase
        if (supabaseService != null) {
            try {
                val remoteLead = supabaseService.fetchLeadByPersonId(personId).getOrNull()
                if (remoteLead != null) {
                    val restoredEntity = LeadEntity(
                        id = remoteLead.id,
                        personId = remoteLead.personId,
                        status = remoteLead.status.uppercase(),
                        priority = remoteLead.priority.uppercase(),
                        feedback = remoteLead.feedback,
                        feedbackRating = remoteLead.feedbackRating,
                        notes = remoteLead.notes,
                        ownerId = remoteLead.ownerId,
                        source = remoteLead.source.uppercase(),
                        nextFollowUpAt = remoteLead.nextFollowUpAt?.let { parseTimestamp(it) },
                        isArchived = remoteLead.isArchived,
                        archivedAt = remoteLead.archivedAt?.let { parseTimestamp(it) },
                        createdAt = parseTimestamp(remoteLead.createdAt),
                        updatedAt = parseTimestamp(remoteLead.updatedAt),
                        syncStatus = "SYNCED"
                    )
                    leadDao.insertLead(restoredEntity)
                    DeveloperLogger.info(
                        "LEAD_STATUS_RESTORED",
                        "Restored lead ${restoredEntity.id} (status=${restoredEntity.status}) from Supabase for person $personId"
                    )
                    return@withContext restoredEntity.toDomain()
                }
            } catch (e: Exception) {
                DeveloperLogger.warning("LEAD_FETCH_FAILED", "Failed fetching lead for $personId from Supabase: ${e.message}")
            }
        }
        null
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
        val entity = lead.toEntity()
        leadDao.insertLead(entity)
        DeveloperLogger.info(
            "ROOM_STATUS_UPDATED",
            "Saved lead ${lead.id} to Room with status=${lead.status.name}, updatedAt=${entity.updatedAt}"
        )
        syncLeadToSupabase(lead.personId, entity.updatedAt)
    }

    override suspend fun getOrCreateLeadForPerson(personId: String): Lead = withContext(Dispatchers.IO) {
        val existing = leadDao.getLeadByPersonId(personId)
        if (existing != null) {
            return@withContext existing.toDomain()
        }

        // If not in local Room DB (e.g. fresh install / cleared cache), check Supabase first
        if (supabaseService != null) {
            try {
                val remoteLead = supabaseService.fetchLeadByPersonId(personId).getOrNull()
                if (remoteLead != null) {
                    val restoredEntity = LeadEntity(
                        id = remoteLead.id,
                        personId = remoteLead.personId,
                        status = remoteLead.status.uppercase(),
                        priority = remoteLead.priority.uppercase(),
                        feedback = remoteLead.feedback,
                        feedbackRating = remoteLead.feedbackRating,
                        notes = remoteLead.notes,
                        ownerId = remoteLead.ownerId,
                        source = remoteLead.source.uppercase(),
                        nextFollowUpAt = remoteLead.nextFollowUpAt?.let { parseTimestamp(it) },
                        isArchived = remoteLead.isArchived,
                        archivedAt = remoteLead.archivedAt?.let { parseTimestamp(it) },
                        createdAt = parseTimestamp(remoteLead.createdAt),
                        updatedAt = parseTimestamp(remoteLead.updatedAt),
                        syncStatus = "SYNCED"
                    )
                    leadDao.insertLead(restoredEntity)
                    DeveloperLogger.info(
                        "LEAD_STATUS_RESTORED",
                        "Restored lead ${restoredEntity.id} (status=${restoredEntity.status}) from Supabase for person $personId"
                    )
                    return@withContext restoredEntity.toDomain()
                }
            } catch (e: Exception) {
                DeveloperLogger.warning("LEAD_FETCH_FAILED", "Failed fetching remote lead for $personId: ${e.message}")
            }
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
        val entity = newLead.toEntity()
        leadDao.insertLead(entity)
        DeveloperLogger.info(
            "LEAD_CREATED",
            "Created local lead ${newLead.id} for person $personId (status=${newLead.status.name})"
        )
        syncLeadToSupabase(personId, entity.updatedAt)
        newLead
    }

    override suspend fun updateLeadStatus(personId: String, status: LeadStatus) = withContext(Dispatchers.IO) {
        val existing = leadDao.getLeadByPersonId(personId) ?: run {
            val created = getOrCreateLeadForPerson(personId)
            created.toEntity()
        }
        val previousStatus = existing.status
        val now = System.currentTimeMillis()

        DeveloperLogger.info(
            "LEAD_STATUS_CHANGED_LOCAL",
            "Lead ${existing.id} for person $personId changing status from $previousStatus to ${status.name}"
        )

        leadDao.updateStatus(personId, status.name, now)
        DeveloperLogger.info(
            "ROOM_STATUS_UPDATED",
            "Room updated lead ${existing.id} status=${status.name}, updatedAt=$now, syncStatus=PENDING"
        )
        DeveloperLogger.info(
            "LEAD_UPDATE_QUEUED",
            "Lead ${existing.id} status update queued for synchronization"
        )

        syncLeadToSupabase(personId, now)
    }

    override suspend fun updateLeadPriority(personId: String, priority: LeadPriority) = withContext(Dispatchers.IO) {
        val existing = leadDao.getLeadByPersonId(personId) ?: run {
            val created = getOrCreateLeadForPerson(personId)
            created.toEntity()
        }
        val now = System.currentTimeMillis()
        leadDao.updatePriority(personId, priority.name, now)
        syncLeadToSupabase(personId, now)
    }

    override suspend fun updateLeadNotes(personId: String, notes: String) = withContext(Dispatchers.IO) {
        val existing = leadDao.getLeadByPersonId(personId) ?: run {
            val created = getOrCreateLeadForPerson(personId)
            created.toEntity()
        }
        val now = System.currentTimeMillis()
        leadDao.updateNotes(personId, notes, now)
        syncLeadToSupabase(personId, now)
    }

    override suspend fun updateLeadFeedback(
        personId: String,
        feedback: String,
        rating: Int?
    ) = withContext(Dispatchers.IO) {
        val existing = leadDao.getLeadByPersonId(personId) ?: run {
            val created = getOrCreateLeadForPerson(personId)
            created.toEntity()
        }
        val now = System.currentTimeMillis()
        leadDao.updateFeedback(personId, feedback, rating, now)
        syncLeadToSupabase(personId, now)
    }

    override suspend fun updateLeadFollowUp(personId: String, nextFollowUpAt: Long?) = withContext(Dispatchers.IO) {
        val existing = leadDao.getLeadByPersonId(personId) ?: run {
            val created = getOrCreateLeadForPerson(personId)
            created.toEntity()
        }
        val now = System.currentTimeMillis()
        leadDao.updateFollowUp(personId, nextFollowUpAt, now)
        syncLeadToSupabase(personId, now)
    }

    override suspend fun archiveLead(personId: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        leadDao.archiveLead(personId, now, now)
        syncLeadToSupabase(personId, now)
    }

    private suspend fun syncLeadToSupabase(personId: String, expectedUpdatedAt: Long? = null) {
        val lead = leadDao.getLeadByPersonId(personId) ?: return
        if (supabaseService == null) return

        try {
            DeveloperLogger.info(
                "SUPABASE_LEAD_UPDATE_STARTED",
                "Starting Supabase update for lead ${lead.id} (status=${lead.status}, updatedAt=${lead.updatedAt})"
            )
            val startTime = System.currentTimeMillis()
            val result = supabaseService.syncLeads(listOf(lead))
            val duration = System.currentTimeMillis() - startTime

            if (result.isSuccess) {
                // Ensure local record has not been updated concurrently before marking SYNCED
                val targetTime = expectedUpdatedAt ?: lead.updatedAt
                val rowsUpdated = leadDao.updateSyncStatusIfTimestampMatches(lead.id, "SYNCED", targetTime)
                if (rowsUpdated > 0) {
                    DeveloperLogger.success(
                        "SUPABASE_LEAD_UPDATE_SUCCESS",
                        "Successfully synced lead ${lead.id} to Supabase in ${duration}ms",
                        durationMs = duration
                    )
                } else {
                    DeveloperLogger.warning(
                        "LEAD_CONFLICT_DETECTED",
                        "Lead ${lead.id} was updated locally while sync was in flight. Retaining PENDING status."
                    )
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Unknown error"
                DeveloperLogger.error(
                    "SUPABASE_LEAD_UPDATE_FAILED",
                    "Supabase update failed for lead ${lead.id}: $err. Retaining PENDING for background retry.",
                    exception = result.exceptionOrNull()
                )
            }
        } catch (e: Exception) {
            DeveloperLogger.error(
                "SUPABASE_LEAD_UPDATE_FAILED",
                "Exception syncing lead ${lead.id} to Supabase: ${e.message}",
                exception = e
            )
        }
    }

    private fun parseTimestamp(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            sdf.parse(isoString)?.time ?: isoString.toLongOrNull() ?: System.currentTimeMillis()
        } catch (e: Exception) {
            try {
                val altSdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                altSdf.parse(isoString)?.time ?: isoString.toLongOrNull() ?: System.currentTimeMillis()
            } catch (e2: Exception) {
                isoString.toLongOrNull() ?: System.currentTimeMillis()
            }
        }
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
