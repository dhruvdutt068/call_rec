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
 * Creates and remembers a [Nav3MultiBackStackState] for enum tabs and [Nav3Key] destinations
 * with safe JSON state persistence across Activity lifecycles.
 */
@Composable
inline fun <reified Tab : Enum<Tab>> rememberNav3MultiBackStack(
    initialTab: Tab,
    tabRoots: Map<Tab, Nav3Key>
): Nav3MultiBackStackState<Tab, Nav3Key> {
    return rememberSaveable(
        saver = Nav3StatePersistence.nav3MultiBackStackSaver(
            initialTab = initialTab,
            tabRoots = tabRoots,
            tabToString = { it.name },
            stringToTab = { name -> enumValues<Tab>().firstOrNull { it.name == name } }
        )
    ) {
        Nav3MultiBackStackState(initialTab, tabRoots)
    }
}

/**
 * Creates and remembers a generic [Nav3MultiBackStackState] with a dedicated type-safe [Saver].
 */
@Composable
fun <Tab : Any, Key : Any> rememberGenericNav3MultiBackStack(
    initialTab: Tab,
    tabRoots: Map<Tab, Key>,
    saver: Saver<Nav3MultiBackStackState<Tab, Key>, out Any>
): Nav3MultiBackStackState<Tab, Key> {
    return rememberSaveable(saver = saver) {
        Nav3MultiBackStackState(initialTab, tabRoots)
    }
}

