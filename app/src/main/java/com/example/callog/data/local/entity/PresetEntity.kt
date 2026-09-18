package com.example.callog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.callog.domain.model.preset.AppPreset
import com.example.callog.domain.model.preset.PresetConfiguration
import com.example.callog.domain.model.preset.PresetEnvironment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "app_presets")
data class PresetEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val environment: String,
    val description: String? = null,
    val supabaseUrl: String? = null,
    val firebaseProjectId: String? = null,
    val firebaseAppId: String? = null,
    val storageBucket: String? = null,
    val gcmSenderId: String? = null,
    val databaseUrl: String? = null,
    val apiBaseUrl: String? = null,
    val featuresJson: String = "{}",
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(json: Json = Json { ignoreUnknownKeys = true }): AppPreset {
        val featuresMap: Map<String, Boolean> = try {
            json.decodeFromString(featuresJson)
        } catch (e: Exception) {
            PresetConfiguration.defaultFeatureFlags()
        }

        return AppPreset(
            id = id,
            name = name,
            environment = PresetEnvironment.fromString(environment),
            description = description,
            configuration = PresetConfiguration(
                supabaseUrl = supabaseUrl,
                firebaseProjectId = firebaseProjectId,
                firebaseAppId = firebaseAppId,
                storageBucket = storageBucket,
                gcmSenderId = gcmSenderId,
                databaseUrl = databaseUrl,
                apiBaseUrl = apiBaseUrl,
                features = featuresMap
            ),
            isActive = isActive,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(
            preset: AppPreset,
            json: Json = Json { ignoreUnknownKeys = true }
        ): PresetEntity {
            val featuresJson = try {
                json.encodeToString(preset.configuration.features)
            } catch (e: Exception) {
                "{}"
            }

            return PresetEntity(
                id = preset.id,
                name = preset.name,
                environment = preset.environment.name,
                description = preset.description,
                supabaseUrl = preset.configuration.supabaseUrl,
                firebaseProjectId = preset.configuration.firebaseProjectId,
                firebaseAppId = preset.configuration.firebaseAppId,
                storageBucket = preset.configuration.storageBucket,
                gcmSenderId = preset.configuration.gcmSenderId,
                databaseUrl = preset.configuration.databaseUrl,
                apiBaseUrl = preset.configuration.apiBaseUrl,
                featuresJson = featuresJson,
                isActive = preset.isActive,
                createdAt = preset.createdAt,
                updatedAt = preset.updatedAt
            )
        }
    }
}
