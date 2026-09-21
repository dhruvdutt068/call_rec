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
import com.example.callog.core.config.EnvironmentConfigManager
import com.example.callog.domain.model.AppEnvironment
import com.example.callog.domain.provider.ActivePresetProvider
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val environmentConfigManager: EnvironmentConfigManager,
    private val activePresetProvider: ActivePresetProvider
) {
    private val TAG = "SupabaseService"

    @Volatile
    private var currentClient: SupabaseClient? = null

    init {
        environmentConfigManager.addOnEnvironmentChangeListener {
            invalidateClient()
        }
        activePresetProvider.addOnActivePresetChangeListener {
            invalidateClient()
        }
    }

    fun invalidateClient() {
        currentClient = null
        Log.d(TAG, "Supabase client invalidated due to environment/preset update.")
    }

    fun getSavedConfig(): SupabaseConfig? {
        val env = environmentConfigManager.getActiveEnvironment()
        val config = environmentConfigManager.getSupabaseConfig(env)
        return if (config.url.isNotBlank() && config.apiKey.isNotBlank()) {
            config
        } else {
            null
        }
    }

    fun getActiveConfig(): SupabaseConfig {
        return environmentConfigManager.getActiveSupabaseConfig()
    }

    fun isCustomConfigActive(): Boolean {
        val env = environmentConfigManager.getActiveEnvironment()
        return environmentConfigManager.isCustomSupabaseConfig(env)
    }

    fun saveConfig(config: SupabaseConfig?, env: AppEnvironment = environmentConfigManager.getActiveEnvironment()) {
        environmentConfigManager.saveSupabaseConfig(env, config)
        invalidateClient()
    }

    fun resetToDefaults(env: AppEnvironment = environmentConfigManager.getActiveEnvironment()) {
        saveConfig(null, env)
    }

    private fun getClient(): SupabaseClient {
        return currentClient ?: synchronized(this) {
            currentClient ?: run {
                val activePreset = runBlocking { activePresetProvider.getActivePreset() }
                val secrets = runBlocking { activePresetProvider.getActiveSecrets() }
                
                val url = activePreset.configuration.supabaseUrl?.trim()?.ifBlank { null }
                    ?: getActiveConfig().url
                val key = secrets?.supabaseAnonKey?.trim()?.ifBlank { null }
                    ?: getActiveConfig().apiKey

                createSupabaseClient(
                    supabaseUrl = url,
                    supabaseKey = key
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
            val supabasePeople = people.distinctBy { it.id }.map {
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
            val supabaseNumbers = numbers.mapNotNull {
                val norm = com.example.callog.core.utils.PhoneNumberNormalizer.normalize(it.normalizedNumber.ifBlank { it.phoneNumber })
                if (norm.isBlank()) null
                else com.example.callog.data.remote.model.SupabasePhoneNumber(
                    id = it.id,
                    personId = it.personId,
                    phoneNumber = it.phoneNumber,
                    normalizedNumber = norm,
                    phoneType = it.phoneType,
                    isPrimary = it.isPrimary,
                    createdAt = formatTimestamp(it.createdAt)
                )
            }.distinctBy { it.normalizedNumber }

            if (supabaseNumbers.isNotEmpty()) {
                getClient().from(SupabaseDefaults.TABLE_PHONE_NUMBERS).upsert(supabaseNumbers) {
                    onConflict = "normalized_number"
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing phone numbers to Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun syncContactAliases(aliases: List<com.example.callog.data.local.entity.ContactAliasEntity>): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val supabaseAliases = aliases.mapNotNull {
                val norm = com.example.callog.core.utils.PhoneNumberNormalizer.normalize(it.normalizedNumber.ifBlank { it.phoneNumber })
                if (norm.isBlank()) null
                else com.example.callog.data.remote.model.SupabaseContactAlias(
                    id = it.id,
                    personId = it.personId,
                    deviceId = it.deviceId,
                    androidContactId = it.androidContactId,
                    aliasName = it.aliasName,
                    phoneNumber = it.phoneNumber,
                    normalizedNumber = norm,
                    createdAt = formatTimestamp(it.createdAt)
                )
            }.distinctBy { "${it.deviceId}_${it.normalizedNumber}" }

            if (supabaseAliases.isNotEmpty()) {
                getClient().from(SupabaseDefaults.TABLE_CONTACT_ALIASES).upsert(supabaseAliases) {
                    onConflict = "device_id,normalized_number"
                }
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

    suspend fun syncLeads(leads: List<com.example.callog.data.local.entity.LeadEntity>): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val supabaseLeads = leads.map {
                com.example.callog.data.remote.model.SupabaseLead(
                    id = it.id,
                    personId = it.personId,
                    status = it.status,
                    priority = it.priority,
                    feedback = it.feedback,
                    feedbackRating = it.feedbackRating,
                    notes = it.notes,
                    ownerId = it.ownerId,
                    source = it.source,
                    nextFollowUpAt = it.nextFollowUpAt?.let { ts -> formatTimestamp(ts) },
                    isArchived = it.isArchived,
                    archivedAt = it.archivedAt?.let { ts -> formatTimestamp(ts) },
                    createdAt = formatTimestamp(it.createdAt),
                    updatedAt = formatTimestamp(it.updatedAt)
                )
            }
            getClient().from(SupabaseDefaults.TABLE_LEADS).upsert(supabaseLeads) {
                onConflict = "person_id"
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing leads to Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun fetchGlobalPeople(): Result<List<com.example.callog.data.remote.model.SupabasePerson>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val people = getClient().from(SupabaseDefaults.TABLE_PEOPLE)
                .select()
                .decodeList<com.example.callog.data.remote.model.SupabasePerson>()
            Result.success(people)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching global people from Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun fetchGlobalPhoneNumbers(): Result<List<com.example.callog.data.remote.model.SupabasePhoneNumber>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val numbers = getClient().from(SupabaseDefaults.TABLE_PHONE_NUMBERS)
                .select()
                .decodeList<com.example.callog.data.remote.model.SupabasePhoneNumber>()
            Result.success(numbers)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching global phone numbers from Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun fetchGlobalContactAliases(): Result<List<com.example.callog.data.remote.model.SupabaseContactAlias>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val aliases = getClient().from(SupabaseDefaults.TABLE_CONTACT_ALIASES)
                .select()
                .decodeList<com.example.callog.data.remote.model.SupabaseContactAlias>()
            Result.success(aliases)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching global contact aliases from Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun fetchGlobalDevices(): Result<List<com.example.callog.data.remote.model.SupabaseDevice>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val devices = getClient().from(SupabaseDefaults.TABLE_DEVICES)
                .select()
                .decodeList<com.example.callog.data.remote.model.SupabaseDevice>()
            Result.success(devices)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching global devices from Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun fetchDeviceByIdentifier(identifier: String): Result<com.example.callog.data.remote.model.SupabaseDevice?> = withContext(Dispatchers.IO) {
        return@withContext try {
            val devices = getClient().from(SupabaseDefaults.TABLE_DEVICES)
                .select {
                    filter {
                        eq("device_identifier", identifier)
                    }
                    limit(1)
                }
                .decodeList<com.example.callog.data.remote.model.SupabaseDevice>()
            Result.success(devices.firstOrNull())
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching device by identifier from Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun insertDevice(device: com.example.callog.data.remote.model.SupabaseDevice): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            getClient().from(SupabaseDefaults.TABLE_DEVICES)
                .upsert(device)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error upserting device to Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun createGlobalPerson(
        name: String,
        phone: String,
        normalizedPhone: String,
        company: String? = null,
        notes: String? = null
    ): Result<com.example.callog.data.remote.model.SupabasePerson> = withContext(Dispatchers.IO) {
        return@withContext try {
            val norm = com.example.callog.core.utils.PhoneNumberNormalizer.normalize(if (normalizedPhone.isNotBlank()) normalizedPhone else phone)
            val client = getClient()
            val now = formatTimestamp(System.currentTimeMillis())

            if (norm.isNotBlank()) {
                // 1. Check if canonical phone already exists in Supabase
                val existingPhones = client.from(SupabaseDefaults.TABLE_PHONE_NUMBERS)
                    .select {
                        filter {
                            eq("normalized_number", norm)
                        }
                        limit(1)
                    }
                    .decodeList<com.example.callog.data.remote.model.SupabasePhoneNumber>()

                if (existingPhones.isNotEmpty()) {
                    val existingPersonId = existingPhones.first().personId
                    val existingPeople = client.from(SupabaseDefaults.TABLE_PEOPLE)
                        .select {
                            filter {
                                eq("id", existingPersonId)
                            }
                            limit(1)
                        }
                        .decodeList<com.example.callog.data.remote.model.SupabasePerson>()

                    if (existingPeople.isNotEmpty()) {
                        val existing = existingPeople.first()
                        val updated = existing.copy(
                            displayName = if (existing.displayName.isBlank() || existing.displayName == "Unknown Contact") name.trim() else existing.displayName,
                            companyName = company?.trim()?.ifBlank { null } ?: existing.companyName,
                            notes = notes?.trim()?.ifBlank { null } ?: existing.notes,
                            updatedAt = now
                        )
                        client.from(SupabaseDefaults.TABLE_PEOPLE).upsert(updated) { onConflict = "id" }
                        Log.i(TAG, "Reused existing canonical person ${existing.id} for phone ${com.example.callog.core.utils.PhoneNumberNormalizer.mask(phone)}")
                        return@withContext Result.success(updated)
                    }
                }
            }

            // 2. If number is not in Supabase, create canonical Person and Phone record
            val personId = "P" + java.util.UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
            val person = com.example.callog.data.remote.model.SupabasePerson(
                id = personId,
                displayName = name.trim().ifBlank { "New Contact" },
                companyName = company?.trim()?.ifBlank { null },
                notes = notes?.trim()?.ifBlank { null },
                createdAt = now,
                updatedAt = now
            )
            client.from(SupabaseDefaults.TABLE_PEOPLE).insert(person)

            if (norm.isNotBlank()) {
                val phoneRecord = com.example.callog.data.remote.model.SupabasePhoneNumber(
                    id = java.util.UUID.randomUUID().toString(),
                    personId = personId,
                    phoneNumber = phone.trim(),
                    normalizedNumber = norm,
                    phoneType = "PRIMARY",
                    isPrimary = true,
                    createdAt = now
                )
                client.from(SupabaseDefaults.TABLE_PHONE_NUMBERS).upsert(phoneRecord) {
                    onConflict = "normalized_number"
                }
            }

            Log.i(TAG, "Created new canonical person $personId for phone ${com.example.callog.core.utils.PhoneNumberNormalizer.mask(phone)}")
            Result.success(person)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating global person in Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun resolveAndAttachContactRemote(
        request: com.example.callog.data.remote.model.PersonResolutionRequest
    ): Result<com.example.callog.data.remote.model.PersonResolutionResponse> = withContext(Dispatchers.IO) {
        return@withContext try {
            val client = getClient()
            val sanitizedRequest = request.copy(
                normalizedNumber = com.example.callog.core.utils.PhoneNumberNormalizer.normalize(
                    request.normalizedNumber.ifBlank { request.phoneNumber }
                )
            )
            val rpcResult = client.postgrest.rpc(
                function = "resolve_and_attach_contact",
                parameters = sanitizedRequest
            ).decodeAs<com.example.callog.data.remote.model.PersonResolutionResponse>()

            Log.i(TAG, "Server resolved identity: person_id=${rpcResult.personId} for phone ${com.example.callog.core.utils.PhoneNumberNormalizer.mask(request.phoneNumber)}")
            Result.success(rpcResult)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing resolve_and_attach_contact RPC on Supabase", e)
            Result.failure(e)
        }
    }
}
