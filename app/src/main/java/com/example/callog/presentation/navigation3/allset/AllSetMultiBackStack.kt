package com.example.callog.presentation.navigation3.allset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap

enum class AllSetCrmTab {
    HOME,
    CONTACTS,
    TASKS,
    MEETINGS,
    SETTINGS
}

/**
 * Multiple Back Stack Manager for AllSet CRM.
 * Maintains isolated, persistent backstacks for each top-level tab.
 */
@Stable
class AllSetMultiBackStack(
    initialTab: AllSetCrmTab = AllSetCrmTab.HOME,
    val tabRoots: Map<AllSetCrmTab, AllSetNavKey> = defaultTabRoots()
) {
    var selectedTab: AllSetCrmTab by mutableStateOf(initialTab)
    val resultManager: AllSetResultManager = AllSetResultManager()

    val stacks: SnapshotStateMap<AllSetCrmTab, AllSetBackStack<AllSetNavKey>> =
        mutableStateMapOf<AllSetCrmTab, AllSetBackStack<AllSetNavKey>>().apply {
            tabRoots.forEach { (tab, root) ->
                put(tab, AllSetBackStack(listOf(root), resultManager))
            }
        }

    val currentStack: AllSetBackStack<AllSetNavKey>
        get() = stacks[selectedTab] ?: error("No backstack found for tab $selectedTab")

    val currentKey: AllSetNavKey?
        get() = currentStack.currentKey

    /**
     * Switches active tab. If reselected and [popToRootIfReselected] is true, pops to root destination.
     */
    fun selectTab(tab: AllSetCrmTab, popToRootIfReselected: Boolean = false) {
        if (selectedTab == tab && popToRootIfReselected) {
            val root = tabRoots[tab]
            if (root != null) {
                currentStack.resetTo(root)
            }
        } else {
            selectedTab = tab
        }
    }

    fun navigate(key: AllSetNavKey) {
        currentStack.navigate(key)
    }

    /**
     * Pops within the active tab. If active tab is at root and not HOME, returns to HOME tab.
     * Returns true if a pop or tab fallback occurred.
     */
    fun pop(): Boolean {
        if (currentStack.pop()) {
            return true
        }
        if (selectedTab != AllSetCrmTab.HOME) {
            selectedTab = AllSetCrmTab.HOME
            return true
        }
        return false
    }

    companion object {
        fun defaultTabRoots(): Map<AllSetCrmTab, AllSetNavKey> = mapOf(
            AllSetCrmTab.HOME to AllSetNavKey.Home,
            AllSetCrmTab.CONTACTS to AllSetNavKey.Contacts.List,
            AllSetCrmTab.TASKS to AllSetNavKey.Tasks.List,
            AllSetCrmTab.MEETINGS to AllSetNavKey.Meetings.List,
            AllSetCrmTab.SETTINGS to AllSetNavKey.SettingsTab
        )
    }
}

/**
 * Remembers an [AllSetMultiBackStack] across recompositions.
 */
@Composable
fun rememberAllSetMultiBackStack(
    initialTab: AllSetCrmTab = AllSetCrmTab.HOME
): AllSetMultiBackStack {
    return rememberSaveable(
        saver = Saver(
            save = { multiStack ->
                listOf(
                    multiStack.selectedTab.name,
                    multiStack.stacks.mapValues { it.value.items.toList() }
                )
            },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                val tabName = saved[0] as String
                @Suppress("UNCHECKED_CAST")
                val restoredMap = saved[1] as Map<AllSetCrmTab, List<AllSetNavKey>>
                val tab = AllSetCrmTab.valueOf(tabName)
                val multiStack = AllSetMultiBackStack(tab)
                restoredMap.forEach { (t, items) ->
                    multiStack.stacks[t] = AllSetBackStack(items, multiStack.resultManager)
                }
                multiStack
            }
        )
    ) {
        AllSetMultiBackStack(initialTab)
    }
}
