package com.example.callog.telecom

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.example.callog.data.telecom.RecordingStorageManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecordingStorageManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fakeContext: Context
    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var internalFilesDir: File
    private lateinit var externalRecordingsDir: File

    class FakeSharedPreferences : SharedPreferences {
        val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class FakeEditor(private val prefs: FakeSharedPreferences) : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) temp.remove(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                temp.clear()
                return this
            }
            override fun commit(): Boolean {
                prefs.map.putAll(temp)
                return true
            }
            override fun apply() {
                prefs.map.putAll(temp)
            }
        }
    }

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        internalFilesDir = tempFolder.newFolder("internal_files")
        externalRecordingsDir = tempFolder.newFolder("external_recordings")

        fakeContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = fakePrefs
            override fun getFilesDir(): File = internalFilesDir
            override fun getExternalFilesDir(type: String?): File = externalRecordingsDir
        }
    }

    @Test
    fun testDefaultDedicatedDirectoryCreation() {
        val manager = RecordingStorageManager(fakeContext)
        val dir = manager.getDedicatedRecordingDirectory()

        assertNotNull(dir)
        assertTrue("Directory should exist or be created", dir.exists())
        assertTrue("Directory should be a directory", dir.isDirectory)
    }

    @Test
    fun testCustomRecordingPathOverride() {
        val customDir = tempFolder.newFolder("MyCustomRecordings")
        val manager = RecordingStorageManager(fakeContext)

        manager.setCustomRecordingPath(customDir.absolutePath)
        assertEquals(customDir.absolutePath, manager.getCustomRecordingPath())

        val resolved = manager.getDedicatedRecordingDirectory()
        assertEquals(customDir.absolutePath, resolved.absolutePath)
    }

    @Test
    fun testResetToDefaultDedicatedDirectory() {
        val customDir = tempFolder.newFolder("CustomToReset")
        val manager = RecordingStorageManager(fakeContext)

        manager.setCustomRecordingPath(customDir.absolutePath)
        assertEquals(customDir.absolutePath, manager.getCustomRecordingPath())

        manager.resetToDefaultDedicatedDirectory()
        assertTrue(manager.getCustomRecordingPath().isEmpty())
    }

    @Test
    fun testStorageCheckReturnsValidResult() {
        val manager = RecordingStorageManager(fakeContext)
        val dir = manager.getDedicatedRecordingDirectory()

        val hasSpace = manager.hasAvailableStorage(dir, minBytes = 1024L)
        assertNotNull(hasSpace)
    }
}
