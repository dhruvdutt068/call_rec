package com.example.callog.presentation.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Observable, Compose-first Navigation 3 back stack.
 * Encapsulates a snapshot state list of typed navigation keys.
 */
@Stable
class Nav3BackStack<T : Any>(
    initialKeys: List<T>
) {
    val items: SnapshotStateList<T> = mutableStateListOf<T>().apply {
        addAll(initialKeys)
    }

    val currentKey: T?
        get() = items.lastOrNull()

    val canPop: Boolean
        get() = items.size > 1

    /**
     * Navigates to a new key by pushing it onto the backstack.
     */
    fun navigate(key: T) {
        items.add(key)
    }

    /**
     * Pops the topmost destination if more than one exists.
     * Returns true if a destination was popped.
     */
    fun pop(): Boolean {
        return if (items.size > 1) {
            items.removeAt(items.lastIndex)
            true
        } else {
            false
        }
    }

    /**
     * Pops back to a specific target key.
     */
    fun popTo(target: T, inclusive: Boolean = false): Boolean {
        val index = items.indexOfLast { it == target }
        if (index == -1) return false
        val removeStartIndex = if (inclusive) index else index + 1
        while (items.size > removeStartIndex) {
            items.removeAt(items.lastIndex)
        }
        return true
    }

    /**
     * Replaces the current destination with a new one.
     */
    fun replace(key: T) {
        if (items.isNotEmpty()) {
            items.removeAt(items.lastIndex)
        }
        items.add(key)
    }

    /**
     * Resets the backstack to a single root key.
     */
    fun resetToRoot(rootKey: T) {
        items.clear()
        items.add(rootKey)
    }
}

/**
 * Remembers a [Nav3BackStack] across recompositions and configuration changes.
 */
@Composable
fun <T : Any> rememberNav3BackStack(
    vararg initialKeys: T
): Nav3BackStack<T> {
    require(initialKeys.isNotEmpty()) { "Backstack must have at least one initial key" }
    return rememberSaveable(
        saver = Saver(
            save = { backStack -> backStack.items.toList() },
            restore = { savedList -> Nav3BackStack(savedList) }
        )
    ) {
        Nav3BackStack(initialKeys.toList())
    }
}
