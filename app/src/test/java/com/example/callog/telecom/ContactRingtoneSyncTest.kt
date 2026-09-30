package com.example.callog.telecom

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.net.FakeUri
import com.example.callog.data.local.dao.LeadMasterDao
import com.example.callog.data.local.dao.LeadStatusDao
import com.example.callog.data.local.entity.LeadMasterEntity
import com.example.callog.data.local.entity.LeadStatusEntity
import com.example.callog.data.provider.ContactRingtoneSyncManager
import com.example.callog.data.telecom.MediaStoreRingtoneExporter
import com.example.callog.domain.call.CallCapabilities
import com.example.callog.domain.call.CallDirection
import com.example.callog.domain.call.CallState
import com.example.callog.domain.model.LeadPriority
import com.example.callog.domain.model.LeadStatus
import com.example.callog.domain.model.Person
import com.example.callog.domain.repository.RingtoneRepository
import com.example.callog.domain.service.CallSessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ContactRingtoneSyncTest {

    private lateinit var fakeContext: Context
    private lateinit var fakeRingtoneRepo: FakeRingtoneRepository
    private lateinit var fakeLeadStatusDao: FakeLeadStatusDao
    private lateinit var fakeLeadMasterDao: FakeLeadMasterDao
    private lateinit var mediaStoreExporter: MediaStoreRingtoneExporter
    private lateinit var syncManager: ContactRingtoneSyncManager
    private lateinit var fakeRingtoneController: CallSessionManagerTest.FakeRingtoneController
    private lateinit var callSessionManager: CallSessionManager

    private fun createFakeContext(): Context {
        return object : ContextWrapper(null) {
            override fun getPackageName(): String = "com.example.callog"
            override fun startActivity(intent: Intent?) {}
            override fun getApplicationContext(): Context = this
        }
    }

    @Before
    fun setUp() {
        fakeContext = createFakeContext()
        fakeRingtoneRepo = FakeRingtoneRepository()
        fakeLeadStatusDao = FakeLeadStatusDao()
        fakeLeadMasterDao = FakeLeadMasterDao()

        mediaStoreExporter = MediaStoreRingtoneExporter(fakeContext, fakeRingtoneRepo)
        syncManager = ContactRingtoneSyncManager(fakeContext, mediaStoreExporter, fakeLeadStatusDao)

        fakeRingtoneController = CallSessionManagerTest.FakeRingtoneController(fakeContext)

        val fakePersonRepo = CallSessionManagerTest.FakePersonRepository()
        val fakeLeadRepo = CallSessionManagerTest.FakeLeadRepository()
        val fakeCallRepo = CallSessionManagerTest.FakeCallRepository()
        val fakeSimRepo = CallSessionManagerTest.FakeSimRepository(fakeContext)
        val fakeAudioCtrl = com.example.callog.core.telecom.CallAudioController(fakeContext)
        val fakeProximityCtrl = com.example.callog.core.telecom.ProximityController(fakeContext)
        val fakeNotificationMgr = CallSessionManagerTest.FakeCallNotificationManager(fakeContext)
        val fakeHapticMgr = CallSessionManagerTest.FakeCallHapticManager(fakeContext)
        val fakeDtmfPlayer = CallSessionManagerTest.FakeDtmfTonePlayer()

        callSessionManager = CallSessionManager(
            context = fakeContext,
            personRepository = fakePersonRepo,
            leadRepository = fakeLeadRepo,
            callRepository = fakeCallRepo,
            simRepository = fakeSimRepo,
            ringtoneController = fakeRingtoneController,
            callAudioController = fakeAudioCtrl,
            proximityController = fakeProximityCtrl,
            notificationManager = fakeNotificationMgr,
            callHapticManager = fakeHapticMgr,
            dtmfTonePlayer = fakeDtmfPlayer,
            contactsProvider = com.example.callog.data.provider.ContactsProvider(fakeContext),
            dialerRoleManager = com.example.callog.domain.service.DialerRoleManager(fakeContext),
            callRecordingController = CallSessionManagerTest.FakeCallRecordingController(),
            recordingRepository = CallSessionManagerTest.FakeRecordingRepository(),
            contactRingtoneSyncManager = syncManager,
            leadMasterDao = fakeLeadMasterDao,
            crmRepository = null
        )
    }

    // ─────────────────────────────────────────────────────────────
    // 1. Status Mapping Verification
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testLeadStatusResolution() = runBlocking {
        fakeLeadStatusDao.insertLeadStatus(LeadStatusEntity(id = 1L, name = "HOT"))
        fakeLeadStatusDao.insertLeadStatus(LeadStatusEntity(id = 2L, name = "WARM"))
        fakeLeadStatusDao.insertLeadStatus(LeadStatusEntity(id = 3L, name = "COLD"))

        assertEquals(LeadStatus.HOT, syncManager.resolveLeadStatus(1L, null))
        assertEquals(LeadStatus.WARM, syncManager.resolveLeadStatus(2L, null))
        assertEquals(LeadStatus.COLD, syncManager.resolveLeadStatus(3L, null))
        assertEquals(LeadStatus.UNKNOWN, syncManager.resolveLeadStatus(null, null))
    }

    // ─────────────────────────────────────────────────────────────
    // 2. MediaStore Exporter Fallback Logic
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testMediaStoreExporterReturnsDefaultWhenNoCustomFile() {
        val uri = mediaStoreExporter.getExportedRingtoneUri(LeadStatus.HOT)
        assertNotNull(uri)
        assertEquals(fakeRingtoneRepo.default(), uri)
    }

    // ─────────────────────────────────────────────────────────────
    // 3. Unknown Number that IS a CRM Lead plays status ringtone
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testUnknownNumberMatchingLeadMasterPlaysLeadRingtone() = runBlocking {
        // Seed LeadMaster in Room
        val leadEntity = LeadMasterEntity(
            id = 501L,
            leadName = "Vikram Patel",
            leadMobile = 9988776655.0,
            fkLeadStatus = 1L // HOT
        )
        fakeLeadMasterDao.insertLeadMaster(leadEntity)
        fakeLeadStatusDao.insertLeadStatus(LeadStatusEntity(id = 1L, name = "HOT"))

        callSessionManager.onTelecomCallAdded(
            callId = "call_crm_unknown",
            rawNumber = "+91 99887 76655",
            telecomDisplayName = "Vikram Patel",
            direction = CallDirection.INCOMING,
            initialState = CallState.RINGING,
            capabilities = CallCapabilities(),
            accountHandleId = null,
            accountComponentName = null
        )

        kotlinx.coroutines.delay(100)

        // Verifies ringtone played for the unknown caller because they are a CRM lead!
        assertTrue("Ringtone must start for unknown caller who is a CRM lead", fakeRingtoneController.started)
        assertEquals("call_crm_unknown", fakeRingtoneController.lastCallId)
        assertEquals("Vikram Patel", fakeRingtoneController.lastPerson?.displayName)
        assertEquals(LeadStatus.HOT, fakeRingtoneController.lastLead?.status)
    }

    // ─────────────────────────────────────────────────────────────
    // 4. Unknown Caller who is NOT a CRM Lead does NOT ring in-app
    //    (Delegating to Samsung's native default ringer cleanly)
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testUnknownCallerNonCrmDoesNotPlayInAppRingtone() = runBlocking {
        callSessionManager.onTelecomCallAdded(
            callId = "call_spam_unknown",
            rawNumber = "+91 11223 34455",
            telecomDisplayName = "Spam Unknown",
            direction = CallDirection.INCOMING,
            initialState = CallState.RINGING,
            capabilities = CallCapabilities(),
            accountHandleId = null,
            accountComponentName = null
        )

        kotlinx.coroutines.delay(100)

        // Verifies app suppresses in-app audio so Samsung default ringtone rings alone with no dual-ring race!
        assertFalse(
            "Non-CRM unknown caller must NOT trigger in-app ringtone to prevent dual-ringing against Samsung system ringer",
            fakeRingtoneController.started
        )
    }

    // ─────────────────────────────────────────────────────────────
    // Fakes
    // ─────────────────────────────────────────────────────────────
    class FakeRingtoneRepository : RingtoneRepository {
        var hotUri: Uri? = null
        var warmUri: Uri? = null
        var coldUri: Uri? = null
        var defaultUri: Uri = FakeUri("content://media/internal/audio/media/default")

        override fun hotLead(): Uri? = hotUri
        override fun warmLead(): Uri? = warmUri
        override fun coldLead(): Uri? = coldUri
        override fun newLead(): Uri? = null
        override fun customer(): Uri? = null
        override fun personSpecific(personId: String): Uri? = null
        override fun default(): Uri = defaultUri

        override fun setRingtoneForPerson(personId: String, uri: Uri?) {}
        override fun setRingtoneForLeadHot(uri: Uri?) { hotUri = uri }
        override fun setRingtoneForLeadWarm(uri: Uri?) { warmUri = uri }
        override fun setRingtoneForLeadCold(uri: Uri?) { coldUri = uri }
        override fun setRingtoneForCustomer(uri: Uri?) {}
    }

    class FakeLeadStatusDao : LeadStatusDao {
        val statuses = mutableListOf<LeadStatusEntity>()

        override suspend fun insertLeadStatus(status: LeadStatusEntity) { statuses.add(status) }
        override suspend fun insertLeadStatuses(statuses: List<LeadStatusEntity>) { this.statuses.addAll(statuses) }
        override fun getAllLeadStatusesFlow(): Flow<List<LeadStatusEntity>> = flowOf(statuses)
        override suspend fun getAllLeadStatuses(): List<LeadStatusEntity> = statuses
        override suspend fun clearAll() { statuses.clear() }
    }

    class FakeLeadMasterDao : LeadMasterDao {
        val leads = mutableListOf<LeadMasterEntity>()

        override suspend fun insertLeadMaster(lead: LeadMasterEntity) { leads.add(lead) }
        override suspend fun insertLeadsMaster(leads: List<LeadMasterEntity>) { this.leads.addAll(leads) }
        override suspend fun updateLeadMaster(lead: LeadMasterEntity) {
            leads.removeAll { it.id == lead.id }
            leads.add(lead)
        }
        override suspend fun getLeadMasterById(id: Long): LeadMasterEntity? = leads.find { it.id == id }
        override fun getLeadMasterByIdFlow(id: Long): Flow<LeadMasterEntity?> = flowOf(leads.find { it.id == id })
        override fun getAllLeadsMasterFlow(): Flow<List<LeadMasterEntity>> = flowOf(leads)
        override suspend fun getAllLeadsMaster(): List<LeadMasterEntity> = leads
        override fun getLeadsMasterByStageFlow(stageId: Long): Flow<List<LeadMasterEntity>> = flowOf(leads.filter { it.fkLeadStage == stageId })
        override suspend fun getLeadsMasterByStage(stageId: Long): List<LeadMasterEntity> = leads.filter { it.fkLeadStage == stageId }
        override suspend fun updateLeadStage(id: Long, newStageId: Long, updatedAt: String, syncStatus: String) {}
        override suspend fun getLeadMasterByMobile(mobile: Double): LeadMasterEntity? = leads.find { it.leadMobile == mobile }
        override suspend fun deleteLeadMaster(id: Long) { leads.removeAll { it.id == id } }
        override suspend fun clearAll() { leads.clear() }
    }
}
