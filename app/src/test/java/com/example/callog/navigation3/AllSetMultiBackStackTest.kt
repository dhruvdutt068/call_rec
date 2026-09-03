package com.example.callog.navigation3

import com.example.callog.presentation.navigation3.allset.AllSetCrmTab
import com.example.callog.presentation.navigation3.allset.AllSetMultiBackStack
import com.example.callog.presentation.navigation3.allset.AllSetNavKey
import org.junit.Assert.*
import org.junit.Test

class AllSetMultiBackStackTest {

    @Test
    fun testMultiBackStackStatePreservation() {
        val multiStack = AllSetMultiBackStack(initialTab = AllSetCrmTab.HOME)

        // Initial tab is HOME
        assertEquals(AllSetCrmTab.HOME, multiStack.selectedTab)
        assertEquals(AllSetNavKey.Home, multiStack.currentKey)

        // 1. Switch to CONTACTS tab
        multiStack.selectTab(AllSetCrmTab.CONTACTS)
        assertEquals(AllSetCrmTab.CONTACTS, multiStack.selectedTab)
        assertEquals(AllSetNavKey.Contacts.List, multiStack.currentKey)

        // 2. Navigate: Contacts -> Search Rahul -> Search Results -> Rahul Details (C001)
        multiStack.navigate(AllSetNavKey.Contacts.GlobalSearch("Rahul"))
        multiStack.navigate(AllSetNavKey.Contacts.SearchResults("Rahul"))
        multiStack.navigate(AllSetNavKey.Contacts.ContactDetails("C001"))
        assertEquals(AllSetNavKey.Contacts.ContactDetails("C001"), multiStack.currentKey)

        // 3. Switch to MEETINGS tab
        multiStack.selectTab(AllSetCrmTab.MEETINGS)
        assertEquals(AllSetCrmTab.MEETINGS, multiStack.selectedTab)
        assertEquals(AllSetNavKey.Meetings.List, multiStack.currentKey)

        // 4. Switch to TASKS tab and create a task
        multiStack.selectTab(AllSetCrmTab.TASKS)
        assertEquals(AllSetCrmTab.TASKS, multiStack.selectedTab)
        assertEquals(AllSetNavKey.Tasks.List, multiStack.currentKey)
        multiStack.navigate(AllSetNavKey.Tasks.CreateTask("C001"))
        assertEquals(AllSetNavKey.Tasks.CreateTask("C001"), multiStack.currentKey)

        // 5. Switch back to CONTACTS tab
        multiStack.selectTab(AllSetCrmTab.CONTACTS)
        assertEquals(AllSetCrmTab.CONTACTS, multiStack.selectedTab)

        // The Contacts stack must restore its previous state: ContactDetails("C001")
        assertEquals(AllSetNavKey.Contacts.ContactDetails("C001"), multiStack.currentKey)

        // 6. Switch back to TASKS tab
        multiStack.selectTab(AllSetCrmTab.TASKS)
        assertEquals(AllSetCrmTab.TASKS, multiStack.selectedTab)
        // Tasks stack should restore CreateTask("C001")
        assertEquals(AllSetNavKey.Tasks.CreateTask("C001"), multiStack.currentKey)
    }

    @Test
    fun testTabReselectionPopsToRoot() {
        val multiStack = AllSetMultiBackStack(initialTab = AllSetCrmTab.CONTACTS)
        multiStack.navigate(AllSetNavKey.Contacts.ContactDetails("C001"))
        assertEquals(AllSetNavKey.Contacts.ContactDetails("C001"), multiStack.currentKey)

        // Reselecting same tab with popToRoot = true resets stack to root
        multiStack.selectTab(AllSetCrmTab.CONTACTS, popToRootIfReselected = true)
        assertEquals(AllSetNavKey.Contacts.List, multiStack.currentKey)
    }

    @Test
    fun testFallbackToHomeTabOnStackExhausted() {
        val multiStack = AllSetMultiBackStack(initialTab = AllSetCrmTab.TASKS)
        assertEquals(AllSetCrmTab.TASKS, multiStack.selectedTab)

        // Popping when at root of TASKS tab switches back to HOME tab
        val popped = multiStack.pop()
        assertTrue(popped)
        assertEquals(AllSetCrmTab.HOME, multiStack.selectedTab)
    }
}
