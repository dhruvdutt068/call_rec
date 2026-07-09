package com.example.callog.domain.service

import kotlinx.coroutines.flow.Flow

interface SyncManager {
    val isSyncing: Flow<Boolean>
    val syncProgress: Flow<String?>
    
    fun startSync()
    fun stopSync()
    fun schedulePeriodicSync(intervalMinutes: Long = 15)
    fun cancelPeriodicSync()
    suspend fun forceResetAndSync()
}
