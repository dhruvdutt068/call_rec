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
        val raw = prefs.getString(KEY_ACTIVE_ENV, AppEnvironment.DEVELOPMENT.name)
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

        return if (!projectId.isNullOrBlank() && !apiKey.isNullOrBlank() && !appId.isNullOrBlank()) {
            FirebaseConfig(
                projectId = projectId.trim(),
                apiKey = apiKey.trim(),
                appId = appId.trim(),
                storageBucket = storageBucket?.trim()?.ifBlank { null },
                gcmSenderId = gcmSenderId?.trim()?.ifBlank { null },
                databaseUrl = databaseUrl?.trim()?.ifBlank { null },
                databaseId = databaseId?.trim()?.ifBlank { null }
            )
        } else {
            // Built-in defaults for Development and Production/Deployment
            if (env == AppEnvironment.DEVELOPMENT) {
                FirebaseConfig(
                    projectId = "allset-491218",
                    apiKey = "AIzaSyBtlY7EoO6PgPUCMjNR55K88H2v665qQgQ",
                    appId = "1:799427430422:android:e0f5737f12b8b9cbb20d37",
                    storageBucket = "allset-491218.firebasestorage.app",
                    gcmSenderId = "799427430422",
                    databaseUrl = null,
                    databaseId = "call-logs"
                )
            } else {
                FirebaseConfig(
                    projectId = "allset-244ab",
                    apiKey = "AIzaSyDLqHOgAdLBz9__Un_BI0ZMwz5l2ucJnKo",
                    appId = "1:598633676290:android:e264397de748bf0371711b",
                    storageBucket = "allset-244ab.firebasestorage.app",
                    gcmSenderId = "598633676290",
                    databaseUrl = null,
                    databaseId = null
                )
            }
        }
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
