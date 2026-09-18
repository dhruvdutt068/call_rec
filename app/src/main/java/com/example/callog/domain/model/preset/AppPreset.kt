package com.example.callog.domain.model.preset

import kotlinx.serialization.Serializable

@Serializable
enum class PresetEnvironment(
    val label: String,
    val description: String,
    val iconName: String
) {
    DEVELOPMENT("Development", "Local development & testing sandbox", "Code"),
    STAGING("Staging", "QA / pre-production validation", "Science"),
    PRODUCTION("Production", "Live customer-facing backend", "RocketLaunch"),
    CUSTOM("Custom", "User-defined custom endpoints & keys", "Settings");

    companion object {
        fun fromString(value: String?): PresetEnvironment {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEVELOPMENT
        }
    }
}

@Serializable
data class PresetConfiguration(
    val supabaseUrl: String? = null,
    val firebaseProjectId: String? = null,
    val firebaseAppId: String? = null,
    val storageBucket: String? = null,
    val gcmSenderId: String? = null,
    val databaseUrl: String? = null,
    val apiBaseUrl: String? = null,
    val features: Map<String, Boolean> = defaultFeatureFlags()
) {
    companion object {
        fun defaultFeatureFlags(): Map<String, Boolean> {
            return mapOf(
                FEATURE_DEV_DIAGNOSTICS to true,
                FEATURE_CALL_SIMULATOR to true,
                FEATURE_DEBUG_LOGGING to true,
                FEATURE_EXPERIMENTAL_CALL_UI to false,
                FEATURE_FAKE_RECORDINGS to false,
                FEATURE_NETWORK_INSPECTOR to true
            )
        }

        const val FEATURE_DEV_DIAGNOSTICS = "dev_diagnostics"
        const val FEATURE_CALL_SIMULATOR = "call_simulator"
        const val FEATURE_DEBUG_LOGGING = "debug_logging"
        const val FEATURE_EXPERIMENTAL_CALL_UI = "experimental_call_ui"
        const val FEATURE_FAKE_RECORDINGS = "fake_recordings"
        const val FEATURE_NETWORK_INSPECTOR = "network_inspector"
    }
}

@Serializable
data class PresetSecrets(
    val supabaseAnonKey: String? = null,
    val firebaseApiKey: String? = null,
    val apiKey: String? = null,
    val extraSecrets: Map<String, String> = emptyMap()
)

@Serializable
data class AppPreset(
    val id: String,
    val name: String,
    val environment: PresetEnvironment,
    val description: String? = null,
    val configuration: PresetConfiguration = PresetConfiguration(),
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class ExportablePreset(
    val version: Int = 1,
    val preset: AppPreset,
    val secrets: PresetSecrets? = null,
    val exportedAt: Long = System.currentTimeMillis()
)
