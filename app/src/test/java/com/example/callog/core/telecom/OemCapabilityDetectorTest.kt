package com.example.callog.core.telecom

import android.content.Context
import android.content.ContextWrapper
import com.example.callog.domain.model.OemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OemCapabilityDetectorTest {

    private lateinit var detector: OemCapabilityDetector

    @Before
    fun setUp() {
        val fakeContext = object : ContextWrapper(null) {
            override fun getPackageName(): String = "com.example.callog"
            override fun getApplicationContext(): Context = this
        }
        detector = OemCapabilityDetector(fakeContext)
    }

    @Test
    fun `resolveOemType accurately categorizes manufacturers and brands`() {
        assertEquals(OemType.SAMSUNG, detector.resolveOemType("samsung", "samsung"))
        assertEquals(OemType.SAMSUNG, detector.resolveOemType("SAMSUNG", "Galaxy"))

        assertEquals(OemType.XIAOMI, detector.resolveOemType("Xiaomi", "Redmi"))
        assertEquals(OemType.XIAOMI, detector.resolveOemType("Xiaomi", "POCO"))

        assertEquals(OemType.ONEPLUS, detector.resolveOemType("OnePlus", "OnePlus"))

        assertEquals(OemType.OPPO, detector.resolveOemType("OPPO", "OPPO"))

        assertEquals(OemType.REALME, detector.resolveOemType("realme", "realme"))

        assertEquals(OemType.VIVO, detector.resolveOemType("vivo", "iQOO"))

        assertEquals(OemType.GOOGLE_PIXEL, detector.resolveOemType("Google", "google"))

        assertEquals(OemType.OTHER, detector.resolveOemType("Motorola", "moto"))
        assertEquals(OemType.OTHER, detector.resolveOemType("Unknown", "Generic"))
    }

    @Test
    fun `getInstructionsForOem provides actionable guidance for supported OEMs`() {
        val samsungSteps = detector.getInstructionsForOem(OemType.SAMSUNG)
        assertTrue(samsungSteps.isNotEmpty())
        assertTrue(samsungSteps.any { it.contains("Auto record calls", ignoreCase = true) })

        val xiaomiSteps = detector.getInstructionsForOem(OemType.XIAOMI)
        assertTrue(xiaomiSteps.isNotEmpty())
        assertTrue(xiaomiSteps.any { it.contains("Record calls automatically", ignoreCase = true) })

        val pixelSteps = detector.getInstructionsForOem(OemType.GOOGLE_PIXEL)
        assertTrue(pixelSteps.any { it.contains("limits automatic 2-way recording", ignoreCase = true) })
    }
}
