package com.example.callog.data.provider

import android.util.Log
import com.example.callog.core.config.SupabaseDefaults
import com.example.callog.domain.model.preset.AppPreset
import com.example.callog.domain.model.preset.PresetConfiguration
import com.example.callog.domain.model.preset.PresetEnvironment
import com.example.callog.domain.model.preset.PresetSecrets
import com.example.callog.domain.provider.ActivePresetProvider
import com.example.callog.domain.repository.PresetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivePresetProviderImpl @Inject constructor(
    private val presetRepository: PresetRepository
) : ActivePresetProvider {

    private val TAG = "ActivePresetProvider"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activePresetFlow = MutableStateFlow<AppPreset?>(null)
    override val activePresetFlow: StateFlow<AppPreset?> = _activePresetFlow.asStateFlow()

    private val listeners = mutableListOf<() -> Unit>()

    init {
        scope.launch {
            presetRepository.ensureDefaultPresets()
            presetRepository.getActivePresetFlow().collect { preset ->
                val previous = _activePresetFlow.value
                _activePresetFlow.value = preset
                if (previous != null && preset != null && (previous.id != preset.id || previous.updatedAt != preset.updatedAt)) {
                    Log.d(TAG, "Active preset updated: ${preset.name} (${preset.id})")
                    notifyListeners()
                } else if (previous == null && preset != null) {
                    Log.d(TAG, "Initial active preset loaded: ${preset.name}")
                    notifyListeners()
                }
            }
        }
    }

    override suspend fun getActivePreset(): AppPreset {
        return _activePresetFlow.value ?: presetRepository.getActivePreset() ?: defaultFallbackPreset()
    }

    override suspend fun getActiveSecrets(): PresetSecrets? {
        val current = getActivePreset()
        return presetRepository.getSecrets(current.id)
    }

    override suspend fun isFeatureEnabled(featureKey: String): Boolean {
        val current = getActivePreset()
        return current.configuration.features[featureKey] ?: true
    }

    override fun addOnActivePresetChangeListener(listener: () -> Unit) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    override fun removeOnActivePresetChangeListener(listener: () -> Unit) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun notifyListeners() {
        val toNotify: List<() -> Unit>
        synchronized(listeners) {
            toNotify = listeners.toList()
        }
        toNotify.forEach { listener ->
            try {
                listener.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Error invoking active preset change listener", e)
            }
        }
    }

    private fun defaultFallbackPreset(): AppPreset {
        return AppPreset(
            id = "fallback_dev",
            name = "Development",
            environment = PresetEnvironment.DEVELOPMENT,
            description = "Default fallback development profile",
            configuration = PresetConfiguration(
                supabaseUrl = SupabaseDefaults.DEFAULT_URL,
                firebaseProjectId = "callog-dev",
                firebaseAppId = "1:100000000001:android:devappcallog001",
                storageBucket = "callog-dev-recordings.appspot.com",
                gcmSenderId = "100000000001",
                apiBaseUrl = "https://dev-api.callog.internal"
            ),
            isActive = true
        )
    }
}
