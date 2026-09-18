package com.example.callog.presentation.navigation3

import android.util.Log
import androidx.compose.runtime.saveable.Saver
import com.example.callog.presentation.navigation3.allset.AllSetBackStack
import com.example.callog.presentation.navigation3.allset.AllSetCrmTab
import com.example.callog.presentation.navigation3.allset.AllSetMultiBackStack
import com.example.callog.presentation.navigation3.allset.AllSetNavKey
import kotlinx.serialization.json.Json

/**
 * Dedicated persistence and state boundary adapter for Navigation 3 and AllSet backstacks.
 *
 * Guarantees that:
 * 1. Raw [Nav3Key] and [AllSetNavKey] objects NEVER cross into Android's [android.os.Bundle] or [android.os.Parcel].
 * 2. All navigation states are cleanly serialized to/from JSON strings via Kotlinx Serialization.
 * 3. Malformed, corrupted, or incompatible saved states fail safely and recover back to root destinations.
 */
object Nav3StatePersistence {

    private const val TAG = "Nav3StatePersistence"

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        allowStructuredMapKeys = true
    }

    // =========================================================================
    // Nav3Key Serialization
    // =========================================================================

    fun encodeNav3Key(key: Nav3Key): String {
        return json.encodeToString(Nav3Key.serializer(), key)
    }

    fun decodeNav3Key(serialized: String): Nav3Key? {
        return try {
            json.decodeFromString(Nav3Key.serializer(), serialized)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode Nav3Key from json: '$serialized'", e)
            null
        }
    }

    fun encodeNav3KeyList(keys: List<Nav3Key>): ArrayList<String> {
        val list = ArrayList<String>(keys.size)
        for (key in keys) {
            try {
                list.add(encodeNav3Key(key))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to encode Nav3Key: $key", e)
            }
        }
        return list
    }

    fun decodeNav3KeyList(serializedList: List<String>?, fallbackRoot: Nav3Key): List<Nav3Key> {
        if (serializedList.isNullOrEmpty()) {
            return listOf(fallbackRoot)
        }
        val decoded = ArrayList<Nav3Key>(serializedList.size)
        for (str in serializedList) {
            val key = decodeNav3Key(str)
            if (key != null) {
                decoded.add(key)
            }
        }
        return if (decoded.isEmpty()) listOf(fallbackRoot) else decoded
    }

    // =========================================================================
    // AllSetNavKey Serialization
    // =========================================================================

    fun encodeAllSetNavKey(key: AllSetNavKey): String {
        return json.encodeToString(AllSetNavKey.serializer(), key)
    }

    fun decodeAllSetNavKey(serialized: String): AllSetNavKey? {
        return try {
            json.decodeFromString(AllSetNavKey.serializer(), serialized)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode AllSetNavKey from json: '$serialized'", e)
            null
        }
    }

    fun encodeAllSetNavKeyList(keys: List<AllSetNavKey>): ArrayList<String> {
        val list = ArrayList<String>(keys.size)
        for (key in keys) {
            try {
                list.add(encodeAllSetNavKey(key))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to encode AllSetNavKey: $key", e)
            }
        }
        return list
    }

    fun decodeAllSetNavKeyList(serializedList: List<String>?, fallbackRoot: AllSetNavKey): List<AllSetNavKey> {
        if (serializedList.isNullOrEmpty()) {
            return listOf(fallbackRoot)
        }
        val decoded = ArrayList<AllSetNavKey>(serializedList.size)
        for (str in serializedList) {
            val key = decodeAllSetNavKey(str)
            if (key != null) {
                decoded.add(key)
            }
        }
        return if (decoded.isEmpty()) listOf(fallbackRoot) else decoded
    }

    // =========================================================================
    // Savers for Nav3BackStack & Nav3MultiBackStack
    // =========================================================================

    @Suppress("UNCHECKED_CAST")
    fun <T : Nav3Key> nav3BackStackSaver(fallbackRoot: T): Saver<Nav3BackStack<T>, ArrayList<String>> {
        return Saver(
            save = { stack ->
                // SnapshotStateList converted to immutable List before serializing to ArrayList<String>
                encodeNav3KeyList(stack.items.toList())
            },
            restore = { savedList ->
                val restoredKeys = decodeNav3KeyList(savedList, fallbackRoot)
                Nav3BackStack(restoredKeys as List<T>)
            }
        )
    }

    fun <Tab : Any> nav3MultiBackStackSaver(
        initialTab: Tab,
        tabRoots: Map<Tab, Nav3Key>,
        tabToString: (Tab) -> String,
        stringToTab: (String) -> Tab?
    ): Saver<Nav3MultiBackStackState<Tab, Nav3Key>, ArrayList<Any>> {
        return Saver(
            save = { state ->
                val list = ArrayList<Any>()
                list.add(tabToString(state.selectedTab))
                val map = HashMap<String, ArrayList<String>>()
                state.stacks.forEach { (tab, backStack) ->
                    val tabKey = tabToString(tab)
                    map[tabKey] = encodeNav3KeyList(backStack.items.toList())
                }
                list.add(map)
                list
            },
            restore = { list ->
                try {
                    val rawTab = list.getOrNull(0) as? String
                    val selectedTab = (if (rawTab != null) stringToTab(rawTab) else null) ?: initialTab
                    @Suppress("UNCHECKED_CAST")
                    val map = list.getOrNull(1) as? Map<String, ArrayList<String>>

                    val restoredState = Nav3MultiBackStackState(selectedTab, tabRoots)
                    tabRoots.forEach { (tab, fallbackRoot) ->
                        val tabKey = tabToString(tab)
                        val serializedItems = map?.get(tabKey)
                        val restoredItems = decodeNav3KeyList(serializedItems, fallbackRoot)
                        restoredState.stacks[tab] = Nav3BackStack(restoredItems)
                    }
                    restoredState
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring Nav3MultiBackStack, returning fresh state", e)
                    Nav3MultiBackStackState(initialTab, tabRoots)
                }
            }
        )
    }

    // =========================================================================
    // Savers for AllSetBackStack & AllSetMultiBackStack
    // =========================================================================

    @Suppress("UNCHECKED_CAST")
    fun <T : AllSetNavKey> allSetBackStackSaver(fallbackRoot: T): Saver<AllSetBackStack<T>, ArrayList<String>> {
        return Saver(
            save = { stack ->
                encodeAllSetNavKeyList(stack.items.toList())
            },
            restore = { savedList ->
                val restoredKeys = decodeAllSetNavKeyList(savedList, fallbackRoot)
                AllSetBackStack(restoredKeys as List<T>)
            }
        )
    }

    fun allSetMultiBackStackSaver(
        initialTab: AllSetCrmTab = AllSetCrmTab.HOME,
        tabRoots: Map<AllSetCrmTab, AllSetNavKey> = AllSetMultiBackStack.defaultTabRoots()
    ): Saver<AllSetMultiBackStack, ArrayList<Any>> {
        return Saver(
            save = { state ->
                val list = ArrayList<Any>()
                list.add(state.selectedTab.name)
                val map = HashMap<String, ArrayList<String>>()
                state.stacks.forEach { (tab, backStack) ->
                    map[tab.name] = encodeAllSetNavKeyList(backStack.items.toList())
                }
                list.add(map)
                list
            },
            restore = { list ->
                try {
                    val tabName = list.getOrNull(0) as? String
                    val selectedTab = tabName?.let { runCatching { AllSetCrmTab.valueOf(it) }.getOrNull() } ?: initialTab
                    @Suppress("UNCHECKED_CAST")
                    val map = list.getOrNull(1) as? Map<String, ArrayList<String>>

                    val multiStack = AllSetMultiBackStack(selectedTab, tabRoots)
                    tabRoots.forEach { (tab, fallbackRoot) ->
                        val serializedItems = map?.get(tab.name)
                        val restoredItems = decodeAllSetNavKeyList(serializedItems, fallbackRoot)
                        multiStack.stacks[tab] = AllSetBackStack(restoredItems, multiStack.resultManager)
                    }
                    multiStack
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring AllSetMultiBackStack, returning fresh state", e)
                    AllSetMultiBackStack(initialTab, tabRoots)
                }
            }
        )
    }
}
