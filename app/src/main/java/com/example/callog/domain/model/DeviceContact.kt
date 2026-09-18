package com.example.callog.domain.model

/**
 * Phone number from Android ContactsContract.
 */
data class DevicePhoneNumber(
    val number: String,
    val normalizedNumber: String,
    val type: String = "MOBILE"
)

/**
 * Android ContactsContract local device contact.
 * Strictly decoupled from canonical Person entity.
 */
data class DeviceContact(
    val androidContactId: String,
    val displayName: String,
    val phoneNumbers: List<DevicePhoneNumber>,
    val emails: List<String> = emptyList(),
    val photoUri: String? = null,
    val isFavorite: Boolean = false
)
