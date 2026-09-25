package com.example.callog.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callog.data.remote.SupabaseService
import com.example.callog.domain.model.preset.*
import com.example.callog.domain.usecase.preset.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed class PresetUiEvent {
    data class ShowToast(val message: String) : PresetUiEvent()
    data class RequestProductionConfirmation(val preset: AppPreset) : PresetUiEvent()
    data class PresetExported(val json: String, val presetName: String) : PresetUiEvent()
}

@HiltViewModel
class PresetViewModel @Inject constructor(
    private val getPresetsUseCase: GetPresetsUseCase,
    private val getActivePresetUseCase: GetActivePresetUseCase,
    private val setActivePresetUseCase: SetActivePresetUseCase,
    private val savePresetUseCase: SavePresetUseCase,
    private val deletePresetUseCase: DeletePresetUseCase,
    private val duplicatePresetUseCase: DuplicatePresetUseCase,
    private val importExportPresetUseCase: ImportExportPresetUseCase,
    private val supabaseService: SupabaseService,
    private val firestoreService: com.example.callog.data.remote.FirestoreService
) : ViewModel() {

    val presets: StateFlow<List<AppPreset>> = getPresetsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activePreset: StateFlow<AppPreset?> = getActivePresetUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _activeSecrets = MutableStateFlow<PresetSecrets?>(null)
    val activeSecrets: StateFlow<PresetSecrets?> = _activeSecrets.asStateFlow()

    private val _uiEvents = MutableSharedFlow<PresetUiEvent>()
    val uiEvents: SharedFlow<PresetUiEvent> = _uiEvents.asSharedFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<Pair<Boolean, String>?>(null)
    val connectionTestResult: StateFlow<Pair<Boolean, String>?> = _connectionTestResult.asStateFlow()

    init {
        viewModelScope.launch {
            activePreset.collect { active ->
                if (active != null) {
                    _activeSecrets.value = getActivePresetUseCase.getSecrets(active.id)
                } else {
                    _activeSecrets.value = null
                }
            }
        }
    }

    fun requestActivatePreset(preset: AppPreset) {
        if (preset.environment == PresetEnvironment.PRODUCTION && !preset.isActive) {
            viewModelScope.launch {
                _uiEvents.emit(PresetUiEvent.RequestProductionConfirmation(preset))
            }
        } else {
            activatePreset(preset.id)
        }
    }

    fun activatePreset(presetId: String) {
        viewModelScope.launch {
            try {
                setActivePresetUseCase(presetId)
                _uiEvents.emit(PresetUiEvent.ShowToast("Environment preset switched successfully"))
            } catch (e: Exception) {
                _uiEvents.emit(PresetUiEvent.ShowToast("Failed to switch preset: ${e.message}"))
            }
        }
    }

    fun savePreset(preset: AppPreset, secrets: PresetSecrets?) {
        viewModelScope.launch {
            try {
                savePresetUseCase(preset, secrets)
                _uiEvents.emit(PresetUiEvent.ShowToast("Saved configuration for ${preset.name}"))
            } catch (e: Exception) {
                _uiEvents.emit(PresetUiEvent.ShowToast("Error saving preset: ${e.message}"))
            }
        }
    }

    fun duplicatePreset(sourcePreset: AppPreset, customName: String? = null) {
        viewModelScope.launch {
            try {
                val newName = customName ?: "${sourcePreset.name} (Copy)"
                val duplicated = duplicatePresetUseCase(sourcePreset.id, newName)
                if (duplicated != null) {
                    _uiEvents.emit(PresetUiEvent.ShowToast("Created duplicate: ${duplicated.name}"))
                }
            } catch (e: Exception) {
                _uiEvents.emit(PresetUiEvent.ShowToast("Failed to duplicate preset: ${e.message}"))
            }
        }
    }

    fun deletePreset(presetId: String) {
        viewModelScope.launch {
            try {
                val success = deletePresetUseCase(presetId)
                if (success) {
                    _uiEvents.emit(PresetUiEvent.ShowToast("Preset deleted"))
                }
            } catch (e: Exception) {
                _uiEvents.emit(PresetUiEvent.ShowToast("Failed to delete preset: ${e.message}"))
            }
        }
    }

    fun exportPreset(presetId: String, includeSecrets: Boolean) {
        viewModelScope.launch {
            val target = presets.value.firstOrNull { it.id == presetId } ?: return@launch
            val json = importExportPresetUseCase.exportJson(presetId, includeSecrets)
            if (json != null) {
                _uiEvents.emit(PresetUiEvent.PresetExported(json, target.name))
            } else {
                _uiEvents.emit(PresetUiEvent.ShowToast("Failed to export preset"))
            }
        }
    }

    fun importPreset(jsonString: String) {
        viewModelScope.launch {
            val result = importExportPresetUseCase.importJson(jsonString)
            result.onSuccess { imported ->
                _uiEvents.emit(PresetUiEvent.ShowToast("Imported preset: ${imported.name}"))
            }.onFailure { error ->
                _uiEvents.emit(PresetUiEvent.ShowToast("Import failed: ${error.message}"))
            }
        }
    }

    fun testSupabaseConnection(url: String, key: String) {
        viewModelScope.launch {
            if (url.isBlank() || key.isBlank()) {
                _connectionTestResult.value = Pair(false, "URL and API Key cannot be blank")
                return@launch
            }
            _isTestingConnection.value = true
            _connectionTestResult.value = null
            val result = supabaseService.testSupabaseConnection(url, key)
            _isTestingConnection.value = false
            if (result.isSuccess) {
                _connectionTestResult.value = Pair(true, "Successfully connected to Supabase!")
            } else {
                _connectionTestResult.value = Pair(false, result.exceptionOrNull()?.message ?: "Connection test failed")
            }
        }
    }

    fun testFirebaseConnection(config: com.example.callog.domain.model.FirebaseConfig) {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionTestResult.value = null
            val result = firestoreService.testFirebaseConnection(config)
            _isTestingConnection.value = false
            if (result.isSuccess) {
                _connectionTestResult.value = Pair(true, "Successfully connected to Firebase Firestore & Storage!")
            } else {
                _connectionTestResult.value = Pair(false, result.exceptionOrNull()?.message ?: "Firebase test failed")
            }
        }
    }

    fun testActiveConnections(preset: AppPreset, secrets: PresetSecrets?) {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionTestResult.value = null
            val results = mutableListOf<String>()
            var allPassed = true

            // 1. Supabase Test if URL is present
            if (!preset.configuration.supabaseUrl.isNullOrBlank()) {
                val subKey = secrets?.supabaseAnonKey ?: ""
                val sbResult = supabaseService.testSupabaseConnection(preset.configuration.supabaseUrl, subKey)
                if (sbResult.isSuccess) {
                    results.add("Supabase: Connected")
                } else {
                    allPassed = false
                    results.add("Supabase Failed: ${sbResult.exceptionOrNull()?.message}")
                }
            }

            // 2. Firebase & Storage Test if Firebase Project is configured
            val fbProj = preset.configuration.firebaseProjectId?.trim()
            val fbAppId = preset.configuration.firebaseAppId?.trim()
            val fbKey = secrets?.firebaseApiKey?.trim()

            if (!fbProj.isNullOrBlank() && !fbAppId.isNullOrBlank() && !fbKey.isNullOrBlank()) {
                val fbConfig = com.example.callog.domain.model.FirebaseConfig(
                    projectId = fbProj,
                    apiKey = fbKey,
                    appId = fbAppId,
                    storageBucket = preset.configuration.storageBucket?.ifBlank { null },
                    gcmSenderId = preset.configuration.gcmSenderId?.ifBlank { null },
                    databaseUrl = preset.configuration.databaseUrl?.ifBlank { null },
                    databaseId = preset.configuration.firebaseDatabaseId?.ifBlank { null }
                )
                val fbResult = firestoreService.testFirebaseConnection(fbConfig)
                if (fbResult.isSuccess) {
                    results.add("Firebase Firestore & Storage: Connected")
                } else {
                    allPassed = false
                    results.add("Firebase Failed: ${fbResult.exceptionOrNull()?.message}")
                }
            } else {
                // If not custom configured in preset, test default Firebase app
                val fbResult = firestoreService.testFirebaseConnection(null)
                if (fbResult.isSuccess) {
                    results.add("Default Firebase & Storage: Connected")
                } else {
                    results.add("Default Firebase: Not reachable (${fbResult.exceptionOrNull()?.message})")
                }
            }

            _isTestingConnection.value = false
            val combinedMsg = results.joinToString("\n")
            _connectionTestResult.value = Pair(allPassed, combinedMsg)
        }
    }

    fun clearConnectionTestResult() {
        _connectionTestResult.value = null
    }

    suspend fun getSecretsForPreset(presetId: String): PresetSecrets? {
        return getActivePresetUseCase.getSecrets(presetId)
    }

    fun createPresetTemplate(env: PresetEnvironment): AppPreset {
        val newId = UUID.randomUUID().toString()
        val defaultFeatures = when (env) {
            PresetEnvironment.DEVELOPMENT -> mapOf(
                PresetConfiguration.FEATURE_DEV_DIAGNOSTICS to true,
                PresetConfiguration.FEATURE_CALL_SIMULATOR to true,
                PresetConfiguration.FEATURE_DEBUG_LOGGING to true,
                PresetConfiguration.FEATURE_EXPERIMENTAL_CALL_UI to true,
                PresetConfiguration.FEATURE_FAKE_RECORDINGS to true,
                PresetConfiguration.FEATURE_NETWORK_INSPECTOR to true
            )
            PresetEnvironment.PRODUCTION -> mapOf(
                PresetConfiguration.FEATURE_DEV_DIAGNOSTICS to false,
                PresetConfiguration.FEATURE_CALL_SIMULATOR to false,
                PresetConfiguration.FEATURE_DEBUG_LOGGING to false,
                PresetConfiguration.FEATURE_EXPERIMENTAL_CALL_UI to false,
                PresetConfiguration.FEATURE_FAKE_RECORDINGS to false,
                PresetConfiguration.FEATURE_NETWORK_INSPECTOR to false
            )
        }

        return AppPreset(
            id = newId,
            name = when (env) {
                PresetEnvironment.DEVELOPMENT -> "Development Sandbox"
                PresetEnvironment.PRODUCTION -> "Production Backend"
            },
            environment = env,
            description = when (env) {
                PresetEnvironment.DEVELOPMENT -> "Local testing and debugging sandbox"
                PresetEnvironment.PRODUCTION -> "Live production database"
            },
            configuration = PresetConfiguration(
                supabaseUrl = if (env == PresetEnvironment.DEVELOPMENT) com.example.callog.core.config.SupabaseDefaults.DEV_URL else com.example.callog.core.config.SupabaseDefaults.PROD_URL,
                firebaseProjectId = "allset-244ab",
                storageBucket = "allset-244ab.firebasestorage.app",
                apiBaseUrl = "",
                features = defaultFeatures
            ),
            isActive = false
        )
    }
}
