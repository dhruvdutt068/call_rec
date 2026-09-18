package com.example.callog.domain.repository

import com.example.callog.domain.model.DeviceContact
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface providing access to local Android ContactsContract address book.
 */
interface DeviceContactsRepository {

    /**
     * Observes local device contacts as a reactive stream.
     */
    fun observeContacts(): Flow<List<DeviceContact>>

    /**
     * Retrieves current cached/fetched list of device contacts.
     */
    suspend fun getContacts(): List<DeviceContact>

    /**
     * Forces a fresh query against ContactsContract and updates the observation stream.
     */
    suspend fun refreshContacts(): Result<Unit>

    /**
     * Retrieves a single device contact by its Android Contact ID.
     */
    suspend fun getContactById(androidContactId: String): DeviceContact?

    /**
     * Searches device contacts by display name or phone number.
     */
    suspend fun searchContacts(query: String): List<DeviceContact>
}
