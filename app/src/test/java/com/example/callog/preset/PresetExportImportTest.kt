package com.example.callog.preset

import com.example.callog.domain.model.preset.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PresetExportImportTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Test
    fun testExportAndImportWithoutSecrets() {
        val original = AppPreset(
            id = "export_test_id",
            name = "Exported Dev",
            environment = PresetEnvironment.DEVELOPMENT,
            description = "To be exported",
            configuration = PresetConfiguration(
                supabaseUrl = "https://dev.supabase.co",
                firebaseProjectId = "dev-project",
                features = PresetConfiguration.defaultFeatureFlags()
            ),
            isActive = true
        )

        val exportable = ExportablePreset(
            version = 1,
            preset = original.copy(isActive = false),
            secrets = null
        )

        val jsonString = json.encodeToString(exportable)
        assertNotNull(jsonString)
        assertTrue(jsonString.contains("Exported Dev"))
        assertTrue(jsonString.contains("https://dev.supabase.co"))

        val decoded = json.decodeFromString<ExportablePreset>(jsonString)
        assertEquals(1, decoded.version)
        assertEquals("Exported Dev", decoded.preset.name)
        assertEquals(PresetEnvironment.DEVELOPMENT, decoded.preset.environment)
        assertNull(decoded.secrets)
        assertFalse(decoded.preset.isActive)
    }

    @Test
    fun testExportAndImportWithSecrets() {
        val original = AppPreset(
            id = "export_test_prod",
            name = "Exported Prod",
            environment = PresetEnvironment.PRODUCTION,
            configuration = PresetConfiguration(
                supabaseUrl = "https://prod.supabase.co",
                firebaseProjectId = "callog-prod",
                firebaseAppId = "1:999999999:android:prod123",
                storageBucket = "callog-prod.appspot.com"
            ),
            isActive = false
        )

        val secrets = PresetSecrets(
            supabaseAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.prodKey",
            firebaseApiKey = "AIzaSyProdKeySecret12345",
            apiKey = "prod-api-key"
        )

        val exportable = ExportablePreset(
            version = 1,
            preset = original,
            secrets = secrets
        )

        val jsonString = json.encodeToString(exportable)
        assertTrue(jsonString.contains("prodKey"))
        assertTrue(jsonString.contains("AIzaSyProdKeySecret12345"))
        assertTrue(jsonString.contains("1:999999999:android:prod123"))

        val decoded = json.decodeFromString<ExportablePreset>(jsonString)
        assertNotNull(decoded.secrets)
        assertEquals("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.prodKey", decoded.secrets?.supabaseAnonKey)
        assertEquals("AIzaSyProdKeySecret12345", decoded.secrets?.firebaseApiKey)
        assertEquals("prod-api-key", decoded.secrets?.apiKey)
        assertEquals("1:999999999:android:prod123", decoded.preset.configuration.firebaseAppId)
        assertEquals("callog-prod.appspot.com", decoded.preset.configuration.storageBucket)
    }
}
