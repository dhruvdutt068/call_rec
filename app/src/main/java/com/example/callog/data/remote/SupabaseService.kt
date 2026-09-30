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
        val activePreset = activePresetProvider.activePresetFlow.value
        if (activePreset != null && !activePreset.configuration.supabaseUrl.isNullOrBlank()) {
            val secrets = activePresetProvider.getActiveSecretsSync()
            val defaultKey = if (activePreset.environment == com.example.callog.domain.model.preset.PresetEnvironment.PRODUCTION || 
                activePreset.configuration.supabaseUrl == SupabaseDefaults.PROD_URL) {
                SupabaseDefaults.PROD_ANON_KEY
            } else {
                SupabaseDefaults.DEV_ANON_KEY
            }
            val key = secrets?.supabaseAnonKey?.ifBlank { null } ?: defaultKey
            return SupabaseConfig(activePreset.configuration.supabaseUrl.trim(), key.trim())
        }

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

    fun getClient(): SupabaseClient {
        return currentClient ?: synchronized(this) {
            currentClient ?: run {
                val config = getActiveConfig()
                Log.d(TAG, "Initializing SupabaseClient for environment: ${environmentConfigManager.getActiveEnvironment()} -> ${config.url}")
                createSupabaseClient(
                    supabaseUrl = config.url,
                    supabaseKey = config.apiKey
                ) {
                    install(Postgrest)
                }.also { currentClient = it }
            }
        }
    }

    suspend fun testSupabaseConnection(url: String, key: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val resolvedUrl = url.trim()
            val defaultKey = if (resolvedUrl == SupabaseDefaults.PROD_URL ||
                resolvedUrl.contains("nmhgdhehsfcootlqwumw", ignoreCase = true) ||
                resolvedUrl.contains("vxineppqpqslqjqvnlzv", ignoreCase = true)) {
                SupabaseDefaults.PROD_ANON_KEY
            } else {
                SupabaseDefaults.DEV_ANON_KEY
            }
            val resolvedKey = key.trim().ifBlank { defaultKey }

            if (resolvedUrl.isBlank() || resolvedKey.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Supabase URL and API Key must not be blank"))
            }

            val testClient = createSupabaseClient(
                supabaseUrl = resolvedUrl,
                supabaseKey = resolvedKey
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
                    createdAt = toUtcIsoString(entity.createdAt),
                    personId = entity.personId
                )
            }.distinctBy { "${it.salespersonPhone}_${it.callId}" }

            Log.i(TAG, "Syncing ${supabaseCalls.size} calls to Supabase in chunks (${getActiveConfig().url})...")
            
            // Perform chunked bulk upsert to Supabase
            val chunkSize = 100
            val chunks = supabaseCalls.chunked(chunkSize)
            chunks.forEachIndexed { index, chunk ->
                Log.d("SUPABASE", "Uploading chunk ${index + 1}/${chunks.size} (${chunk.size} calls) to Supabase")
                getClient().from(SupabaseDefaults.TABLE_SALES_CALLS).upsert(chunk) {
                    onConflict = "salesperson_phone,call_id"
                }
            }
            
            Log.d("SUPABASE", "Upsert of all ${supabaseCalls.size} calls completed successfully.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SUPABASE", "Error syncing sales calls to Supabase", e)
            DeveloperLogger.error("SUPABASE_UPLOAD_FAILED", "Failed syncing sales calls to Supabase", e)
            Result.failure(e)
        }
    }

    private fun toUtcIsoString(raw: String): String {
        return try {
            if (raw.endsWith("Z")) return raw
            val epoch = raw.toLongOrNull()
            if (epoch != null) {
                return formatTimestamp(epoch)
            }
            val legacySdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val parsed = legacySdf.parse(raw)
            if (parsed != null) {
                formatTimestamp(parsed.time)
            } else {
                raw
            }
        } catch (e: Exception) {
            raw
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

    private fun sanitizeLeadStatusForDb(status: String?): String {
        return when (status?.trim()?.uppercase()) {
            "HOT" -> "HOT"
            "WARM" -> "WARM"
            "COLD" -> "COLD"
            else -> "UNKNOWN"
        }
    }

    private fun sanitizeLeadPriorityForDb(priority: String?): String {
        return when (priority?.trim()?.uppercase()) {
            "LOW" -> "LOW"
            "HIGH" -> "HIGH"
            "URGENT" -> "URGENT"
            else -> "MEDIUM"
        }
    }

    suspend fun syncLeads(leads: List<com.example.callog.data.local.entity.LeadEntity>): Result<Unit> = withContext(Dispatchers.IO) {
        if (leads.isEmpty()) return@withContext Result.success(Unit)
        return@withContext try {
            val supabaseLeads = leads.distinctBy { it.id }.map {
                com.example.callog.data.remote.model.SupabaseLead(
                    id = it.id,
                    personId = it.personId,
                    status = sanitizeLeadStatusForDb(it.status),
                    priority = sanitizeLeadPriorityForDb(it.priority),
                    feedback = it.feedback,
                    feedbackRating = it.feedbackRating,
                    notes = it.notes,
                    ownerId = it.ownerId,
                    source = it.source.uppercase(),
                    nextFollowUpAt = it.nextFollowUpAt?.let { ts -> formatTimestamp(ts) },
                    isArchived = it.isArchived,
                    archivedAt = it.archivedAt?.let { ts -> formatTimestamp(ts) },
                    createdAt = formatTimestamp(it.createdAt),
                    updatedAt = formatTimestamp(it.updatedAt)
                )
            }
            getClient().from(SupabaseDefaults.TABLE_LEADS).upsert(supabaseLeads) {
                onConflict = "id"
            }
            Result.success(Unit)
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("PGRST205") || msg.contains("Could not find the table", ignoreCase = true)) {
                Log.w(TAG, "Table '${SupabaseDefaults.TABLE_LEADS}' not found in Supabase schema cache; skipping lead sync.")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Error syncing leads to Supabase", e)
                Result.failure(e)
            }
        }
    }

    suspend fun updateLeadStatusInSupabase(leadId: String, status: String, updatedAt: Long): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            getClient().from(SupabaseDefaults.TABLE_LEADS).update(
                mapOf(
                    "status" to sanitizeLeadStatusForDb(status),
                    "updated_at" to formatTimestamp(updatedAt)
                )
            ) {
                filter {
                    eq("id", leadId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("PGRST205") || msg.contains("Could not find the table", ignoreCase = true)) {
                Log.w(TAG, "Table '${SupabaseDefaults.TABLE_LEADS}' not found in Supabase schema cache; skipping status update.")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Error updating lead status in Supabase for $leadId", e)
                Result.failure(e)
            }
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

    suspend fun fetchGlobalLeads(): Result<List<com.example.callog.data.remote.model.SupabaseLead>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val leads = getClient().from(SupabaseDefaults.TABLE_LEADS)
                .select()
                .decodeList<com.example.callog.data.remote.model.SupabaseLead>()
            Result.success(leads)
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("PGRST205") || msg.contains("Could not find the table", ignoreCase = true)) {
                Log.w(TAG, "Table '${SupabaseDefaults.TABLE_LEADS}' not found in Supabase schema cache; skipping.")
                Result.success(emptyList())
            } else {
                Log.e(TAG, "Error fetching global leads from Supabase", e)
                Result.failure(e)
            }
        }
    }

    suspend fun fetchLeadByPersonId(personId: String): Result<com.example.callog.data.remote.model.SupabaseLead?> = withContext(Dispatchers.IO) {
        return@withContext try {
            val leads = getClient().from(SupabaseDefaults.TABLE_LEADS)
                .select {
                    filter {
                        eq("person_id", personId)
                    }
                    limit(1)
                }
                .decodeList<com.example.callog.data.remote.model.SupabaseLead>()
            Result.success(leads.firstOrNull())
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("PGRST205") || msg.contains("Could not find the table", ignoreCase = true)) {
                Log.w(TAG, "Table '${SupabaseDefaults.TABLE_LEADS}' not found in Supabase schema cache; returning null.")
                Result.success(null)
            } else {
                Log.e(TAG, "Error fetching lead by personId from Supabase", e)
                Result.failure(e)
            }
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

    suspend fun fetchLeadStages(): Result<List<com.example.callog.data.remote.model.SupabaseLeadStage>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val stages = getClient().from(SupabaseDefaults.TABLE_LEAD_STAGES)
                .select {
                    filter {
                        eq("is_active", true)
                    }
                    order(column = "sort_order", order = io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                }
                .decodeList<com.example.callog.data.remote.model.SupabaseLeadStage>()
            Result.success(stages)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching lead stages from Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun fetchLeadStatuses(): Result<List<com.example.callog.data.remote.model.SupabaseLeadStatus>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val statuses = getClient().from(SupabaseDefaults.TABLE_LEAD_STATUSES)
                .select {
                    filter {
                        eq("is_active", true)
                    }
                    order(column = "sort_order", order = io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                }
                .decodeList<com.example.callog.data.remote.model.SupabaseLeadStatus>()
            Result.success(statuses)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching lead statuses from Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun fetchLeadsMaster(stageId: Long? = null, limit: Long = 100): Result<List<com.example.callog.data.remote.model.SupabaseLeadMaster>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val leads = getClient().from(SupabaseDefaults.TABLE_LEADS_MASTER)
                .select {
                    if (stageId != null) {
                        filter {
                            eq("fk_lead_stage", stageId)
                        }
                    }
                    order(column = "updated_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(limit)
                }
                .decodeList<com.example.callog.data.remote.model.SupabaseLeadMaster>()
            Result.success(leads)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching leads_master from Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun updateLeadMasterStage(leadId: Long, newStageId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val now = formatTimestamp(System.currentTimeMillis())
            getClient().from(SupabaseDefaults.TABLE_LEADS_MASTER).update(
                mapOf(
                    "fk_lead_stage" to newStageId,
                    "updated_at" to now
                )
            ) {
                filter {
                    eq("id", leadId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating lead_stage in leads_master for leadId=$leadId", e)
            Result.failure(e)
        }
    }

    suspend fun createLeadTask(task: com.example.callog.data.remote.model.SupabaseLeadTask): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            getClient().from(SupabaseDefaults.TABLE_LEAD_TASKS).insert(task)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating lead task in Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun createLeadNote(note: com.example.callog.data.remote.model.SupabaseLeadNote): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            getClient().from(SupabaseDefaults.TABLE_LEAD_NOTES).insert(note)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating lead note in Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun fetchSalesCalls(limit: Long = 500): Result<List<com.example.callog.data.remote.model.SupabaseSalesCall>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val calls = getClient().from(SupabaseDefaults.TABLE_SALES_CALLS)
                .select {
                    order(column = "created_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(limit)
                }
                .decodeList<com.example.callog.data.remote.model.SupabaseSalesCall>()
            Result.success(calls)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching sales_calls from Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun fetchCallsLog(limit: Long = 500): Result<List<com.example.callog.data.crm.model.ClientCallsLogDto>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val logs = getClient().from(SupabaseDefaults.TABLE_CALLS_LOG)
                .select {
                    limit(limit)
                }
                .decodeList<com.example.callog.data.crm.model.ClientCallsLogDto>()
            Result.success(logs)
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("PGRST205") || msg.contains("Could not find the table", ignoreCase = true)) {
                Log.w(TAG, "Table '${SupabaseDefaults.TABLE_CALLS_LOG}' not found in Supabase schema cache; returning empty.")
                Result.success(emptyList())
            } else {
                Log.e(TAG, "Error fetching calls_log from Supabase", e)
                Result.failure(e)
            }
        }
    }

    suspend fun fetchLeadTasks(leadId: Long? = null, limit: Long = 100): Result<List<com.example.callog.data.remote.model.SupabaseLeadTask>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val tasks = getClient().from(SupabaseDefaults.TABLE_LEAD_TASKS)
                .select {
                    if (leadId != null) {
                        filter {
                            eq("lead_id", leadId)
                        }
                    }
                    order(column = "due_at", order = io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                    limit(limit)
                }
                .decodeList<com.example.callog.data.remote.model.SupabaseLeadTask>()
            Result.success(tasks)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching lead_tasks from Supabase", e)
            Result.failure(e)
        }
    }

    suspend fun fetchLeadNotes(leadId: Long? = null, limit: Long = 100): Result<List<com.example.callog.data.remote.model.SupabaseLeadNote>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val notes = getClient().from(SupabaseDefaults.TABLE_LEAD_NOTES)
                .select {
                    if (leadId != null) {
                        filter {
                            eq("lead_id", leadId)
                        }
                    }
                    order(column = "created_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(limit)
                }
                .decodeList<com.example.callog.data.remote.model.SupabaseLeadNote>()
            Result.success(notes)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching lead_notes from Supabase", e)
            Result.failure(e)
        }
    }
}
