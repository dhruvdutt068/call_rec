package com.example.callog.telecom

import com.example.callog.core.telecom.T9SearchEngine
import com.example.callog.domain.model.Lead
import com.example.callog.domain.model.LeadPriority
import com.example.callog.domain.model.LeadStatus
import com.example.callog.domain.model.Person
import com.example.callog.domain.model.PhoneNumber
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class T9SearchEngineTest {

    private lateinit var t9SearchEngine: T9SearchEngine
    private lateinit var samplePeople: List<Person>
    private lateinit var sampleLeadsMap: Map<String, Lead>

    @Before
    fun setUp() {
        t9SearchEngine = T9SearchEngine()

        val person1 = Person(
            id = "p1",
            displayName = "Rahul Sharma",
            companyName = "Apex Solutions",
            phoneNumbers = listOf(
                PhoneNumber("pn1", "p1", "+91 98765 43210", "9876543210")
            )
        )

        val person2 = Person(
            id = "p2",
            displayName = "Amit Verma",
            companyName = "Zenith Corp",
            phoneNumbers = listOf(
                PhoneNumber("pn2", "p2", "+91 88888 22222", "8888822222")
            )
        )

        val person3 = Person(
            id = "p3",
            displayName = "Neha Patel",
            companyName = "Nexus Enterprises",
            phoneNumbers = listOf(
                PhoneNumber("pn3", "p3", "+91 77777 98765", "7777798765")
            )
        )

        samplePeople = listOf(person1, person2, person3)

        sampleLeadsMap = mapOf(
            "p1" to Lead("l1", "p1", status = LeadStatus.HOT, priority = LeadPriority.URGENT),
            "p2" to Lead("l2", "p2", status = LeadStatus.WARM, priority = LeadPriority.MEDIUM),
            "p3" to Lead("l3", "p3", status = LeadStatus.COLD, priority = LeadPriority.LOW)
        )
    }

    @Test
    fun testCharToT9Digit() {
        assertEquals('2', T9SearchEngine.charToT9Digit('a'))
        assertEquals('2', T9SearchEngine.charToT9Digit('B'))
        assertEquals('2', T9SearchEngine.charToT9Digit('c'))
        assertEquals('3', T9SearchEngine.charToT9Digit('D'))
        assertEquals('3', T9SearchEngine.charToT9Digit('e'))
        assertEquals('4', T9SearchEngine.charToT9Digit('G'))
        assertEquals('5', T9SearchEngine.charToT9Digit('k'))
        assertEquals('6', T9SearchEngine.charToT9Digit('o'))
        assertEquals('7', T9SearchEngine.charToT9Digit('r'))
        assertEquals('7', T9SearchEngine.charToT9Digit('S'))
        assertEquals('8', T9SearchEngine.charToT9Digit('t'))
        assertEquals('9', T9SearchEngine.charToT9Digit('z'))
        assertEquals('5', T9SearchEngine.charToT9Digit('5'))
    }

    @Test
    fun testTextToT9() {
        // "Rahul" -> R(7) A(2) H(4) U(8) L(5) -> 72485
        assertEquals("72485", T9SearchEngine.textToT9("Rahul"))
        // "Amit" -> A(2) M(6) I(4) T(8) -> 2648
        assertEquals("2648", T9SearchEngine.textToT9("Amit"))
    }

    @Test
    fun testSearchByNamePrefix() {
        // "724" matches "RAH" in "Rahul Sharma"
        val results = t9SearchEngine.search("724", samplePeople, sampleLeadsMap)
        assertTrue(results.isNotEmpty())
        assertEquals("Rahul Sharma", results[0].displayName)
        assertEquals(LeadStatus.HOT, results[0].crmStatus)
        assertNotNull(results[0].matchedNameRange)
    }

    @Test
    fun testSearchByLastName() {
        // "742" matches "SHA" in "Sharma" (S=7, H=4, A=2)
        val results = t9SearchEngine.search("742", samplePeople, sampleLeadsMap)
        assertTrue(results.isNotEmpty())
        assertEquals("Rahul Sharma", results[0].displayName)
    }

    @Test
    fun testSearchByPhoneNumberDigits() {
        // "88888" matches Amit Verma's phone number
        val results = t9SearchEngine.search("88888", samplePeople, sampleLeadsMap)
        assertTrue(results.isNotEmpty())
        assertEquals("Amit Verma", results[0].displayName)
        assertNotNull(results[0].matchedNumberRange)
    }

    @Test
    fun testSearchRankingPrefersHotLead() {
        // "98765" is in Rahul's number (HOT) and Neha's number (COLD)
        val results = t9SearchEngine.search("98765", samplePeople, sampleLeadsMap)
        assertTrue(results.size >= 2)
        // Rahul Sharma should rank higher due to HOT status and Urgent priority boost
        assertEquals("Rahul Sharma", results[0].displayName)
    }

    @Test
    fun testBlankQueryReturnsEmpty() {
        val results = t9SearchEngine.search("", samplePeople, sampleLeadsMap)
        assertTrue(results.isEmpty())
    }
}
