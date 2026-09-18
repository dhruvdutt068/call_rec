package com.example.callog.data.repository

import android.util.Log
import com.example.callog.core.config.SupabaseDefaults
import com.example.callog.data.local.dao.PresetDao
import com.example.callog.data.local.entity.PresetEntity
import com.example.callog.data.security.SecureSecretStore
import com.example.callog.domain.model.preset.*
import com.example.callog.domain.repository.PresetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresetRepositoryImpl @Inject constructor(
    private val presetDao: PresetDao,
    private val secretStore: SecureSecretStore
) : PresetRepository {

    private val TAG = "PresetRepositoryImpl"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    override fun getPresetsFlow(): Flow<List<AppPreset>> {
        return presetDao.observeAllPresets().map { list ->
            list.map { it.toDomain(json) }
        }
    }

    override fun getActivePresetFlow(): Flow<AppPreset?> {
        return presetDao.observeActivePreset().map { entity ->
            entity?.toDomain(json)
        }
    }

    override suspend fun getPresets(): List<AppPreset> = withContext(Dispatchers.IO) {
        ensureDefaultPresetsInternal()
        presetDao.getAllPresets().map { it.toDomain(json) }
    }

    override suspend fun getPresetById(id: String): AppPreset? = withContext(Dispatchers.IO) {
        presetDao.getPresetById(id)?.toDomain(json)
    }

    override suspend fun getActivePreset(): AppPreset? = withContext(Dispatchers.IO) {
        ensureDefaultPresetsInternal()
        presetDao.getActivePreset()?.toDomain(json)
    }

    override suspend fun getSecrets(presetId: String): PresetSecrets? = withContext(Dispatchers.IO) {
        secretStore.getSecrets(presetId)
    }

    override suspend fun savePreset(preset: AppPreset, secrets: PresetSecrets?): Unit = withContext(Dispatchers.IO) {
        val entity = PresetEntity.fromDomain(preset, json)
        presetDao.insertOrUpdate(entity)
        if (secrets != null) {
            secretStore.saveSecrets(preset.id, secrets)
        }
        Log.d(TAG, "Saved preset: ${preset.name} (${preset.id})")
    }

    override suspend fun setActivePreset(presetId: String): Unit = withContext(Dispatchers.IO) {
        presetDao.setActivePreset(presetId)
        Log.i(TAG, "Activated preset ID: $presetId")
    }

    override suspend fun deletePreset(presetId: String): Boolean = withContext(Dispatchers.IO) {
        val existing = presetDao.getPresetById(presetId)
        if (existing != null) {
            if (existing.isActive) {
                // Cannot delete active preset if it's the only one, or switch to first available
                val all = presetDao.getAllPresets()
                val fallback = all.firstOrNull { it.id != presetId }
                if (fallback != null) {
                    presetDao.setActivePreset(fallback.id)
                }
            }
            presetDao.deleteById(presetId)
            secretStore.deleteSecrets(presetId)
            Log.d(TAG, "Deleted preset: $presetId")
            true
        } else {
            false
        }
    }

    override suspend fun duplicatePreset(sourceId: String, newName: String): AppPreset? = withContext(Dispatchers.IO) {
        val source = presetDao.getPresetById(sourceId)?.toDomain(json) ?: return@withContext null
        val newId = UUID.randomUUID().toString()
        val duplicated = source.copy(
            id = newId,
            name = newName.ifBlank { "${source.name} (Copy)" },
            isActive = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        // Duplicate entity
        presetDao.insertOrUpdate(PresetEntity.fromDomain(duplicated, json))

        // Duplicate secrets if any
        val existingSecrets = secretStore.getSecrets(sourceId)
        if (existingSecrets != null) {
            secretStore.saveSecrets(newId, existingSecrets)
        }

        Log.d(TAG, "Duplicated preset ${source.name} -> ${duplicated.name}")
        duplicated
    }

    override suspend fun exportPresetJson(presetId: String, includeSecrets: Boolean): String? = withContext(Dispatchers.IO) {
        val preset = getPresetById(presetId) ?: return@withContext null
        val secrets = if (includeSecrets) getSecrets(presetId) else null
        val exportable = ExportablePreset(
            version = 1,
            preset = preset.copy(isActive = false),
            secrets = secrets,
            exportedAt = System.currentTimeMillis()
        )
        try {
            json.encodeToString(exportable)
        } catch (e: Exception) {
            Log.e(TAG, "Error serializing preset for export", e)
            null
        }
    }

    override suspend fun importPresetJson(jsonString: String): Result<AppPreset> = withContext(Dispatchers.IO) {
        try {
            val exportable = json.decodeFromString<ExportablePreset>(jsonString)
            val newId = UUID.randomUUID().toString()
            val importedPreset = exportable.preset.copy(
                id = newId,
                name = "${exportable.preset.name} (Imported)",
                isActive = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            presetDao.insertOrUpdate(PresetEntity.fromDomain(importedPreset, json))

            if (exportable.secrets != null) {
                secretStore.saveSecrets(newId, exportable.secrets)
            }

            Log.i(TAG, "Successfully imported preset: ${importedPreset.name}")
            Result.success(importedPreset)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import preset from JSON", e)
            Result.failure(e)
        }
    }

    override suspend fun ensureDefaultPresets() = withContext(Dispatchers.IO) {
        ensureDefaultPresetsInternal()
    }

    private suspend fun ensureDefaultPresetsInternal() {
        if (presetDao.getCount() == 0) {
            Log.i(TAG, "Seeding default environment presets...")

            val devId = "preset_dev_default"
            val devPreset = AppPreset(
                id = devId,
                name = "Development",
                environment = PresetEnvironment.DEVELOPMENT,
                description = "Default local sandbox with debug tools and test backend",
                configuration = PresetConfiguration(
                    supabaseUrl = SupabaseDefaults.DEFAULT_URL,
                    firebaseProjectId = "callog-dev",
                    firebaseAppId = "1:100000000001:android:devappcallog001",
                    storageBucket = "callog-dev-recordings.appspot.com",
                    gcmSenderId = "100000000001",
                    apiBaseUrl = "https://dev-api.callog.internal",
                    features = mapOf(
                        PresetConfiguration.FEATURE_DEV_DIAGNOSTICS to true,
                        PresetConfiguration.FEATURE_CALL_SIMULATOR to true,
                        PresetConfiguration.FEATURE_DEBUG_LOGGING to true,
                        PresetConfiguration.FEATURE_EXPERIMENTAL_CALL_UI to true,
                        PresetConfiguration.FEATURE_FAKE_RECORDINGS to true,
                        PresetConfiguration.FEATURE_NETWORK_INSPECTOR to true
                    )
                ),
                isActive = true
            )
            val devSecrets = PresetSecrets(
                supabaseAnonKey = SupabaseDefaults.DEFAULT_ANON_KEY,
                firebaseApiKey = "AIzaSyDevKeySample00000000000000000000",
                apiKey = "dev_api_key_sample"
            )

            val stagingId = "preset_staging_default"
            val stagingPreset = AppPreset(
                id = stagingId,
                name = "Staging",
                environment = PresetEnvironment.STAGING,
                description = "QA & staging verification environment",
                configuration = PresetConfiguration(
                    supabaseUrl = "https://staging-callog.supabase.co",
                    firebaseProjectId = "callog-staging",
                    firebaseAppId = "1:200000000002:android:stagingcallog02",
                    storageBucket = "callog-staging-recordings.appspot.com",
                    gcmSenderId = "200000000002",
                    apiBaseUrl = "https://staging-api.callog.internal",
                    features = mapOf(
                        PresetConfiguration.FEATURE_DEV_DIAGNOSTICS to true,
                        PresetConfiguration.FEATURE_CALL_SIMULATOR to true,
                        PresetConfiguration.FEATURE_DEBUG_LOGGING to true,
                        PresetConfiguration.FEATURE_EXPERIMENTAL_CALL_UI to false,
                        PresetConfiguration.FEATURE_FAKE_RECORDINGS to false,
                        PresetConfiguration.FEATURE_NETWORK_INSPECTOR to true
                    )
                ),
                isActive = false
            )
            val stagingSecrets = PresetSecrets(
                supabaseAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.staging",
                firebaseApiKey = "AIzaSyStagingKeySample00000000000000000",
                apiKey = "staging_api_key_sample"
            )

            val prodId = "preset_prod_default"
            val prodPreset = AppPreset(
                id = prodId,
                name = "Production",
                environment = PresetEnvironment.PRODUCTION,
                description = "Live customer environment with strict security",
                configuration = PresetConfiguration(
                    supabaseUrl = "https://prod-callog.supabase.co",
                    firebaseProjectId = "callog-prod",
                    firebaseAppId = "",
                    storageBucket = "callog-prod-recordings.appspot.com",
                    gcmSenderId = "",
                    apiBaseUrl = "https://api.callog.com",
                    features = mapOf(
                        PresetConfiguration.FEATURE_DEV_DIAGNOSTICS to false,
                        PresetConfiguration.FEATURE_CALL_SIMULATOR to false,
                        PresetConfiguration.FEATURE_DEBUG_LOGGING to false,
                        PresetConfiguration.FEATURE_EXPERIMENTAL_CALL_UI to false,
                        PresetConfiguration.FEATURE_FAKE_RECORDINGS to false,
                        PresetConfiguration.FEATURE_NETWORK_INSPECTOR to false
                    )
                ),
                isActive = false
            )
            val prodSecrets = PresetSecrets(
                supabaseAnonKey = "",
                firebaseApiKey = "",
                apiKey = ""
            )

            presetDao.insertAll(
                listOf(
                    PresetEntity.fromDomain(devPreset, json),
                    PresetEntity.fromDomain(stagingPreset, json),
                    PresetEntity.fromDomain(prodPreset, json)
                )
            )

            secretStore.saveSecrets(devId, devSecrets)
            secretStore.saveSecrets(stagingId, stagingSecrets)
            secretStore.saveSecrets(prodId, prodSecrets)

            Log.i(TAG, "Default environment presets seeded successfully.")
        }
    }
}
