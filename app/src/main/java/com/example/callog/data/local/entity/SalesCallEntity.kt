package com.example.callog.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents a single sales interaction log.
 *
 * - Salesperson fields are populated from the device owner settings
 *   (Settings → Device Configuration: phone number & owner name).
 * - Buyer fields come from the call log contact matched at logging time.
 * - [callType] mirrors the values used in [CallEntity]: INCOMING, OUTGOING, MISSED, REJECTED.
 * - [callId] is a foreign-key reference to [CallEntity.id] so the record
 *   is always traceable back to the original call log entry.
 */

@Entity(
    tableName = "sales_calls",
    indices = [
        Index(value = ["callId"], unique = true),
        Index(value = ["salespersonPhone"]),
        Index(value = ["buyerPhone"])
    ]
)
data class SalesCallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // ── Salesperson (device owner, fetched from Settings) ──────────────────
    val salespersonPhone: String,
    val salespersonName: String,

    // ── Buyer (contact on the other end of the call) ────────────────────────
    val buyerPhone: String,
    val buyerName: String?,        // Nullable if unknown contact

    // ── Call metadata ───────────────────────────────────────────────────────
    val callType: String,          // INCOMING | OUTGOING | MISSED | REJECTED
    val callId: Long,              // References CallEntity.id
    val duration: Int,             // in seconds

    // ── Timestamps ──────────────────────────────────────────────────────────
    val createdAt: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),

    // ── Sync tracking ───────────────────────────────────────────────────────
    val syncStatus: String = "PENDING", // PENDING | SYNCED | FAILED
    val syncError: String? = null
)
