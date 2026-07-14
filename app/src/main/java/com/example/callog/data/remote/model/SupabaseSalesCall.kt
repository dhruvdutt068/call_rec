package com.example.callog.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseSalesCall(
    @SerialName("salesperson_phone") val salespersonPhone: String,
    @SerialName("salesperson_name") val salespersonName: String,
    @SerialName("buyer_phone") val buyerPhone: String,
    @SerialName("buyer_name") val buyerName: String?,
    @SerialName("call_type") val callType: String,
    @SerialName("call_id") val callId: Long,
    @SerialName("duration") val duration: Int,
    @SerialName("created_at") val createdAt: String
)
