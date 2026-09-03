package com.example.callog.data.remote

import android.content.Context
import android.util.Log
import com.example.callog.core.config.SupabaseDefaults
import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.data.local.entity.SalesCallEntity
import com.example.callog.data.remote.model.SupabaseSalesCall
import com.example.callog.data.repository.FirestoreRepositoryImpl
import com.example.callog.domain.model.SupabaseConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "SupabaseService"
    private val PREFS_NAME = "supabase_config_prefs"

    @Volatile
    private var currentClient: SupabaseClient? = null

    private fun getSharedPreferences() =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSavedConfig(): SupabaseConfig? {
        val prefs = getSharedPreferences()
        val url = prefs.getString("supabase_url", null)
        val key = prefs.getString("supabase_key", null)
        return if (!url.isNullOrBlank() && !key.isNullOrBlank()) {
            SupabaseConfig(url, key)
        } else {
            null
        }
    }

    fun getActiveConfig(): SupabaseConfig {
        val saved = getSavedConfig()
        return saved ?: SupabaseConfig(
            url = SupabaseDefaults.DEFAULT_URL,
            apiKey = SupabaseDefaults.DEFAULT_ANON_KEY
        )
    }

    fun isCustomConfigActive(): Boolean {
        return getSavedConfig() != null
    }

    fun saveConfig(config: SupabaseConfig?) {
        val prefs = getSharedPreferences()
        prefs.edit().apply {
            if (config != null) {
                putString("supabase_url", config.url.trim())
                putString("supabase_key", config.apiKey.trim())
            } else {
                remove("supabase_url")
                remove("supabase_key")
            }
            apply()
        }
        currentClient = null // Invalidate client to force re-instantiation on next call
    }

    fun resetToDefaults() {
        saveConfig(null)
    }

    private fun getClient(): SupabaseClient {
        return currentClient ?: synchronized(this) {
            currentClient ?: run {
                val active = getActiveConfig()
                createSupabaseClient(
                    supabaseUrl = active.url,
                    supabaseKey = active.apiKey
                ) {
                    install(Postgrest)
                }.also { currentClient = it }
            }
        }
    }

    suspend fun testSupabaseConnection(url: String, key: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val testClient = createSupabaseClient(
                supabaseUrl = url.trim(),
                supabaseKey = key.trim()
            ) {
                install(Postgrest)
            }

            // Test by attempting a select limit 1 on sales_calls table
            testClient.from(SupabaseDefaults.TABLE_SALES_CALLS)
                .select {
                    limit(1)
                }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Supabase connection test failed", e)
            Result.failure(e)
        }
    }

    suspend fun syncSalesCalls(calls: List<SalesCallEntity>): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val supabaseCalls = calls.map { entity ->
                SupabaseSalesCall(
                    salespersonPhone = FirestoreRepositoryImpl.normalizePhoneNumber(entity.salespersonPhone),
                    salespersonName = entity.salespersonName,
                    buyerPhone = entity.buyerPhone,
                    buyerName = entity.buyerName,
                    callType = entity.callType,
                    callId = entity.callId,
                    duration = entity.duration,
                    createdAt = entity.createdAt
                )
            }

            Log.i(TAG, "Syncing ${supabaseCalls.size} calls to Supabase (${getActiveConfig().url})...")
            
            // Log payload objects right before sending
            supabaseCalls.forEach { call ->
                Log.d("SUPABASE", "Uploading call to Supabase: $call")
            }
            
            // Perform bulk upsert to Supabase
            val response = getClient().from(SupabaseDefaults.TABLE_SALES_CALLS).upsert(supabaseCalls) {
                onConflict = "salesperson_phone,call_id"
            }
            
            Log.d("SUPABASE", "Upsert completed successfully. Response = $response")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SUPABASE", "Error syncing sales calls to Supabase", e)
            DeveloperLogger.error("SUPABASE_UPLOAD_FAILED", "Failed syncing sales calls to Supabase", e)
            Result.failure(e)
        }
    }
}
