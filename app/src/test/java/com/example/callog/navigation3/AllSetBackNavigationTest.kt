package com.example.callog.navigation3

import com.example.callog.presentation.navigation3.allset.AllSetBackStack
import com.example.callog.presentation.navigation3.allset.AllSetNavKey
import org.junit.Assert.*
import org.junit.Test

class AllSetBackNavigationTest {

    @Test
    fun testInitialBackStackState() {
        val stack = AllSetBackStack<AllSetNavKey>(listOf(AllSetNavKey.Home))
        assertEquals(1, stack.size)
        assertEquals(AllSetNavKey.Home, stack.currentKey)
        assertFalse(stack.canPop)
    }

    @Test
    fun testNavigateAndPop() {
        val stack = AllSetBackStack<AllSetNavKey>(listOf(AllSetNavKey.Contacts.List))
        stack.navigate(AllSetNavKey.Contacts.ContactDetails("C001"))

        assertEquals(2, stack.size)
        assertEquals(AllSetNavKey.Contacts.ContactDetails("C001"), stack.currentKey)
        assertTrue(stack.canPop)

        val popped = stack.pop()
        assertTrue(popped)
        assertEquals(1, stack.size)
        assertEquals(AllSetNavKey.Contacts.List, stack.currentKey)
        assertFalse(stack.canPop)
    }

    @Test
    fun testPopToTarget() {
        val stack = AllSetBackStack<AllSetNavKey>(listOf(AllSetNavKey.Contacts.List))
        stack.navigate(AllSetNavKey.Contacts.GlobalSearch("Rahul"))
        stack.navigate(AllSetNavKey.Contacts.SearchResults("Rahul"))
        stack.navigate(AllSetNavKey.Contacts.ContactDetails("C001"))

        assertEquals(4, stack.size)

        // Pop back to GlobalSearch (exclusive)
        val success = stack.popTo(AllSetNavKey.Contacts.GlobalSearch("Rahul"), inclusive = false)
        assertTrue(success)
        assertEquals(2, stack.size)
        assertEquals(AllSetNavKey.Contacts.GlobalSearch("Rahul"), stack.currentKey)
    }

    @Test
    fun testPopAtRootReturnsFalse() {
        val stack = AllSetBackStack<AllSetNavKey>(listOf(AllSetNavKey.Home))
        val popped = stack.pop()
        assertFalse(popped)
        assertEquals(1, stack.size)
    }
}
