package com.example.callog.data.remote

import android.util.Log
import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.data.local.entity.SalesCallEntity
import com.example.callog.data.remote.model.SupabaseSalesCall
import com.example.callog.data.repository.FirestoreRepositoryImpl
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseService @Inject constructor() {
    private val TAG = "SupabaseService"

    private val supabaseClient = createSupabaseClient(
        supabaseUrl = "https://qizrtmvgcwxuycbpkeua.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFpenJ0bXZnY3d4dXljYnBrZXVhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM3NTAzNTMsImV4cCI6MjA5OTMyNjM1M30.wLHCz8zv79ySmPSnI6Z-WSdD7s9qvzCxVxYQOnIh5WU"
    ) {
        install(Postgrest)
    }

    suspend fun syncSalesCalls(calls: List<SalesCallEntity>): Result<Unit> {
        return try {
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

            Log.i(TAG, "Syncing ${supabaseCalls.size} calls to Supabase...")
            
            // Log payload objects right before sending
            supabaseCalls.forEach { call ->
                Log.d("SUPABASE", "Uploading call to Supabase: $call")
            }
            
            // Perform bulk upsert to Supabase
            val response = supabaseClient.from("sales_calls").upsert(supabaseCalls) {
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
