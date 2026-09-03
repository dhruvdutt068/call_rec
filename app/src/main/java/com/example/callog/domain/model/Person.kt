package com.example.callog.domain.model

data class Person(
    val id: String,
    val displayName: String,
    val companyName: String? = null,
    val notes: String? = null,
    val phoneNumbers: List<PhoneNumber> = emptyList(),
    val aliases: List<ContactAlias> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class PhoneNumber(
    val id: String,
    val personId: String,
    val phoneNumber: String,
    val normalizedNumber: String,
    val phoneType: String = "MOBILE",
    val isPrimary: Boolean = false
)

data class ContactAlias(
    val id: String,
    val personId: String,
    val deviceId: String,
    val androidContactId: String,
    val aliasName: String,
    val phoneNumber: String,
    val normalizedNumber: String
)

data class Device(
    val id: String,
    val deviceName: String,
    val devicePhone: String,
    val deviceIdentifier: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastSyncAt: Long? = null
)

data class PersonResolutionResult(
    val personId: String,
    val phoneNumberId: String? = null,
    val aliasId: String? = null,
    val isFromRemote: Boolean = false
)
