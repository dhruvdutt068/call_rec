package com.example.callog.telecom

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.example.callog.data.telecom.CallRingtoneManager
import com.example.callog.data.telecom.RingtoneType
import com.example.callog.domain.model.Lead
import com.example.callog.domain.model.LeadPriority
import com.example.callog.domain.model.LeadStatus
import com.example.callog.domain.model.Person
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class CallRingtoneManagerTest {

    private lateinit var ringtoneManager: CallRingtoneManager
    private val inMemoryPrefs = mutableMapOf<String, Any?>()

    @Before
    fun setUp() {
        val fakeSharedPrefs = createFakeSharedPreferences(inMemoryPrefs)
        val fakeContext = createFakeContext(fakeSharedPrefs)

        ringtoneManager = CallRingtoneManager(fakeContext)
    }

    // ─────────────────────────────────────────────────────────────
    // Priority 1: Person-specific ringtone
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testPriority1_PersonSpecificRingtoneTakesPrecedence() {
        val person = Person(id = "person_vip_1", displayName = "Dr. Mehta")
        val lead = Lead(
            id = "lead_1",
            personId = "person_vip_1",
            status = LeadStatus.HOT,
            priority = LeadPriority.URGENT
        )

        // Store custom ringtone for person_vip_1
        inMemoryPrefs["ringtone_person_person_vip_1"] = "content://media/internal/audio/media/42"

        val resolved = ringtoneManager.resolveRingtoneType(person, lead)
        assertEquals(RingtoneType.PERSON_SPECIFIC, resolved)
    }

    // ─────────────────────────────────────────────────────────────
    // Priority 2: CRM Priority
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testPriority2_CrmPriorityTakesPrecedenceOverLeadStatus() {
        val person = Person(id = "person_2", displayName = "Neha Singh")
        val leadUrgent = Lead(
            id = "lead_2",
            personId = "person_2",
            status = LeadStatus.HOT,
            priority = LeadPriority.URGENT
        )

        val resolvedUrgent = ringtoneManager.resolveRingtoneType(person, leadUrgent)
        assertEquals(RingtoneType.CRM_PRIORITY, resolvedUrgent)

        val leadHigh = Lead(
            id = "lead_3",
            personId = "person_2",
            status = LeadStatus.WARM,
            priority = LeadPriority.HIGH
        )
        val resolvedHigh = ringtoneManager.resolveRingtoneType(person, leadHigh)
        assertEquals(RingtoneType.CRM_PRIORITY, resolvedHigh)
    }

    // ─────────────────────────────────────────────────────────────
    // Priority 3: Lead Status (HOT > WARM)
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testPriority3_LeadStatusHotAndWarm() {
        val person = Person(id = "person_3", displayName = "Vikram Aditya")
        val leadHot = Lead(
            id = "lead_hot",
            personId = "person_3",
            status = LeadStatus.HOT,
            priority = LeadPriority.MEDIUM
        )
        val resolvedHot = ringtoneManager.resolveRingtoneType(person, leadHot)
        assertEquals(RingtoneType.LEAD_STATUS_HOT, resolvedHot)

        val leadWarm = Lead(
            id = "lead_warm",
            personId = "person_3",
            status = LeadStatus.WARM,
            priority = LeadPriority.MEDIUM
        )
        val resolvedWarm = ringtoneManager.resolveRingtoneType(person, leadWarm)
        assertEquals(RingtoneType.LEAD_STATUS_WARM, resolvedWarm)
    }

    // ─────────────────────────────────────────────────────────────
    // Priority 4: Customer Type
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testPriority4_CustomerTypeIdentification() {
        // Customer via notes/feedback tag
        val person = Person(id = "person_4", displayName = "Ananya Roy")
        val leadCustomer = Lead(
            id = "lead_cust",
            personId = "person_4",
            status = LeadStatus.COLD,
            priority = LeadPriority.LOW,
            notes = "Active Customer since 2024"
        )
        val resolvedCustomer = ringtoneManager.resolveRingtoneType(person, leadCustomer)
        assertEquals(RingtoneType.CUSTOMER_TYPE, resolvedCustomer)

        // Customer via company association
        val personWithCompany = Person(
            id = "person_corp",
            displayName = "Arjun Kapoor",
            companyName = "Tata Consultancy Services"
        )
        val resolvedCompany = ringtoneManager.resolveRingtoneType(personWithCompany, null)
        assertEquals(RingtoneType.CUSTOMER_TYPE, resolvedCompany)
    }

    // ─────────────────────────────────────────────────────────────
    // Priority 5: Default Ringtone Fallback
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testPriority5_DefaultFallbackForUnknownOrUnconfigured() {
        // Unknown caller (no person, no lead)
        val resolvedUnknown = ringtoneManager.resolveRingtoneType(null, null)
        assertEquals(RingtoneType.DEFAULT, resolvedUnknown)

        // Person with default lead status and no company/custom tone
        val regularPerson = Person(id = "person_reg", displayName = "Unknown Person")
        val regularLead = Lead(
            id = "lead_reg",
            personId = "person_reg",
            status = LeadStatus.UNKNOWN,
            priority = LeadPriority.MEDIUM
        )
        val resolvedRegular = ringtoneManager.resolveRingtoneType(regularPerson, regularLead)
        assertEquals(RingtoneType.DEFAULT, resolvedRegular)
    }

    // ─────────────────────────────────────────────────────────────
    // Ringtone Stop & Cleanup Verification
    // ─────────────────────────────────────────────────────────────
    @Test
    fun testStopRingtoneIsSafeAndResetsState() {
        assertFalse(ringtoneManager.isRingtonePlaying)

        // Call stop repeatedly to verify idempotency and safe cleanup
        ringtoneManager.stopRingtone()
        assertFalse(ringtoneManager.isRingtonePlaying)

        ringtoneManager.stopRingtone()
        assertFalse(ringtoneManager.isRingtonePlaying)
    }

    // ── Fake Context and SharedPreferences helpers ───────────────

    private fun createFakeContext(sharedPreferences: SharedPreferences): Context {
        return object : ContextWrapper(null) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = sharedPreferences
            override fun getSystemService(name: String): Any? = null
            override fun getPackageName(): String = "com.example.callog"
            override fun getApplicationContext(): Context = this
        }
    }

    private fun createFakeSharedPreferences(map: MutableMap<String, Any?>): SharedPreferences {
        val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "putString" -> {
                    map[args[0] as String] = args[1]
                    proxy
                }
                "remove" -> {
                    map.remove(args[0] as String)
                    proxy
                }
                "apply", "commit" -> true
                else -> proxy
            }
        } as SharedPreferences.Editor

        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> {
                    val key = args[0] as String
                    val def = args.getOrNull(1) as? String
                    map[key] as? String ?: def
                }
                "edit" -> editor
                else -> null
            }
        } as SharedPreferences
    }
}
