package com.example.callog.data.repository

import android.content.Context
import com.example.callog.core.utils.PhoneNumberNormalizer
import com.example.callog.data.provider.ContactsProvider
import com.example.callog.domain.model.DeviceContact
import com.example.callog.domain.model.DevicePhoneNumber
import com.example.callog.domain.repository.DeviceContactsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceContactsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val contactsProvider: ContactsProvider
) : DeviceContactsRepository {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val _contactsFlow = MutableStateFlow<List<DeviceContact>>(emptyList())
    private var hasInitialized = false

    override fun observeContacts(): Flow<List<DeviceContact>> {
        return _contactsFlow.asStateFlow().onStart {
            if (!hasInitialized) {
                refreshContacts()
            }
        }
    }

    override suspend fun getContacts(): List<DeviceContact> = withContext(ioDispatcher) {
        if (!hasInitialized || _contactsFlow.value.isEmpty()) {
            refreshContacts()
        }
        _contactsFlow.value
    }

    override suspend fun refreshContacts(): Result<Unit> = withContext(ioDispatcher) {
        try {
            val rawContacts = contactsProvider.fetchContacts()
            val mapped = rawContacts.map { dto ->
                DeviceContact(
                    androidContactId = dto.contactId,
                    displayName = dto.name,
                    phoneNumbers = dto.phoneNumbers.map { num ->
                        DevicePhoneNumber(
                            number = num,
                            normalizedNumber = PhoneNumberNormalizer.normalize(num)
                        )
                    },
                    emails = dto.emails,
                    photoUri = dto.photoUri,
                    isFavorite = dto.isFavorite
                )
            }
            _contactsFlow.value = mapped
            hasInitialized = true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getContactById(androidContactId: String): DeviceContact? = withContext(ioDispatcher) {
        val current = getContacts()
        current.find { it.androidContactId == androidContactId }
    }

    override suspend fun searchContacts(query: String): List<DeviceContact> = withContext(ioDispatcher) {
        val q = query.trim().lowercase()
        if (q.isBlank()) return@withContext getContacts()
        
        getContacts().filter { contact ->
            contact.displayName.lowercase().contains(q) ||
            contact.phoneNumbers.any { 
                it.number.contains(q) || it.normalizedNumber.contains(q) 
            }
        }
    }
}
