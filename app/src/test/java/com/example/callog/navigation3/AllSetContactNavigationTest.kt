package com.example.callog.navigation3

import com.example.callog.presentation.navigation3.allset.AllSetBackStack
import com.example.callog.presentation.navigation3.allset.AllSetNavKey
import org.junit.Assert.*
import org.junit.Test

class AllSetContactNavigationTest {

    @Test
    fun testContactFlowSequence() {
        val stack = AllSetBackStack<AllSetNavKey>(listOf(AllSetNavKey.Contacts.List))

        // 1. Contacts -> Global Search
        stack.navigate(AllSetNavKey.Contacts.GlobalSearch(initialQuery = "Rahul"))
        assertEquals(AllSetNavKey.Contacts.GlobalSearch("Rahul"), stack.currentKey)

        // 2. Global Search -> Search Results
        stack.navigate(AllSetNavKey.Contacts.SearchResults(query = "Rahul"))
        assertEquals(AllSetNavKey.Contacts.SearchResults("Rahul"), stack.currentKey)

        // 3. Search Results -> Contact Details with Canonical contactId
        val canonicalContactId = "C001"
        stack.navigate(AllSetNavKey.Contacts.ContactDetails(contactId = canonicalContactId))
        assertEquals(AllSetNavKey.Contacts.ContactDetails("C001"), stack.currentKey)

        // 4. Contact Details -> WhatsApp sub-section
        stack.navigate(AllSetNavKey.Contacts.SubSection.WhatsApp(contactId = canonicalContactId))
        assertEquals(AllSetNavKey.Contacts.SubSection.WhatsApp("C001"), stack.currentKey)

        // 5. Contact Details -> Feedback sub-section
        stack.pop()
        stack.navigate(AllSetNavKey.Contacts.AddFeedback(contactId = canonicalContactId))
        assertEquals(AllSetNavKey.Contacts.AddFeedback("C001"), stack.currentKey)

        // Pop back to ContactDetails
        stack.pop()
        assertEquals(AllSetNavKey.Contacts.ContactDetails("C001"), stack.currentKey)
    }

    @Test
    fun testCanonicalContactIdIntegrity() {
        val dest1 = AllSetNavKey.Contacts.ContactDetails("C001")
        val dest2 = AllSetNavKey.Contacts.ContactDetails("C001")
        val dest3 = AllSetNavKey.Contacts.ContactDetails("C002")

        assertEquals(dest1, dest2)
        assertNotEquals(dest1, dest3)
        assertEquals("C001", dest1.contactId)
    }
}
