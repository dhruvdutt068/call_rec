package com.example.callog.presentation.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap

/**
 * Manages multiple independent [Nav3BackStack] instances for top-level app tabs.
 * Allows switching between tabs while preserving the full navigation history and hierarchy of each tab.
 */
@Stable
class Nav3MultiBackStackState<Tab : Any, Key : Any>(
    initialTab: Tab,
    val tabRoots: Map<Tab, Key>
) {
    var selectedTab: Tab by mutableStateOf(initialTab)

    val stacks: SnapshotStateMap<Tab, Nav3BackStack<Key>> = mutableStateMapOf<Tab, Nav3BackStack<Key>>().apply {
        tabRoots.forEach { (tab, rootKey) ->
            put(tab, Nav3BackStack(listOf(rootKey)))
        }
    }

    val currentStack: Nav3BackStack<Key>
        get() = stacks[selectedTab] ?: error("No backstack found for tab $selectedTab")

    val currentKey: Key?
        get() = currentStack.currentKey

    /**
     * Switches to [tab]. If [tab] is already selected and reselect is triggered, it optionally pops to root.
     */
    fun selectTab(tab: Tab, popToRootIfSelected: Boolean = false) {
        if (selectedTab == tab && popToRootIfSelected) {
            val root = tabRoots[tab]
            if (root != null) {
                currentStack.resetToRoot(root)
            }
        } else {
            selectedTab = tab
        }
    }

    /**
     * Navigates within the active tab stack.
     */
    fun navigate(key: Key) {
        currentStack.navigate(key)
    }

    /**
     * Pops within the active tab stack. Returns true if popped.
     */
    fun pop(): Boolean {
        return currentStack.pop()
    }
}

/**
 * Creates and remembers a [Nav3MultiBackStackState].
 */
@Composable
fun <Tab : Any, Key : Any> rememberNav3MultiBackStack(
    initialTab: Tab,
    tabRoots: Map<Tab, Key>
): Nav3MultiBackStackState<Tab, Key> {
    return rememberSaveable(
        saver = Saver(
            save = { state ->
                listOf(
                    state.selectedTab,
                    state.stacks.mapValues { it.value.items.toList() }
                )
            },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                val restoredTab = saved[0] as Tab
                @Suppress("UNCHECKED_CAST")
                val restoredMap = saved[1] as Map<Tab, List<Key>>
                val state = Nav3MultiBackStackState(restoredTab, tabRoots)
                restoredMap.forEach { (tab, items) ->
                    state.stacks[tab] = Nav3BackStack(items)
                }
                state
            }
        )
    ) {
        Nav3MultiBackStackState(initialTab, tabRoots)
    }
}
