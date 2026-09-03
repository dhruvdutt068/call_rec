package com.example.callog.navigation3

import com.example.callog.presentation.navigation3.allset.AllSetDeepLinkParser
import com.example.callog.presentation.navigation3.allset.AllSetNavKey
import org.junit.Assert.*
import org.junit.Test

class AllSetDeepLinkTest {

    @Test
    fun testContactDetailsDeepLink() {
        val key = AllSetDeepLinkParser.parseUrl("allset://contact/C001")
        assertEquals(AllSetNavKey.Contacts.ContactDetails("C001"), key)
    }

    @Test
    fun testContactDetailsDeepLinkWithQueryParam() {
        val key = AllSetDeepLinkParser.parseUrl("allset://contact?id=C002")
        assertEquals(AllSetNavKey.Contacts.ContactDetails("C002"), key)
    }

    @Test
    fun testWhatsAppDeepLink() {
        val key = AllSetDeepLinkParser.parseUrl("allset://whatsapp/C001")
        assertEquals(AllSetNavKey.Contacts.SubSection.WhatsApp("C001"), key)
    }

    @Test
    fun testTaskDetailsDeepLink() {
        val key = AllSetDeepLinkParser.parseUrl("allset://task/task-99")
        assertEquals(AllSetNavKey.Tasks.TaskDetails("task-99"), key)
    }

    @Test
    fun testCreateTaskWithContactDeepLink() {
        val key = AllSetDeepLinkParser.parseUrl("allset://tasks/create?contactId=C001")
        assertEquals(AllSetNavKey.Tasks.CreateTask("C001"), key)
    }

    @Test
    fun testMeetingDetailsDeepLink() {
        val key = AllSetDeepLinkParser.parseUrl("allset://meeting/meet-101")
        assertEquals(AllSetNavKey.Meetings.MeetingDetails("meet-101"), key)
    }

    @Test
    fun testScheduleMeetingWithContactDeepLink() {
        val key = AllSetDeepLinkParser.parseUrl("allset://meetings/schedule?contactId=C001")
        assertEquals(AllSetNavKey.Meetings.ScheduleMeeting("C001"), key)
    }

    @Test
    fun testSearchDeepLink() {
        val key = AllSetDeepLinkParser.parseUrl("allset://contacts/search?q=Rahul")
        assertEquals(AllSetNavKey.Contacts.SearchResults("Rahul"), key)
    }

    @Test
    fun testAuthDeepLink() {
        val key = AllSetDeepLinkParser.parseUrl("allset://auth/login")
        assertEquals(AllSetNavKey.Auth.Login, key)
    }
}
