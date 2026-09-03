package com.example.callog.presentation.navigation3.allset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap

/**
 * Result bus for Navigation 3 result-passing recipes.
 * Enables screens like EditContact, AddMeeting, or CreateTask to return strongly-typed results
 * to caller screens without tight coupling.
 */
@Stable
class AllSetResultManager {
    private val results: SnapshotStateMap<String, Any> = mutableStateMapOf()

    fun <R : Any> setResult(key: String, result: R) {
        results[key] = result
    }

    @Suppress("UNCHECKED_CAST")
    fun <R : Any> consumeResult(key: String): R? {
        val result = results.remove(key)
        return result as? R
    }

    fun hasResult(key: String): Boolean = results.containsKey(key)
}

/**
 * Navigation 3 Observable Back Stack.
 * Implements standard push, pop, popTo, and replace operations on an observable [SnapshotStateList].
 */
@Stable
class AllSetBackStack<T : Any>(
    initialKeys: List<T>,
    val resultManager: AllSetResultManager = AllSetResultManager()
) {
    val items: SnapshotStateList<T> = mutableStateListOf<T>().apply {
        addAll(initialKeys)
    }

    val currentKey: T?
        get() = items.lastOrNull()

    val size: Int
        get() = items.size

    val canPop: Boolean
        get() = items.size > 1

    fun navigate(key: T) {
        items.add(key)
    }

    fun pop(): Boolean {
        return if (items.size > 1) {
            items.removeAt(items.lastIndex)
            true
        } else {
            false
        }
    }

    fun popTo(target: T, inclusive: Boolean = false): Boolean {
        val index = items.indexOfLast { it == target }
        if (index == -1) return false
        val removeStartIndex = if (inclusive) index else index + 1
        while (items.size > removeStartIndex) {
            items.removeAt(items.lastIndex)
        }
        return true
    }

    fun replace(key: T) {
        if (items.isNotEmpty()) {
            items.removeAt(items.lastIndex)
        }
        items.add(key)
    }

    fun resetTo(rootKey: T) {
        items.clear()
        items.add(rootKey)
    }
}

/**
 * Remembers an [AllSetBackStack] across configuration changes.
 */
@Composable
fun <T : Any> rememberAllSetBackStack(
    vararg initialKeys: T
): AllSetBackStack<T> {
    require(initialKeys.isNotEmpty()) { "Back stack must have at least one initial destination key." }
    return rememberSaveable(
        saver = Saver(
            save = { stack -> stack.items.toList() },
            restore = { saved -> AllSetBackStack(saved) }
        )
    ) {
        AllSetBackStack(initialKeys.toList())
    }
}
