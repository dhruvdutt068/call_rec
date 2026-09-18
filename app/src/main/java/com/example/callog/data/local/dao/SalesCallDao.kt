package com.example.callog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.callog.data.local.entity.SalesCallEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SalesCallDao {

    /** Insert or replace a sales call record (unique per callId). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalesCall(salesCall: SalesCallEntity)

    /** All sales call records, newest first. */
    @Query("SELECT * FROM sales_calls ORDER BY createdAt DESC")
    fun getAllSalesCalls(): Flow<List<SalesCallEntity>>

    /** Look up the record tied to a specific call log entry. */
    @Query("SELECT * FROM sales_calls WHERE callId = :callId LIMIT 1")
    suspend fun getSalesCallByCallId(callId: Long): SalesCallEntity?

    /** All calls made/received by a particular salesperson. */
    @Query("SELECT * FROM sales_calls WHERE salespersonPhone = :phone ORDER BY createdAt DESC")
    fun getSalesCallsBySalesperson(phone: String): Flow<List<SalesCallEntity>>

    /** All calls involving a specific buyer phone number. */
    @Query("SELECT * FROM sales_calls WHERE buyerPhone = :phone ORDER BY createdAt DESC")
    fun getSalesCallsByBuyer(phone: String): Flow<List<SalesCallEntity>>

    /** Delete a single record by its primary key. */
    @Query("DELETE FROM sales_calls WHERE id = :id")
    suspend fun deleteSalesCall(id: Long)

    /** Delete all records (for data reset). */
    @Query("DELETE FROM sales_calls")
    suspend fun clearAll()

    /** Fetch all pending sales call records. */
    @Query("SELECT * FROM sales_calls WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSalesCalls(): List<SalesCallEntity>

    /** Update sync status and error message for a specific record. */
    @Query("UPDATE sales_calls SET syncStatus = :status, syncError = :error WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: String, error: String? = null)

    /** Reset all sales calls sync status to PENDING. */
    @Query("UPDATE sales_calls SET syncStatus = 'PENDING'")
    suspend fun resetSyncStatus()
}
