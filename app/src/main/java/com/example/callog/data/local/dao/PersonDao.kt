package com.example.callog.data.local.dao

import androidx.room.*
import com.example.callog.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    // --- People ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: PersonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeople(people: List<PersonEntity>)

    @Update
    suspend fun updatePerson(person: PersonEntity)

    @Query("SELECT * FROM people WHERE id = :id")
    fun getPersonByIdFlow(id: String): Flow<PersonEntity?>

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun getPersonById(id: String): PersonEntity?

    @Transaction
    @Query("SELECT * FROM people WHERE id = :id")
    fun getPersonWithDetailsByIdFlow(id: String): Flow<PersonWithDetails?>

    @Transaction
    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun getPersonWithDetailsById(id: String): PersonWithDetails?

    @Transaction
    @Query("SELECT * FROM people ORDER BY displayName ASC")
    fun getAllPeopleWithDetailsFlow(): Flow<List<PersonWithDetails>>

    @Transaction
    @Query("SELECT * FROM people ORDER BY displayName ASC")
    suspend fun getAllPeopleWithDetails(): List<PersonWithDetails>

    @Query("SELECT * FROM people WHERE syncStatus = 'PENDING'")
    suspend fun getPendingPeople(): List<PersonEntity>

    @Query("UPDATE people SET syncStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePersonSyncStatus(id: String, status: String, updatedAt: Long = System.currentTimeMillis())

    // --- Phone Numbers ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoneNumber(phoneNumber: PhoneNumberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoneNumbers(phoneNumbers: List<PhoneNumberEntity>)

    @Query("SELECT * FROM phone_numbers WHERE personId = :personId")
    suspend fun getPhoneNumbersForPerson(personId: String): List<PhoneNumberEntity>

    @Query("SELECT * FROM phone_numbers WHERE normalizedNumber = :normalizedNumber LIMIT 1")
    suspend fun findPhoneNumberByNormalized(normalizedNumber: String): PhoneNumberEntity?

    @Query("SELECT * FROM phone_numbers WHERE normalizedNumber = :normalizedNumber")
    suspend fun findPhoneNumbersByNormalized(normalizedNumber: String): List<PhoneNumberEntity>

    @Query("SELECT * FROM phone_numbers WHERE syncStatus = 'PENDING'")
    suspend fun getPendingPhoneNumbers(): List<PhoneNumberEntity>

    @Query("UPDATE phone_numbers SET syncStatus = :status WHERE id = :id")
    suspend fun updatePhoneNumberSyncStatus(id: String, status: String)

    // --- Contact Aliases ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlias(alias: ContactAliasEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAliases(aliases: List<ContactAliasEntity>)

    @Query("SELECT * FROM contact_aliases WHERE personId = :personId")
    suspend fun getAliasesForPerson(personId: String): List<ContactAliasEntity>

    @Query("SELECT * FROM contact_aliases WHERE deviceId = :deviceId")
    suspend fun getAliasesForDevice(deviceId: String): List<ContactAliasEntity>

    @Query("SELECT * FROM contact_aliases WHERE personId = :personId AND deviceId = :deviceId AND normalizedNumber = :normalizedNumber LIMIT 1")
    suspend fun findAlias(personId: String, deviceId: String, normalizedNumber: String): ContactAliasEntity?

    @Query("SELECT * FROM contact_aliases WHERE syncStatus = 'PENDING'")
    suspend fun getPendingAliases(): List<ContactAliasEntity>

    @Query("UPDATE contact_aliases SET syncStatus = :status WHERE id = :id")
    suspend fun updateAliasSyncStatus(id: String, status: String)

    // --- Devices ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: DeviceEntity)

    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun getDeviceById(id: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE deviceIdentifier = :identifier LIMIT 1")
    suspend fun getDeviceByIdentifier(identifier: String): DeviceEntity?

    @Query("SELECT * FROM devices")
    suspend fun getAllDevices(): List<DeviceEntity>

    @Query("UPDATE devices SET lastSyncAt = :lastSyncAt WHERE id = :id")
    suspend fun updateDeviceLastSync(id: String, lastSyncAt: Long)

    // --- Search Queries ---
    @Transaction
    @Query("""
        SELECT DISTINCT p.* FROM people p
        LEFT JOIN phone_numbers pn ON p.id = pn.personId
        LEFT JOIN contact_aliases ca ON p.id = ca.personId
        WHERE p.displayName LIKE '%' || :query || '%'
           OR (p.companyName IS NOT NULL AND p.companyName LIKE '%' || :query || '%')
           OR pn.phoneNumber LIKE '%' || :query || '%'
           OR pn.normalizedNumber LIKE '%' || :query || '%'
           OR ca.aliasName LIKE '%' || :query || '%'
           OR ca.phoneNumber LIKE '%' || :query || '%'
        ORDER BY p.displayName ASC
    """)
    fun searchPeopleWithDetailsFlow(query: String): Flow<List<PersonWithDetails>>

    @Transaction
    @Query("""
        SELECT DISTINCT p.* FROM people p
        LEFT JOIN phone_numbers pn ON p.id = pn.personId
        LEFT JOIN contact_aliases ca ON p.id = ca.personId
        WHERE p.displayName LIKE '%' || :query || '%'
           OR (p.companyName IS NOT NULL AND p.companyName LIKE '%' || :query || '%')
           OR pn.phoneNumber LIKE '%' || :query || '%'
           OR pn.normalizedNumber LIKE '%' || :query || '%'
           OR ca.aliasName LIKE '%' || :query || '%'
           OR ca.phoneNumber LIKE '%' || :query || '%'
        ORDER BY p.displayName ASC
    """)
    suspend fun searchPeopleWithDetails(query: String): List<PersonWithDetails>
}
