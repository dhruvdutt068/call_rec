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
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
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
        if (!url.isNullOrBlank() && (url.contains("qizrtmvgcwxuycbpkeua") || url.isBlank())) {
            // Clean up legacy URL to adopt updated SupabaseDefaults
            prefs.edit().remove("supabase_url").remove("supabase_key").apply()
            return null
        }
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
                    createdAt = entity.createdAt,
                    personId = entity.personId
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

    private fun formatTimestamp(epochMillis: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date(epochMillis))
    }

    suspend fun syncPeople(people: List<com.example.callog.data.local.entity.PersonEntity>): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val supabasePeople = people.map {
                com.example.callog.data.remote.model.SupabasePerson(
                    id = it.id,
                    displayName = it.displayName,
                    companyName = it.companyName,
                    notes = it.notes,
                    createdAt = formatTimestamp(it.createdAt),
                    updatedAt = formatTimestamp(it.updatedAt)
                )
            }
            getClient().from(SupabaseDefaults.TABLE_PEOPLE).upsert(supabasePeople) {
                onConflict = "id"
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing people to Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun syncPhoneNumbers(numbers: List<com.example.callog.data.local.entity.PhoneNumberEntity>): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val supabaseNumbers = numbers.map {
                com.example.callog.data.remote.model.SupabasePhoneNumber(
                    id = it.id,
                    personId = it.personId,
                    phoneNumber = it.phoneNumber,
                    normalizedNumber = it.normalizedNumber,
                    phoneType = it.phoneType,
                    isPrimary = it.isPrimary,
                    createdAt = formatTimestamp(it.createdAt)
                )
            }
            getClient().from(SupabaseDefaults.TABLE_PHONE_NUMBERS).upsert(supabaseNumbers) {
                onConflict = "id"
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing phone numbers to Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun syncContactAliases(aliases: List<com.example.callog.data.local.entity.ContactAliasEntity>): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val supabaseAliases = aliases.map {
                com.example.callog.data.remote.model.SupabaseContactAlias(
                    id = it.id,
                    personId = it.personId,
                    deviceId = it.deviceId,
                    androidContactId = it.androidContactId,
                    aliasName = it.aliasName,
                    phoneNumber = it.phoneNumber,
                    normalizedNumber = it.normalizedNumber,
                    createdAt = formatTimestamp(it.createdAt)
                )
            }
            getClient().from(SupabaseDefaults.TABLE_CONTACT_ALIASES).upsert(supabaseAliases) {
                onConflict = "id"
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing contact aliases to Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun syncDevices(devices: List<com.example.callog.data.local.entity.DeviceEntity>): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val supabaseDevices = devices.map {
                com.example.callog.data.remote.model.SupabaseDevice(
                    id = it.id,
                    deviceName = it.deviceName,
                    devicePhone = it.devicePhone,
                    deviceIdentifier = it.deviceIdentifier,
                    createdAt = formatTimestamp(it.createdAt),
                    updatedAt = formatTimestamp(it.updatedAt),
                    lastSyncAt = it.lastSyncAt?.let { ts -> formatTimestamp(ts) }
                )
            }
            getClient().from(SupabaseDefaults.TABLE_DEVICES).upsert(supabaseDevices) {
                onConflict = "id"
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing devices to Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun resolveAndAttachContactRemote(
        request: com.example.callog.data.remote.model.PersonResolutionRequest
    ): Result<com.example.callog.data.remote.model.PersonResolutionResponse> = withContext(Dispatchers.IO) {
        return@withContext try {
            val client = getClient()
            val response = client.from("resolve_and_attach_contact")
            // In postgrest-kt, rpc can be invoked via postgrest.rpc(...)
            val rpcResult = client.postgrest.rpc(
                function = "resolve_and_attach_contact",
                parameters = request
            ).decodeAs<com.example.callog.data.remote.model.PersonResolutionResponse>()

            Log.i(TAG, "Server resolved identity for contact: person_id=${rpcResult.personId}")
            Result.success(rpcResult)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing resolve_and_attach_contact RPC on Supabase", e)
            Result.failure(e)
        }
    }
}
