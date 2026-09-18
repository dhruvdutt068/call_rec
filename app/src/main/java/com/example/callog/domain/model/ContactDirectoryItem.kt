package com.example.callog.domain.model

/**
 * Sealed interface representing an item in the Contacts Directory.
 * Prevents accidental mixing of Cloud and Device contacts at the domain and UI levels.
 */
sealed interface ContactDirectoryItem {
    val displayName: String
    val primaryPhone: String?

    /**
     * Canonical Supabase / Room CRM contact.
     */
    data class Cloud(
        val person: Person,
        override val displayName: String,
        override val primaryPhone: String?,
        val companyName: String? = person.companyName,
        val aliasCount: Int = person.aliases.size
    ) : ContactDirectoryItem

    /**
     * Local Android ContactsContract contact.
     */
    data class Device(
        val contactId: String,
        override val displayName: String,
        override val primaryPhone: String?,
        val photoUri: String? = null,
        val deviceContact: DeviceContact? = null,
        val linkedPersonId: String? = null,
        val linkedPersonName: String? = null
    ) : ContactDirectoryItem
}
