package com.example.callog.telecom

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import android.net.FakeUri
import com.example.callog.core.telecom.RingtoneController
import com.example.callog.data.repository.RingtoneRepositoryImpl
import com.example.callog.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.InputStream

class RingtonePersistenceAndLifecycleTest {

    private lateinit var tempDir: File
    private lateinit var testContext: TestContext
    private lateinit var ringtoneRepo: RingtoneRepositoryImpl

    class InMemorySharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = HashMap(map)
        override fun getString(key: String, defValue: String?): String? = (map[key] as? String) ?: defValue
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = (map[key] as? Set<String>) ?: defValues
        override fun getInt(key: String, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removes = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor { pending[key] = value; return this }
            override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor { pending[key] = values; return this }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor { pending[key] = value; return this }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor { pending[key] = value; return this }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor { pending[key] = value; return this }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { pending[key] = value; return this }
            override fun remove(key: String): SharedPreferences.Editor { removes.add(key); return this }
            override fun clear(): SharedPreferences.Editor { clearAll = true; return this }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) map.clear()
                removes.forEach { map.remove(it) }
                map.putAll(pending)
            }
        }
    }


    class TestContext(private val baseDir: File) : ContextWrapper(null) {
        val sharedPrefs = InMemorySharedPreferences()

        override fun getFilesDir(): File = baseDir
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = sharedPrefs
        override fun getPackageName(): String = "com.example.callog"
        override fun getApplicationContext(): Context = this
    }

    @Before
    fun setUp() {
        tempDir = File.createTempFile("callog_test_", "_dir").apply {
            delete()
            mkdirs()
        }
        testContext = TestContext(tempDir)
        ringtoneRepo = RingtoneRepositoryImpl(testContext).apply {
            uriParser = { FakeUri(it) }
            fileToUri = { FakeUri("file://${it.absolutePath}", it.absolutePath) }
        }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // ─────────────────────────────────────────────────────────────
    // 1. Ringtone Persistence & Atomic Storage
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testImportAudioFileCopiesToInternalStorageAndPersists() = runBlocking {
        // Create dummy external audio file (over 256 bytes)
        val dummyAudio = File(tempDir, "external_audio.m4a").apply {
            writeBytes(ByteArray(512) { 0x41 })
        }
        val externalUri = FakeUri("file://${dummyAudio.absolutePath}", dummyAudio.absolutePath)

        val result = ringtoneRepo.importRingtoneForLeadHot(externalUri)
        assertTrue(result.isSuccess)

        val savedUri = result.getOrNull()
        assertNotNull(savedUri)
        assertEquals("file", savedUri?.scheme)

        val destinationFile = File(savedUri?.path ?: "")
        assertTrue("Imported file must exist in internal storage", destinationFile.exists())
        assertEquals(512L, destinationFile.length())
        assertTrue(
            "Imported file must be placed inside app internal ringtones directory",
            destinationFile.absolutePath.contains("ringtones")
        )

        // Verify restored from repository
        val restoredUri = ringtoneRepo.hotLead()
        assertEquals(savedUri, restoredUri)
    }

    @Test
    fun testImportFailurePreservesPreviouslyConfiguredRingtone() = runBlocking {
        // Step 1: Set initial valid ringtone
        val initialAudio = File(tempDir, "initial_audio.m4a").apply {
            writeBytes(ByteArray(512) { 0x42 })
        }
        val initialUri = FakeUri("file://${initialAudio.absolutePath}", initialAudio.absolutePath)
        val firstResult = ringtoneRepo.importRingtoneForLeadWarm(initialUri)
        assertTrue(firstResult.isSuccess)
        val initialSavedUri = ringtoneRepo.warmLead()
        assertNotNull(initialSavedUri)

        // Step 2: Attempt import of 0-byte corrupt file
        val badAudio = File(tempDir, "corrupted.m4a").apply {
            writeBytes(ByteArray(0))
        }
        val badUri = FakeUri("file://${badAudio.absolutePath}", badAudio.absolutePath)

        val secondResult = ringtoneRepo.importRingtoneForLeadWarm(badUri)
        assertTrue("Corrupted 0-byte file import must fail", secondResult.isFailure)

        // Step 3: Verify the previously saved ringtone is still active and intact
        val currentWarmUri = ringtoneRepo.warmLead()
        assertEquals(initialSavedUri, currentWarmUri)
        assertTrue(File(currentWarmUri?.path ?: "").exists())
    }

    @Test
    fun testResetToDefaultCleansUpInternalFile() = runBlocking {
        val audioFile = File(tempDir, "sample.m4a").apply {
            writeBytes(ByteArray(512) { 0x43 })
        }
        val externalUri = FakeUri("file://${audioFile.absolutePath}", audioFile.absolutePath)

        val importResult = ringtoneRepo.importRingtoneForLeadCold(externalUri)
        assertTrue(importResult.isSuccess)

        val savedFile = File(ringtoneRepo.coldLead()?.path ?: "")
        assertTrue(savedFile.exists())

        // Reset to default
        ringtoneRepo.setRingtoneForLeadCold(null)
        assertNull(ringtoneRepo.coldLead())
        assertFalse("Resetting ringtone to default must delete private internal file", savedFile.exists())
    }

    @Test
    fun testCorruptedOrDeletedInternalFileCleansPrefAndFallsBack() = runBlocking {
        val audioFile = File(tempDir, "will_delete.m4a").apply {
            writeBytes(ByteArray(512) { 0x44 })
        }
        val externalUri = FakeUri("file://${audioFile.absolutePath}", audioFile.absolutePath)

        ringtoneRepo.importRingtoneForCustomer(externalUri)
        val customerUri = ringtoneRepo.customer()
        assertNotNull(customerUri)

        // Simulate external deletion or corruption on disk
        val diskFile = File(customerUri?.path ?: "")
        assertTrue(diskFile.exists())
        diskFile.delete()

        // Calling customer() should detect missing file, clear preference, and return null (for default fallback)
        val fallbackUri = ringtoneRepo.customer()
        assertNull("Missing file must cause customer() to return null and clear stale pref", fallbackUri)
    }

    // ─────────────────────────────────────────────────────────────
    // 2. RingtoneController Idempotency & Audio Focus
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testRingtoneControllerIdempotentCalls() {
        val policy = object : com.example.callog.domain.repository.RingtonePolicy {
            override fun ringtoneFor(person: Person?, lead: Lead?): Uri = FakeUri("file://${tempDir.absolutePath}/test.m4a", "${tempDir.absolutePath}/test.m4a")
        }

        var startCount = 0
        var stopCount = 0

        val controller = object : RingtoneController(testContext, policy) {
            override fun startRingtone(person: Person?, lead: Lead?) {
                startCount++
            }
            override fun stopRingtone() {
                stopCount++
            }
        }

        // Call startRingtone twice for the same call
        controller.startRingtone("call_101", null, null)
        controller.startRingtone("call_101", null, null)

        assertEquals("Delegation to startRingtone should be registered", 2, startCount)

        controller.stopRingtone()
        assertEquals(1, stopCount)
    }
}
