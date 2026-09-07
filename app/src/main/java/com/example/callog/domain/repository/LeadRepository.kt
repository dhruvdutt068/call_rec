package com.example.callog.domain.repository

import com.example.callog.domain.model.Lead
import com.example.callog.domain.model.LeadPriority
import com.example.callog.domain.model.LeadStatus
import kotlinx.coroutines.flow.Flow

interface LeadRepository {
    fun getLeadForPersonFlow(personId: String): Flow<Lead?>
    suspend fun getLeadForPerson(personId: String): Lead?
    suspend fun getLeadById(id: String): Lead?
    fun getAllLeadsFlow(): Flow<List<Lead>>
    suspend fun getAllLeads(): List<Lead>
    
    suspend fun saveLead(lead: Lead)
    suspend fun getOrCreateLeadForPerson(personId: String): Lead
    suspend fun updateLeadStatus(personId: String, status: LeadStatus)
    suspend fun updateLeadPriority(personId: String, priority: LeadPriority)
    suspend fun updateLeadNotes(personId: String, notes: String)
    suspend fun updateLeadFeedback(personId: String, feedback: String, rating: Int? = null)
    suspend fun updateLeadFollowUp(personId: String, nextFollowUpAt: Long?)
    suspend fun archiveLead(personId: String)
}
