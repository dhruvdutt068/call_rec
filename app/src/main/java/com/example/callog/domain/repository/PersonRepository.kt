package com.example.callog.domain.repository

import com.example.callog.domain.model.ContactAlias
import com.example.callog.domain.model.Device
import com.example.callog.domain.model.Person
import com.example.callog.domain.model.PhoneNumber
import kotlinx.coroutines.flow.Flow

interface PersonRepository {
    fun getPersonByIdFlow(id: String): Flow<Person?>
    suspend fun getPersonById(id: String): Person?
    fun getAllPeopleFlow(): Flow<List<Person>>
    suspend fun getAllPeople(): List<Person>
    fun searchPeopleFlow(query: String): Flow<List<Person>>
    suspend fun searchPeople(query: String): List<Person>

    suspend fun findPersonByNormalizedPhone(normalizedPhone: String): Person?
    suspend fun insertOrUpdatePerson(person: Person)
    suspend fun savePhoneNumber(phoneNumber: PhoneNumber)
    suspend fun saveAlias(alias: ContactAlias)

    suspend fun getOrCreateCurrentDevice(): Device
    suspend fun syncContactsFromDevice(): List<Person>
    suspend fun syncGlobalContactsFromSupabase(): Result<List<Person>>
    suspend fun createGlobalContact(name: String, phone: String, company: String?, notes: String?): Result<Person>
    suspend fun resolveAndAttachContact(deviceId: String, contact: com.example.callog.data.provider.ContactDto): com.example.callog.domain.model.PersonResolutionResult
}
