package com.example.callog.preset

import com.example.callog.data.local.entity.PresetEntity
import com.example.callog.domain.model.preset.*
import org.junit.Assert.*
import org.junit.Test

class PresetEntityMappingTest {

    @Test
    fun testDomainToEntityAndBack() {
        val preset = AppPreset(
            id = "test_preset_1",
            name = "Test Staging",
            environment = PresetEnvironment.STAGING,
            description = "Test description",
            configuration = PresetConfiguration(
                supabaseUrl = "https://staging.supabase.co",
                firebaseProjectId = "callog-staging",
                firebaseAppId = "1:123456789012:android:staging123",
                storageBucket = "callog-bucket.appspot.com",
                gcmSenderId = "123456789012",
                databaseUrl = "https://staging-rtdb.firebaseio.com",
                apiBaseUrl = "https://staging.callog.com",
                features = mapOf(
                    PresetConfiguration.FEATURE_DEV_DIAGNOSTICS to true,
                    PresetConfiguration.FEATURE_CALL_SIMULATOR to false
                )
            ),
            isActive = true
        )

        val entity = PresetEntity.fromDomain(preset)
        assertEquals("test_preset_1", entity.id)
        assertEquals("Test Staging", entity.name)
        assertEquals("STAGING", entity.environment)
        assertEquals("1:123456789012:android:staging123", entity.firebaseAppId)
        assertEquals("callog-bucket.appspot.com", entity.storageBucket)
        assertEquals("123456789012", entity.gcmSenderId)
        assertEquals("https://staging-rtdb.firebaseio.com", entity.databaseUrl)
        assertTrue(entity.isActive)

        val restored = entity.toDomain()
        assertEquals(preset.id, restored.id)
        assertEquals(preset.name, restored.name)
        assertEquals(preset.environment, restored.environment)
        assertEquals(preset.configuration.supabaseUrl, restored.configuration.supabaseUrl)
        assertEquals(preset.configuration.firebaseProjectId, restored.configuration.firebaseProjectId)
        assertEquals(preset.configuration.firebaseAppId, restored.configuration.firebaseAppId)
        assertEquals(preset.configuration.storageBucket, restored.configuration.storageBucket)
        assertEquals(preset.configuration.gcmSenderId, restored.configuration.gcmSenderId)
        assertEquals(preset.configuration.databaseUrl, restored.configuration.databaseUrl)
        assertEquals(true, restored.configuration.features[PresetConfiguration.FEATURE_DEV_DIAGNOSTICS])
        assertEquals(false, restored.configuration.features[PresetConfiguration.FEATURE_CALL_SIMULATOR])
        assertTrue(restored.isActive)
    }

    @Test
    fun testPresetEnvironmentFromStringFallback() {
        assertEquals(PresetEnvironment.DEVELOPMENT, PresetEnvironment.fromString(null))
        assertEquals(PresetEnvironment.DEVELOPMENT, PresetEnvironment.fromString("INVALID_ENV"))
        assertEquals(PresetEnvironment.PRODUCTION, PresetEnvironment.fromString("production"))
        assertEquals(PresetEnvironment.STAGING, PresetEnvironment.fromString("STAGING"))
        assertEquals(PresetEnvironment.CUSTOM, PresetEnvironment.fromString("custom"))
    }
}
