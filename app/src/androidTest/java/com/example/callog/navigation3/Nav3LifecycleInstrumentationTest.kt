package com.example.callog.navigation3

import android.os.Bundle
import android.os.Parcel
import androidx.compose.runtime.saveable.SaverScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.callog.presentation.navigation3.CrmBottomTab
import com.example.callog.presentation.navigation3.Nav3Key
import com.example.callog.presentation.navigation3.Nav3MultiBackStackState
import com.example.callog.presentation.navigation3.Nav3StatePersistence
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android Instrumentation test executing against the real Android OS runtime and Telecom Parcel subsystem.
 *
 * Verifies that:
 * 1. Contacts -> ContactDetails -> Nested Screen navigation state can be saved into a Bundle.
 * 2. The Bundle can be written to and read from an Android [Parcel] without any [IllegalArgumentException].
 * 3. Activity re-creation / state restoration preserves the entire navigation hierarchy.
 */
@RunWith(AndroidJUnit4::class)
class Nav3LifecycleInstrumentationTest {

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
    fun testParcelRoundTripDuringActivityLifecycleAndInCallTransition() {
        // 1. Open Contacts Tab
        val multiStack = Nav3MultiBackStackState(
            initialTab = CrmBottomTab.CONTACTS,
            tabRoots = tabRoots
        )

        // 2. Navigate: Contacts -> ContactDetails
        multiStack.navigate(Nav3Key.Contacts.ContactDetails(contactId = "C001", canonicalContactId = "canon_123"))

        // 3. Navigate: ContactDetails -> Task (Nested destination)
        multiStack.navigate(Nav3Key.Task.CreateTask(initialContactId = "C001"))

        assertEquals(3, multiStack.currentStack.items.size)
        assertTrue(multiStack.currentKey is Nav3Key.Task.CreateTask)

        // 4. Save state using Nav3StatePersistence (simulating onSaveInstanceState when InCallActivity launches)
        val saver = Nav3StatePersistence.nav3MultiBackStackSaver(
            initialTab = CrmBottomTab.CALL_LOGS,
            tabRoots = tabRoots,
            tabToString = { it.name },
            stringToTab = { name -> CrmBottomTab.values().firstOrNull { it.name == name } }
        )

        val savedList = with(saver) { dummySaverScope.save(multiStack) }
        assertNotNull(savedList)

        // 5. Test real Android Parcel marshalling
        val outerBundle = Bundle()
        outerBundle.putSerializable("SAVED_NAV_STATE", savedList as java.io.Serializable)

        val parcel = Parcel.obtain()
        try {
            outerBundle.writeToParcel(parcel, 0)

            parcel.setDataPosition(0)

            val restoredBundle = Bundle.CREATOR.createFromParcel(parcel)
            assertNotNull(restoredBundle)

            @Suppress("UNCHECKED_CAST")
            val restoredList = restoredBundle.getSerializable("SAVED_NAV_STATE") as? ArrayList<Any>
            assertNotNull(restoredList)

            val restoredMultiStack = saver.restore(restoredList!!)
            assertNotNull(restoredMultiStack)

            // Verify full navigation state preserved
            assertEquals(CrmBottomTab.CONTACTS, restoredMultiStack!!.selectedTab)
            val contactsStack = restoredMultiStack.stacks[CrmBottomTab.CONTACTS]
            assertNotNull(contactsStack)
            assertEquals(3, contactsStack!!.items.size)

            assertEquals(Nav3Key.Contacts.ContactList, contactsStack.items[0])
            assertEquals(
                Nav3Key.Contacts.ContactDetails(contactId = "C001", canonicalContactId = "canon_123"),
                contactsStack.items[1]
            )
            assertEquals(
                Nav3Key.Task.CreateTask(initialContactId = "C001"),
                contactsStack.items[2]
            )
        } finally {
            parcel.recycle()
        }
    }
}
