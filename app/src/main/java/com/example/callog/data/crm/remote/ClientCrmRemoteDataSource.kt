package com.example.callog.data.crm.remote

import android.util.Log
import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.data.crm.model.ClientCallsLogDto
import com.example.callog.data.crm.model.ClientLeadCommunicationDto
import com.example.callog.data.remote.SupabaseService
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientCrmRemoteDataSource @Inject constructor(
    private val supabaseService: SupabaseService
) {
    companion object {
        private const val TAG = "ClientCrmRemoteDataSource"
        const val TABLE_CALLS_LOG = "calls_log"
        const val TABLE_LEAD_COMMUNICATIONS = "lead_communications"
    }

    private fun isTableMissing(e: Exception): Boolean {
        val msg = e.message.orEmpty()
        return msg.contains("PGRST205") || msg.contains("Could not find the table", ignoreCase = true)
    }

    suspend fun findLeadByPhone(crmPhoneNumber: String): Result<ClientCallsLogDto?> = withContext(Dispatchers.IO) {
        return@withContext try {
            val results = supabaseService.getClient().from(TABLE_CALLS_LOG)
                .select {
                    filter {
                        eq("phone_number", crmPhoneNumber)
                    }
                    limit(1)
                }
                .decodeList<ClientCallsLogDto>()

            Result.success(results.firstOrNull())
        } catch (e: Exception) {
            if (isTableMissing(e)) {
                Log.w(TAG, "Table '$TABLE_CALLS_LOG' not found in Supabase schema cache; skipping lookup.")
                Result.success(null)
            } else {
                Log.w(TAG, "External CRM lookup for $crmPhoneNumber returned error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun upsertCallsLog(dto: ClientCallsLogDto): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            supabaseService.getClient().from(TABLE_CALLS_LOG).upsert(dto) {
                onConflict = "phone_number"
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (isTableMissing(e)) {
                Log.w(TAG, "Table '$TABLE_CALLS_LOG' not found in Supabase schema cache; skipping upsert.")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to upsert client calls_log for ${dto.phoneNumber}", e)
                DeveloperLogger.warning("CRM_UPSERT_FAILED", "Failed upserting client calls_log: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun insertLeadCommunication(dto: ClientLeadCommunicationDto): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            supabaseService.getClient().from(TABLE_LEAD_COMMUNICATIONS).upsert(dto)
            Result.success(Unit)
        } catch (e: Exception) {
            if (isTableMissing(e)) {
                Log.w(TAG, "Table '$TABLE_LEAD_COMMUNICATIONS' not found in Supabase schema cache; skipping insert.")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed inserting client lead_communication for ${dto.phoneNumber}", e)
                DeveloperLogger.warning("CRM_COMM_FAILED", "Failed inserting client lead_communication: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun insertBatchLeadCommunications(dtos: List<ClientLeadCommunicationDto>): Result<Unit> = withContext(Dispatchers.IO) {
        if (dtos.isEmpty()) return@withContext Result.success(Unit)
        return@withContext try {
            supabaseService.getClient().from(TABLE_LEAD_COMMUNICATIONS).upsert(dtos)
            Result.success(Unit)
        } catch (e: Exception) {
            if (isTableMissing(e)) {
                Log.w(TAG, "Table '$TABLE_LEAD_COMMUNICATIONS' not found in Supabase schema cache; skipping batch insert.")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed batch inserting ${dtos.size} client lead_communications", e)
                DeveloperLogger.warning("CRM_COMM_BATCH_FAILED", "Failed batch inserting lead_communications: ${e.message}")
                Result.failure(e)
            }
        }
    }
}
