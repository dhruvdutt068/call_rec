package com.example.callog.data.repository

import android.util.Log
import com.example.callog.core.config.FirebaseDefaults
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
        val stored = secretStore.getSecrets(presetId)
        if (stored != null && !stored.supabaseAnonKey.isNullOrBlank() &&
            (presetId != "preset_dev_default" || stored.supabaseAnonKey == SupabaseDefaults.DEV_ANON_KEY) &&
            (presetId != "preset_prod_default" || stored.supabaseAnonKey == SupabaseDefaults.PROD_ANON_KEY)) {
            stored
        } else {
            if (presetId == "preset_prod_default") {
                PresetSecrets(
                    supabaseAnonKey = SupabaseDefaults.PROD_ANON_KEY,
                    firebaseApiKey = "AIzaSyDLqHOgAdLBz9__Un_BI0ZMwz5l2ucJnKo",
                    apiKey = ""
                ).also { secretStore.saveSecrets(presetId, it) }
            } else if (presetId == "preset_dev_default") {
                PresetSecrets(
                    supabaseAnonKey = SupabaseDefaults.DEV_ANON_KEY,
                    firebaseApiKey = "AIzaSyBtlY7EoO6PgPUCMjNR55K88H2v665qQgQ",
                    apiKey = "dev_api_key_sample"
                ).also { secretStore.saveSecrets(presetId, it) }
            } else {
                stored
            }
        }
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
        val devId = "preset_dev_default"
        val devPreset = AppPreset(
            id = devId,
            name = "Development",
            environment = PresetEnvironment.DEVELOPMENT,
            description = "Default local sandbox with debug tools and test backend",
            configuration = PresetConfiguration(
                supabaseUrl = SupabaseDefaults.DEV_URL,
                firebaseProjectId = FirebaseDefaults.DEV_CONFIG.projectId,
                firebaseAppId = FirebaseDefaults.DEV_CONFIG.appId,
                storageBucket = FirebaseDefaults.DEV_CONFIG.storageBucket,
                gcmSenderId = FirebaseDefaults.DEV_CONFIG.gcmSenderId,
                databaseUrl = FirebaseDefaults.DEV_CONFIG.databaseUrl,
                firebaseDatabaseId = FirebaseDefaults.DEV_CONFIG.databaseId,
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
            supabaseAnonKey = SupabaseDefaults.DEV_ANON_KEY,
            firebaseApiKey = FirebaseDefaults.DEV_CONFIG.apiKey,
            apiKey = "dev_api_key_sample"
        )

        val prodId = "preset_prod_default"
        val prodPreset = AppPreset(
            id = prodId,
            name = "Production",
            environment = PresetEnvironment.PRODUCTION,
            description = "Live customer environment with strict security",
            configuration = PresetConfiguration(
                supabaseUrl = SupabaseDefaults.PROD_URL,
                firebaseProjectId = FirebaseDefaults.PROD_CONFIG.projectId,
                firebaseAppId = FirebaseDefaults.PROD_CONFIG.appId,
                storageBucket = FirebaseDefaults.PROD_CONFIG.storageBucket,
                gcmSenderId = FirebaseDefaults.PROD_CONFIG.gcmSenderId,
                databaseUrl = FirebaseDefaults.PROD_CONFIG.databaseUrl,
                firebaseDatabaseId = FirebaseDefaults.PROD_CONFIG.databaseId,
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
            supabaseAnonKey = SupabaseDefaults.PROD_ANON_KEY,
            firebaseApiKey = FirebaseDefaults.PROD_CONFIG.apiKey,
            apiKey = ""
        )

        if (presetDao.getCount() == 0) {
            Log.i(TAG, "Seeding default environment presets...")

            presetDao.insertAll(
                listOf(
                    PresetEntity.fromDomain(devPreset, json),
                    PresetEntity.fromDomain(prodPreset, json)
                )
            )

            secretStore.saveSecrets(devId, devSecrets)
            secretStore.saveSecrets(prodId, prodSecrets)

            Log.i(TAG, "Default environment presets seeded successfully.")
        } else {
            // Self-heal existing default presets if they contain obsolete credentials
            val existingDevEntity = presetDao.getPresetById(devId)
            if (existingDevEntity != null) {
                val existingDev = existingDevEntity.toDomain(json)
                if (existingDev.configuration.firebaseProjectId != FirebaseDefaults.DEV_CONFIG.projectId ||
                    existingDev.configuration.supabaseUrl != SupabaseDefaults.DEV_URL) {
                    val updatedDev = devPreset.copy(isActive = existingDev.isActive)
                    presetDao.insertOrUpdate(PresetEntity.fromDomain(updatedDev, json))
                    secretStore.saveSecrets(devId, devSecrets)
                    Log.i(TAG, "Self-healed preset_dev_default to verified credentials.")
                }
            }
            val existingProdEntity = presetDao.getPresetById(prodId)
            if (existingProdEntity != null) {
                val existingProd = existingProdEntity.toDomain(json)
                if (existingProd.configuration.firebaseProjectId != FirebaseDefaults.PROD_CONFIG.projectId ||
                    existingProd.configuration.supabaseUrl != SupabaseDefaults.PROD_URL ||
                    existingProd.configuration.firebaseDatabaseId != FirebaseDefaults.PROD_CONFIG.databaseId) {
                    val updatedProd = prodPreset.copy(
                        isActive = existingProd.isActive,
                        configuration = prodPreset.configuration.copy(
                            features = existingProd.configuration.features
                        )
                    )
                    presetDao.insertOrUpdate(PresetEntity.fromDomain(updatedProd, json))
                    secretStore.saveSecrets(prodId, prodSecrets)
                    Log.i(TAG, "Self-healed preset_prod_default to verified credentials with call-logs Firestore database.")
                }
            }

            // Always ensure default presets have valid non-blank secrets in secretStore
            val prodSec = secretStore.getSecrets(prodId)
            if (prodSec == null || prodSec.supabaseAnonKey.isNullOrBlank() || prodSec.supabaseAnonKey != SupabaseDefaults.PROD_ANON_KEY) {
                secretStore.saveSecrets(prodId, prodSecrets)
                Log.i(TAG, "Restored default secrets for preset_prod_default.")
            }
            val devSec = secretStore.getSecrets(devId)
            if (devSec == null || devSec.supabaseAnonKey.isNullOrBlank() || devSec.supabaseAnonKey != SupabaseDefaults.DEV_ANON_KEY) {
                secretStore.saveSecrets(devId, devSecrets)
                Log.i(TAG, "Restored default secrets for preset_dev_default.")
            }

            // Ensure Development is the active preset if no preset is active or if dev was active pointing to obsolete host
            val currentActive = presetDao.getActivePreset()
            if (currentActive == null || (currentActive.id == devId && currentActive.supabaseUrl?.contains("qizrtmvgcwxuycbpkeua") == true)) {
                presetDao.setActivePreset(devId)
                Log.i(TAG, "Defaulted active preset to Development (preset_dev_default).")
            }
        }
    }
}
