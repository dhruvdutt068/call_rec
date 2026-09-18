package com.example.callog.navigation3

import androidx.compose.runtime.saveable.SaverScope
import com.example.callog.presentation.navigation3.CrmBottomTab
import com.example.callog.presentation.navigation3.Nav3BackStack
import com.example.callog.presentation.navigation3.Nav3Key
import com.example.callog.presentation.navigation3.Nav3MultiBackStackState
import com.example.callog.presentation.navigation3.Nav3StatePersistence
import com.example.callog.presentation.navigation3.allset.AllSetBackStack
import com.example.callog.presentation.navigation3.allset.AllSetCrmTab
import com.example.callog.presentation.navigation3.allset.AllSetMultiBackStack
import com.example.callog.presentation.navigation3.allset.AllSetNavKey
import org.junit.Assert.*
import org.junit.Test

class Nav3StatePersistenceTest {

    private val dummySaverScope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    // =========================================================================
    // 1. Representative Navigation Keys Serialization Round-Trip
    // =========================================================================

    @Test
    fun testSerializationOfFiveRepresentativeNav3KeyDestinations() {
        val keys = listOf(
            Nav3Key.CrmTab.Recordings,
            Nav3Key.CrmTab.Dashboard,
            Nav3Key.Contacts.ContactDetails(contactId = "C001", canonicalContactId = "uuid-canonical-1"),
            Nav3Key.Task.CreateTask(initialContactId = "C002"),
            Nav3Key.Auth.OtpVerification(destinationAddress = "support@allset.com")
        )

        for (originalKey in keys) {
            val json = Nav3StatePersistence.encodeNav3Key(originalKey)
            assertNotNull("JSON string should not be null", json)
            assertTrue("JSON should contain type descriptor or properties", json.isNotEmpty())

            val decodedKey = Nav3StatePersistence.decodeNav3Key(json)
            assertEquals("Decoded key must match original key", originalKey, decodedKey)
        }
    }

    @Test
    fun testSerializationOfFiveRepresentativeAllSetNavKeyDestinations() {
        val keys = listOf(
            AllSetNavKey.Home,
            AllSetNavKey.ContactsTab,
            AllSetNavKey.Contacts.ContactDetails(contactId = "C_101"),
            AllSetNavKey.Contacts.SubSection.WhatsApp(contactId = "C_101"),
            AllSetNavKey.Tasks.CreateTask(initialContactId = "C_102")
        )

        for (originalKey in keys) {
            val json = Nav3StatePersistence.encodeAllSetNavKey(originalKey)
            assertNotNull("JSON string should not be null", json)

            val decodedKey = Nav3StatePersistence.decodeAllSetNavKey(json)
            assertEquals("Decoded AllSet key must match original key", originalKey, decodedKey)
        }
    }

    // =========================================================================
    // 2. Single & Parameterized Destinations
    // =========================================================================

    @Test
    fun testSingleDestinationNav3BackStackSaveAndRestore() {
        val stack = Nav3BackStack<Nav3Key>(listOf(Nav3Key.CrmTab.Recordings))
        val saver = Nav3StatePersistence.nav3BackStackSaver<Nav3Key>(fallbackRoot = Nav3Key.CrmTab.CallLogs)

        val savedList = with(saver) { dummySaverScope.save(stack) }
        assertNotNull(savedList)
        assertTrue(savedList is ArrayList<*>)
        assertTrue(savedList!!.first() is String)

        val restoredStack = saver.restore(savedList)
        assertNotNull(restoredStack)
        assertEquals(1, restoredStack!!.items.size)
        assertEquals(Nav3Key.CrmTab.Recordings, restoredStack.currentKey)
    }

    @Test
    fun testParameterizedDestinationWithArgumentsSaveAndRestore() {
        val originalKey = Nav3Key.Contacts.ContactDetails(
            contactId = "contact_456",
            canonicalContactId = "canonical_789"
        )
        val stack = Nav3BackStack<Nav3Key>(listOf(originalKey))
        val saver = Nav3StatePersistence.nav3BackStackSaver<Nav3Key>(fallbackRoot = Nav3Key.Contacts.ContactList)

        val saved = with(saver) { dummySaverScope.save(stack) }
        val restored = saver.restore(saved!!)

        assertNotNull(restored)
        val restoredKey = restored!!.currentKey as? Nav3Key.Contacts.ContactDetails
        assertNotNull(restoredKey)
        assertEquals("contact_456", restoredKey!!.contactId)
        assertEquals("canonical_789", restoredKey.canonicalContactId)
    }

    // =========================================================================
    // 3. Nested Destinations Backstack
    // =========================================================================

    @Test
    fun testNestedDestinationsPreserveOrderAndBackNavigationCapabilities() {
        val stack = Nav3BackStack<Nav3Key>(listOf(Nav3Key.Contacts.ContactList))
        stack.navigate(Nav3Key.Contacts.ContactDetails(contactId = "C001"))
        stack.navigate(Nav3Key.Task.CreateTask(initialContactId = "C001"))

        assertEquals(3, stack.items.size)
        assertTrue(stack.canPop)

        val saver = Nav3StatePersistence.nav3BackStackSaver<Nav3Key>(fallbackRoot = Nav3Key.Contacts.ContactList)
        val saved = with(saver) { dummySaverScope.save(stack) }
        val restored = saver.restore(saved!!)

        assertNotNull(restored)
        assertEquals(3, restored!!.items.size)
        assertEquals(Nav3Key.Contacts.ContactList, restored.items[0])
        assertEquals(Nav3Key.Contacts.ContactDetails(contactId = "C001"), restored.items[1])
        assertEquals(Nav3Key.Task.CreateTask(initialContactId = "C001"), restored.items[2])

        // Verify pop behavior on restored backstack
        assertTrue(restored.pop())
        assertEquals(Nav3Key.Contacts.ContactDetails(contactId = "C001"), restored.currentKey)
        assertTrue(restored.pop())
        assertEquals(Nav3Key.Contacts.ContactList, restored.currentKey)
        assertFalse(restored.pop())
    }

    // =========================================================================
    // 4. Empty / Fallback Backstack Save & Restore
    // =========================================================================

    @Test
    fun testEmptyOrNullSerializedListRecoversWithFallbackRoot() {
        val fallback = Nav3Key.CrmTab.CallLogs
        val saver = Nav3StatePersistence.nav3BackStackSaver<Nav3Key>(fallbackRoot = fallback)

        val restoredFromEmpty = saver.restore(ArrayList())
        assertNotNull(restoredFromEmpty)
        assertEquals(1, restoredFromEmpty!!.items.size)
        assertEquals(fallback, restoredFromEmpty.currentKey)
    }

    // =========================================================================
    // 5. Malformed & Corrupted State Recovery
    // =========================================================================

    @Test
    fun testMalformedJsonStringsFailSafelyWithoutCrashingAndReturnFallbackRoot() {
        val corruptList = arrayListOf(
            "INVALID_JSON_STRING_123",
            "{\"unknown_property\": 42}",
            ""
        )
        val fallback = Nav3Key.CrmTab.Dialer
        val decodedKeys = Nav3StatePersistence.decodeNav3KeyList(corruptList, fallback)

        assertEquals(1, decodedKeys.size)
        assertEquals(fallback, decodedKeys.first())
    }

    // =========================================================================
    // 6. Multi-Tab Backstacks & Selected Tab Restoration
    // =========================================================================

    @Test
    fun testMultiTabBackstackIsolationAndSelectedTabRestoration() {
        val tabRoots = mapOf(
            CrmBottomTab.DIALER to Nav3Key.CrmTab.Dialer,
            CrmBottomTab.CALL_LOGS to Nav3Key.CrmTab.CallLogs,
            CrmBottomTab.CONTACTS to Nav3Key.Contacts.ContactList,
            CrmBottomTab.RECORDINGS to Nav3Key.CrmTab.Recordings
        )

        val multiStack = Nav3MultiBackStackState(
            initialTab = CrmBottomTab.CONTACTS,
            tabRoots = tabRoots
        )

        // Navigate within Contacts tab
        multiStack.navigate(Nav3Key.Contacts.ContactDetails("C999"))

        // Switch to Recordings tab and navigate within it
        multiStack.selectTab(CrmBottomTab.RECORDINGS)

        val saver = Nav3StatePersistence.nav3MultiBackStackSaver(
            initialTab = CrmBottomTab.CALL_LOGS,
            tabRoots = tabRoots,
            tabToString = { it.name },
            stringToTab = { name -> CrmBottomTab.values().firstOrNull { it.name == name } }
        )

        val savedState = with(saver) { dummySaverScope.save(multiStack) }
        assertNotNull(savedState)
        assertTrue(savedState is ArrayList<*>)

        val restoredState = saver.restore(savedState as ArrayList<Any>)
        assertNotNull(restoredState)
        assertEquals(CrmBottomTab.RECORDINGS, restoredState!!.selectedTab)

        // Verify Contacts tab preserved its nested navigation independently
        val contactsStack = restoredState.stacks[CrmBottomTab.CONTACTS]
        assertNotNull(contactsStack)
        assertEquals(2, contactsStack!!.items.size)
        assertEquals(Nav3Key.Contacts.ContactDetails("C999"), contactsStack.currentKey)

        // Verify Recordings tab preserved its root
        val recordingsStack = restoredState.stacks[CrmBottomTab.RECORDINGS]
        assertNotNull(recordingsStack)
        assertEquals(1, recordingsStack!!.items.size)
        assertEquals(Nav3Key.CrmTab.Recordings, recordingsStack.currentKey)
    }

    // =========================================================================
    // 7. AllSet MultiBackStack Full Round Trip
    // =========================================================================

    @Test
    fun testAllSetMultiBackStackSaveAndRestoreRoundtrip() {
        val multiStack = AllSetMultiBackStack(initialTab = AllSetCrmTab.CONTACTS)
        multiStack.navigate(AllSetNavKey.Contacts.ContactDetails("AllSet_007"))

        val saver = Nav3StatePersistence.allSetMultiBackStackSaver(initialTab = AllSetCrmTab.HOME)
        val saved = with(saver) { dummySaverScope.save(multiStack) }
        assertNotNull(saved)

        val restored = saver.restore(saved as ArrayList<Any>)
        assertNotNull(restored)
        assertEquals(AllSetCrmTab.CONTACTS, restored!!.selectedTab)
        assertEquals(AllSetNavKey.Contacts.ContactDetails("AllSet_007"), restored.currentKey)
    }
}
