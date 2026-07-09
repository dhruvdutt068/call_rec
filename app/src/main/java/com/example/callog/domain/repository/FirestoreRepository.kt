package com.example.callog.domain.repository

import com.example.callog.data.local.entity.CallEntity
import com.example.callog.domain.model.FirebaseConfig
import kotlinx.coroutines.flow.StateFlow

interface FirestoreRepository {
    suspend fun uploadCallMetadata(call: CallEntity)
    suspend fun updateCallMetadata(call: CallEntity)
    suspend fun checkIfExists(callId: Long): Boolean
    suspend fun syncPendingCalls()
    suspend fun resetAllSyncStatus()
    
    fun getFirebaseConfig(): FirebaseConfig?
    fun saveFirebaseConfig(config: FirebaseConfig?)
    suspend fun testFirebaseConnection(config: FirebaseConfig?): Result<Unit>
    fun getLastUploadError(): StateFlow<String?>

    fun getDevicePhoneNumber(): String
    fun saveDevicePhoneNumber(number: String)
}
