package com.example.callog.navigation3

import androidx.compose.runtime.saveable.SaverScope
import com.example.callog.presentation.navigation3.CrmBottomTab
import com.example.callog.presentation.navigation3.Nav3Key
import com.example.callog.presentation.navigation3.Nav3MultiBackStackState
import com.example.callog.presentation.navigation3.Nav3StatePersistence
import org.junit.Assert.*
import org.junit.Test

/**
 * Activity lifecycle and saved-state preservation test.
 *
 * Verifies that during Activity pause/stop (such as when launching InCallActivity from Contacts):
 * 1. The navigation state is converted into primitive JSON strings.
 * 2. No raw unparcelable objects cross the state boundary.
 * 3. Deep navigation states (Contacts -> ContactDetails -> Nested Screen) survive process recreation.
 */
class Nav3LifecycleStateTest {

    private val dummySaverScope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    private val tabRoots = mapOf(
        CrmBottomTab.DIALER to Nav3Key.CrmTab.Dialer,
        CrmBottomTab.CALL_LOGS to Nav3Key.CrmTab.CallLogs,
        CrmBottomTab.CONTACTS to Nav3Key.Contacts.ContactList,
        CrmBottomTab.RECORDINGS to Nav3Key.CrmTab.Recordings
    )

    @Test
    fun testContactsToInCallActivityLifecycleStatePreservation() {
        // 1. User starts in Contacts tab
        val multiStack = Nav3MultiBackStackState(
            initialTab = CrmBottomTab.CONTACTS,
            tabRoots = tabRoots
        )

        // 2. User taps a contact -> Navigates to ContactDetails
        multiStack.navigate(Nav3Key.Contacts.ContactDetails(contactId = "C001", canonicalContactId = "canon_123"))

        // 3. User navigates to a nested action (e.g. Schedule Meeting)
        multiStack.navigate(Nav3Key.Meeting.ScheduleMeeting(initialContactId = "C001"))

        assertEquals(3, multiStack.currentStack.items.size)
        assertTrue(multiStack.currentKey is Nav3Key.Meeting.ScheduleMeeting)

        // 4. User taps Call -> Launches InCallActivity -> MainActivity is stopped
        // Android OS invokes Compose Saver to bundle state
        val saver = Nav3StatePersistence.nav3MultiBackStackSaver(
            initialTab = CrmBottomTab.CALL_LOGS,
            tabRoots = tabRoots,
            tabToString = { it.name },
            stringToTab = { name -> CrmBottomTab.values().firstOrNull { it.name == name } }
        )

        val savedState = with(saver) { dummySaverScope.save(multiStack) }
        assertNotNull("Saved state must produce a non-null serializable structure", savedState)
        assertTrue(savedState is ArrayList<*>)

        // 5. Verify the saved state contains ONLY primitives / Strings / Maps
        val savedList = savedState as ArrayList<Any>
        assertEquals("CONTACTS", savedList[0])
        @Suppress("UNCHECKED_CAST")
        val map = savedList[1] as Map<String, ArrayList<String>>
        assertNotNull(map)

        // Ensure every entry in the list is a serialized String (NOT a raw Nav3Key)
        val contactsSerialized = map["CONTACTS"]
        assertNotNull(contactsSerialized)
        assertEquals(3, contactsSerialized!!.size)
        for (item in contactsSerialized) {
            assertTrue("Every backstack item must be a JSON string", item is String)
            assertFalse("Item must not be a raw object", item::class.java.name.contains("Nav3Key"))
        }

        // 6. Simulate Activity Re-creation: Restore from saved state
        val restoredMultiStack = saver.restore(savedList)
        assertNotNull(restoredMultiStack)

        // 7. Verify all navigation hierarchy and nested screens are precisely intact
        assertEquals(CrmBottomTab.CONTACTS, restoredMultiStack!!.selectedTab)
        val restoredContactsStack = restoredMultiStack.stacks[CrmBottomTab.CONTACTS]
        assertNotNull(restoredContactsStack)
        assertEquals(3, restoredContactsStack!!.items.size)

        assertEquals(Nav3Key.Contacts.ContactList, restoredContactsStack.items[0])
        assertEquals(
            Nav3Key.Contacts.ContactDetails(contactId = "C001", canonicalContactId = "canon_123"),
            restoredContactsStack.items[1]
        )
        assertEquals(
            Nav3Key.Meeting.ScheduleMeeting(initialContactId = "C001"),
            restoredContactsStack.items[2]
        )

        // Verify other tabs (like RECORDINGS) restored cleanly to their root
        val restoredRecordingsStack = restoredMultiStack.stacks[CrmBottomTab.RECORDINGS]
        assertNotNull(restoredRecordingsStack)
        assertEquals(Nav3Key.CrmTab.Recordings, restoredRecordingsStack!!.currentKey)
    }
}
