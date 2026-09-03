package com.example.callog.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabasePerson(
    @SerialName("id") val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("company_name") val companyName: String? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class SupabasePhoneNumber(
    @SerialName("id") val id: String,
    @SerialName("person_id") val personId: String,
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("normalized_number") val normalizedNumber: String,
    @SerialName("phone_type") val phoneType: String,
    @SerialName("is_primary") val isPrimary: Boolean,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class SupabaseContactAlias(
    @SerialName("id") val id: String,
    @SerialName("person_id") val personId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("android_contact_id") val androidContactId: String,
    @SerialName("alias_name") val aliasName: String,
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("normalized_number") val normalizedNumber: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class SupabaseDevice(
    @SerialName("id") val id: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("device_phone") val devicePhone: String,
    @SerialName("device_identifier") val deviceIdentifier: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("last_sync_at") val lastSyncAt: String? = null
)

@Serializable
data class PersonResolutionRequest(
    @SerialName("p_device_id") val deviceId: String,
    @SerialName("p_android_contact_id") val androidContactId: String,
    @SerialName("p_alias_name") val aliasName: String,
    @SerialName("p_phone_number") val phoneNumber: String,
    @SerialName("p_normalized_number") val normalizedNumber: String,
    @SerialName("p_device_name") val deviceName: String? = null,
    @SerialName("p_device_phone") val devicePhone: String? = null,
    @SerialName("p_device_identifier") val deviceIdentifier: String? = null
)

@Serializable
data class PersonResolutionResponse(
    @SerialName("person_id") val personId: String,
    @SerialName("phone_number_id") val phoneNumberId: String? = null,
    @SerialName("alias_id") val aliasId: String? = null
)
