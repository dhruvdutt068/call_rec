package com.example.callog.domain.provider

import com.example.callog.domain.model.preset.AppPreset
import com.example.callog.domain.model.preset.PresetSecrets
import kotlinx.coroutines.flow.StateFlow

interface ActivePresetProvider {
    val activePresetFlow: StateFlow<AppPreset?>
    suspend fun getActivePreset(): AppPreset
    suspend fun getActiveSecrets(): PresetSecrets?
    suspend fun isFeatureEnabled(featureKey: String): Boolean
    fun addOnActivePresetChangeListener(listener: () -> Unit)
    fun removeOnActivePresetChangeListener(listener: () -> Unit)
}
