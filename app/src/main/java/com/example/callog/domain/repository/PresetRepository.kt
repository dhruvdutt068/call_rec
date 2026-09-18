package com.example.callog.domain.repository

import com.example.callog.domain.model.preset.AppPreset
import com.example.callog.domain.model.preset.PresetSecrets
import kotlinx.coroutines.flow.Flow

interface PresetRepository {
    fun getPresetsFlow(): Flow<List<AppPreset>>
    fun getActivePresetFlow(): Flow<AppPreset?>
    suspend fun getPresets(): List<AppPreset>
    suspend fun getPresetById(id: String): AppPreset?
    suspend fun getActivePreset(): AppPreset?
    suspend fun getSecrets(presetId: String): PresetSecrets?
    suspend fun savePreset(preset: AppPreset, secrets: PresetSecrets? = null)
    suspend fun setActivePreset(presetId: String)
    suspend fun deletePreset(presetId: String): Boolean
    suspend fun duplicatePreset(sourceId: String, newName: String): AppPreset?
    suspend fun exportPresetJson(presetId: String, includeSecrets: Boolean): String?
    suspend fun importPresetJson(jsonString: String): Result<AppPreset>
    suspend fun ensureDefaultPresets()
}
