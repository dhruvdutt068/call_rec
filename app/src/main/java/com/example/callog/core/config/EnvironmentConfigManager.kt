package com.example.callog.core.config

import android.content.Context
import android.util.Log
import com.example.callog.domain.model.AppEnvironment
import com.example.callog.domain.model.FirebaseConfig
import com.example.callog.domain.model.SupabaseConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnvironmentConfigManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "EnvironmentConfigMgr"
    private val PREFS_NAME = "app_environment_prefs"

    private val KEY_ACTIVE_ENV = "key_active_environment"

    // Key prefixes for environments
    private fun getPrefix(env: AppEnvironment) = env.name.lowercase()

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _activeEnvironment = MutableStateFlow(loadActiveEnvironment())
    val activeEnvironment: StateFlow<AppEnvironment> = _activeEnvironment.asStateFlow()

    private val environmentChangeListeners = mutableListOf<() -> Unit>()

    private fun loadActiveEnvironment(): AppEnvironment {
        val flavorDefault = try {
            val resId = context.resources.getIdentifier("app_environment", "string", context.packageName)
            if (resId != 0) {
                AppEnvironment.fromString(context.getString(resId))
            } else {
                AppEnvironment.DEVELOPMENT
            }
        } catch (e: Exception) {
            AppEnvironment.DEVELOPMENT
        }
        val raw = prefs.getString(KEY_ACTIVE_ENV, flavorDefault.name)
        return AppEnvironment.fromString(raw)
    }

    fun getActiveEnvironment(): AppEnvironment {
        return _activeEnvironment.value
    }

    fun setActiveEnvironment(env: AppEnvironment) {
        if (_activeEnvironment.value != env) {
            prefs.edit().putString(KEY_ACTIVE_ENV, env.name).apply()
            _activeEnvironment.value = env
            Log.d(TAG, "Switched active environment to: ${env.name}")
            notifyListeners()
        }
    }

    fun addOnEnvironmentChangeListener(listener: () -> Unit) {
        synchronized(environmentChangeListeners) {
            environmentChangeListeners.add(listener)
        }
    }

    private fun notifyListeners() {
        synchronized(environmentChangeListeners) {
            environmentChangeListeners.forEach { listener ->
                try {
                    listener.invoke()
                } catch (e: Exception) {
                    Log.e(TAG, "Error invoking environment change listener", e)
                }
            }
        }
    }

    // --- Supabase Configs ---

    fun getSupabaseConfig(env: AppEnvironment): SupabaseConfig {
        val prefix = getPrefix(env)
        val url = prefs.getString("${prefix}_supabase_url", null)
        val key = prefs.getString("${prefix}_supabase_key", null)

        if (url?.contains("vxineppqpqslqjqvnlzv") == true || url?.contains("qizrtmvgcwxuycbpkeua") == true) {
            prefs.edit().remove("${prefix}_supabase_url").remove("${prefix}_supabase_key").apply()
            return if (env == AppEnvironment.DEVELOPMENT) {
                SupabaseConfig(SupabaseDefaults.DEV_URL, SupabaseDefaults.DEV_ANON_KEY)
            } else {
                SupabaseConfig(SupabaseDefaults.PROD_URL, SupabaseDefaults.PROD_ANON_KEY)
            }
        }

        return if (!url.isNullOrBlank() && !key.isNullOrBlank()) {
            SupabaseConfig(url.trim(), key.trim())
        } else {
            if (env == AppEnvironment.DEVELOPMENT) {
                SupabaseConfig(SupabaseDefaults.DEV_URL, SupabaseDefaults.DEV_ANON_KEY)
            } else {
                SupabaseConfig(SupabaseDefaults.PROD_URL, SupabaseDefaults.PROD_ANON_KEY)
            }
        }
    }

    fun getActiveSupabaseConfig(): SupabaseConfig {
        val env = getActiveEnvironment()
        return getSupabaseConfig(env)
    }

    fun saveSupabaseConfig(env: AppEnvironment, config: SupabaseConfig?) {
        val prefix = getPrefix(env)
        prefs.edit().apply {
            if (config != null && config.url.isNotBlank() && config.apiKey.isNotBlank()) {
                putString("${prefix}_supabase_url", config.url.trim())
                putString("${prefix}_supabase_key", config.apiKey.trim())
            } else {
                remove("${prefix}_supabase_url")
                remove("${prefix}_supabase_key")
            }
            apply()
        }
        notifyListeners()
    }

    fun isCustomSupabaseConfig(env: AppEnvironment): Boolean {
        val prefix = getPrefix(env)
        return prefs.contains("${prefix}_supabase_url")
    }

    // --- Firebase Configs ---

    fun getFirebaseConfig(env: AppEnvironment): FirebaseConfig? {
        val prefix = getPrefix(env)
        val projectId = prefs.getString("${prefix}_firebase_project_id", null)
        val apiKey = prefs.getString("${prefix}_firebase_api_key", null)
        val appId = prefs.getString("${prefix}_firebase_app_id", null)
        val storageBucket = prefs.getString("${prefix}_firebase_storage_bucket", null)
        val gcmSenderId = prefs.getString("${prefix}_firebase_gcm_sender_id", null)
        val databaseUrl = prefs.getString("${prefix}_firebase_database_url", null)
        val databaseId = prefs.getString("${prefix}_firebase_database_id", null)

        // Sanitize stale/buggy saved preferences from previous builds
        if (env == AppEnvironment.PRODUCTION && projectId == "allset-491218") {
            prefs.edit().remove("${prefix}_firebase_project_id").apply()
            return defaultFirebaseConfig(env)
        }
        if (env == AppEnvironment.DEVELOPMENT && projectId == "restaurant-manager-185bd") {
            prefs.edit().remove("${prefix}_firebase_project_id").apply()
            return defaultFirebaseConfig(env)
        }

        return if (!projectId.isNullOrBlank() && !apiKey.isNullOrBlank() && !appId.isNullOrBlank()) {
            val resolvedDbId = databaseId?.trim()?.ifBlank { null }
                ?: if (env == AppEnvironment.PRODUCTION) "call-logs" else null

            FirebaseConfig(
                projectId = projectId.trim(),
                apiKey = apiKey.trim(),
                appId = appId.trim(),
                storageBucket = storageBucket?.trim()?.ifBlank { null },
                gcmSenderId = gcmSenderId?.trim()?.ifBlank { null },
                databaseUrl = databaseUrl?.trim()?.ifBlank { null },
                databaseId = resolvedDbId
            )
        } else {
            defaultFirebaseConfig(env)
        }
    }

    companion object {
        val DEV_API_KEY: String get() = FirebaseDefaults.DEV_CONFIG.apiKey
        val PROD_API_KEY: String get() = FirebaseDefaults.PROD_CONFIG.apiKey
    }

    private fun defaultFirebaseConfig(env: AppEnvironment): FirebaseConfig {
        return FirebaseDefaults.forEnvironment(env)
    }

    fun getActiveFirebaseConfig(): FirebaseConfig? {
        return getFirebaseConfig(getActiveEnvironment())
    }

    fun saveFirebaseConfig(env: AppEnvironment, config: FirebaseConfig?) {
        val prefix = getPrefix(env)
        prefs.edit().apply {
            if (config != null && config.projectId.isNotBlank() && config.apiKey.isNotBlank() && config.appId.isNotBlank()) {
                putString("${prefix}_firebase_project_id", config.projectId.trim())
                putString("${prefix}_firebase_api_key", config.apiKey.trim())
                putString("${prefix}_firebase_app_id", config.appId.trim())
                if (!config.storageBucket.isNullOrBlank()) {
                    putString("${prefix}_firebase_storage_bucket", config.storageBucket.trim())
                } else {
                    remove("${prefix}_firebase_storage_bucket")
                }
                if (!config.gcmSenderId.isNullOrBlank()) {
                    putString("${prefix}_firebase_gcm_sender_id", config.gcmSenderId.trim())
                } else {
                    remove("${prefix}_firebase_gcm_sender_id")
                }
                if (!config.databaseUrl.isNullOrBlank()) {
                    putString("${prefix}_firebase_database_url", config.databaseUrl.trim())
                } else {
                    remove("${prefix}_firebase_database_url")
                }
                if (!config.databaseId.isNullOrBlank()) {
                    putString("${prefix}_firebase_database_id", config.databaseId.trim())
                } else {
                    remove("${prefix}_firebase_database_id")
                }
            } else {
                remove("${prefix}_firebase_project_id")
                remove("${prefix}_firebase_api_key")
                remove("${prefix}_firebase_app_id")
                remove("${prefix}_firebase_storage_bucket")
                remove("${prefix}_firebase_gcm_sender_id")
                remove("${prefix}_firebase_database_url")
                remove("${prefix}_firebase_database_id")
            }
            apply()
        }
        notifyListeners()
    }
}
