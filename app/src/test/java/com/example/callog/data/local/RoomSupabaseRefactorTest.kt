package com.example.callog.data.local

import com.example.callog.data.crm.model.ClientCallsLogDto
import com.example.callog.data.local.entity.*
import com.example.callog.data.remote.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RoomSupabaseRefactorTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun testLeadMasterEntityMapping() {
        val remote = SupabaseLeadMaster(
            id = 101L,
            fkHashKey = "HASH123",
            leadName = "John Doe",
            leadMobile = 9876543210.0,
            fkLeadStatus = 1L,
            fkLeadStage = 2L,
            requirementCaptured = true,
            leadSource = "google_ad",
            createdAt = "2026-09-30T10:00:00Z",
            updatedAt = "2026-09-30T11:00:00Z"
        )

        val entity = LeadMasterEntity.fromRemote(remote, syncStatus = "SYNCED")
        assertEquals(101L, entity.id)
        assertEquals("John Doe", entity.leadName)
        assertEquals(9876543210.0, entity.leadMobile)
        assertEquals(2L, entity.fkLeadStage)
        assertEquals(true, entity.requirementCaptured)
        assertEquals("SYNCED", entity.syncStatus)

        val roundTrip = entity.toRemote()
        assertEquals(remote.id, roundTrip.id)
        assertEquals(remote.leadName, roundTrip.leadName)
        assertEquals(remote.leadMobile, roundTrip.leadMobile)
        assertEquals(remote.fkLeadStage, roundTrip.fkLeadStage)
    }

    @Test
    fun testLeadStageAndStatusMapping() {
        val stageRemote = SupabaseLeadStage(
            id = 1L,
            name = "New Lead",
            sortOrder = 1,
            isActive = true,
            kind = "pipeline"
        )
        val stageEntity = LeadStageEntity.fromRemote(stageRemote)
        assertEquals(1L, stageEntity.id)
        assertEquals("New Lead", stageEntity.name)
        assertEquals("pipeline", stageEntity.kind)

        val statusRemote = SupabaseLeadStatus(
            id = 4L,
            name = "Hot",
            sortOrder = 7,
            isActive = true,
            countsAsActive = false
        )
        val statusEntity = LeadStatusEntity.fromRemote(statusRemote)
        assertEquals(4L, statusEntity.id)
        assertEquals("Hot", statusEntity.name)
        assertFalse(statusEntity.countsAsActive)
    }

    @Test
    fun testCallsLogEntityMapping() {
        val dto = ClientCallsLogDto(
            phoneNumber = "+919876543210",
            leadName = "Jane Smith",
            journeyStage = "Site Visit Scheduled",
            botStatus = "active",
            bhk = "3 BHK",
            locality = "Downtown",
            budgetMin = 85.0,
            budgetMax = 120.0,
            requirementsComplete = true
        )
        val entity = CallsLogEntity.fromRemote(dto)
        assertEquals("+919876543210", entity.phoneNumber)
        assertEquals("Jane Smith", entity.leadName)
        assertEquals("3 BHK", entity.bhk)
        assertEquals(85.0, entity.budgetMin)
        assertEquals(true, entity.requirementsComplete)

        val roundTrip = entity.toRemote()
        assertEquals(dto.phoneNumber, roundTrip.phoneNumber)
        assertEquals(dto.leadName, roundTrip.leadName)
        assertEquals(dto.bhk, roundTrip.bhk)
    }

    @Test
    fun testLeadTaskAndNoteMapping() {
        val taskRemote = SupabaseLeadTask(
            id = 55L,
            leadId = 101L,
            taskType = "call",
            title = "Follow up with client",
            dueAt = "2026-10-01T10:00:00Z",
            status = "pending",
            source = "auto"
        )
        val taskEntity = LeadTaskEntity.fromRemote(taskRemote)
        assertEquals(55L, taskEntity.id)
        assertEquals(101L, taskEntity.leadId)
        assertEquals("Follow up with client", taskEntity.title)

        val noteRemote = SupabaseLeadNote(
            id = 88L,
            leadId = 101L,
            noteText = "Customer interested in 3BHK penthouse",
            authorName = "Agent Alice",
            authorRole = "sales"
        )
        val noteEntity = LeadNoteEntity.fromRemote(noteRemote)
        assertEquals(88L, noteEntity.id)
        assertEquals(101L, noteEntity.leadId)
        assertEquals("Agent Alice", noteEntity.authorName)
    }

    private fun readCleanJson(file: File): String = file.readText().trim().removePrefix("\uFEFF")

    @Test
    fun testParseDownloadedSupabaseDataFiles() {
        val dataDir = File("C:/Users/Dhruv/.gemini/antigravity-ide/brain/472398ff-b94f-4b0c-9cae-3afa6624c408/scratch/supabase_data")
        if (!dataDir.exists()) return

        // 1. Verify DEV lead stages
        val stagesFile = File(dataDir, "dev_lead_stages.json")
        if (stagesFile.exists()) {
            val stages = json.decodeFromString<List<SupabaseLeadStage>>(readCleanJson(stagesFile))
            assertTrue("Expected at least 10 lead stages from Supabase", stages.size >= 10)
            val entities = stages.map { LeadStageEntity.fromRemote(it) }
            assertEquals(stages.size, entities.size)
        }

        // 2. Verify DEV lead statuses
        val statusesFile = File(dataDir, "dev_lead_statuses.json")
        if (statusesFile.exists()) {
            val statuses = json.decodeFromString<List<SupabaseLeadStatus>>(readCleanJson(statusesFile))
            assertTrue("Expected statuses from Supabase", statuses.isNotEmpty())
            val entities = statuses.map { LeadStatusEntity.fromRemote(it) }
            assertEquals(statuses.size, entities.size)
        }

        // 3. Verify DEV calls_log
        val callsLogFile = File(dataDir, "dev_calls_log.json")
        if (callsLogFile.exists()) {
            val logs = json.decodeFromString<List<ClientCallsLogDto>>(readCleanJson(callsLogFile))
            assertTrue("Expected calls_log from Supabase", logs.isNotEmpty())
            val entities = logs.map { CallsLogEntity.fromRemote(it) }
            assertEquals(logs.size, entities.size)
        }

        // 4. Verify DEV leads_master
        val leadsFile = File(dataDir, "dev_leads_master.json")
        if (leadsFile.exists()) {
            val leads = json.decodeFromString<List<SupabaseLeadMaster>>(readCleanJson(leadsFile))
            assertTrue("Expected leads_master from Supabase", leads.isNotEmpty())
            val entities = leads.map { LeadMasterEntity.fromRemote(it) }
            assertEquals(leads.size, entities.size)
        }
    }
}
