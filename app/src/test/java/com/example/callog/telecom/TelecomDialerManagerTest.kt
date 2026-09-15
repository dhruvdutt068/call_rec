package com.example.callog.telecom

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.example.callog.core.telecom.TelecomDialerManager
import com.example.callog.sim.SimInfo
import com.example.callog.sim.SimManager
import com.example.callog.sim.SimRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TelecomDialerManagerTest {

    private lateinit var dialerManager: TelecomDialerManager
    private lateinit var fakeSimRepository: FakeSimRepository
    private lateinit var fakeContext: Context

    private var startedIntent: Intent? = null

    @Before
    fun setUp() {
        fakeContext = object : ContextWrapper(null) {
            override fun getPackageName(): String = "com.example.callog"
            override fun getApplicationContext(): Context = this
            override fun startActivity(intent: Intent?) {
                startedIntent = intent
            }
        }

        fakeSimRepository = FakeSimRepository(fakeContext)
        fakeSimRepository.sims.addAll(
            listOf(
                SimInfo(subscriptionId = 1, slotIndex = 0, carrierName = "Jio 5G", displayName = "SIM 1", phoneNumber = "9876543210", isActive = true),
                SimInfo(subscriptionId = 2, slotIndex = 1, carrierName = "Airtel", displayName = "SIM 2", phoneNumber = "8888822222", isActive = true)
            )
        )

        dialerManager = object : TelecomDialerManager(fakeContext, fakeSimRepository) {
            override fun hasCallPhonePermission(): Boolean = true
        }
    }

    @Test
    fun testGetSimAccountMappings_returnsAllSims() {
        val mappings = dialerManager.getSimAccountMappings()
        assertEquals(2, mappings.size)
        assertEquals("Jio 5G", mappings[0].simInfo.carrierName)
        assertEquals("Airtel", mappings[1].simInfo.carrierName)
    }

    @Test
    fun testPlaceCall_withBlankNumber_fails() {
        val result = dialerManager.placeCall("   ", null)
        assertTrue(result.isFailure)
    }

    @Test
    fun testPlaceCall_withValidNumber_succeeds() {
        val sim1 = fakeSimRepository.sims[0]
        val result = dialerManager.placeCall("+91 98765 43210", sim1)
        assertTrue(result.isSuccess)
    }

    class FakeSimRepository(context: Context) : SimRepository(SimManager(context)) {
        val sims = mutableListOf<SimInfo>()
        override fun getInstalledSims(): List<SimInfo> = sims
    }
}
