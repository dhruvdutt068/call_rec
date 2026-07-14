package com.example.callog.domain.repository

import com.example.callog.data.local.dao.ReminderWithCall
import com.example.callog.data.local.entity.CallEntity
import com.example.callog.data.local.entity.ReminderEntity
import com.example.callog.data.provider.ContactDto
import com.example.callog.domain.model.CallLogEntry
import kotlinx.coroutines.flow.Flow

interface CallRepository {
    fun getCallLogsFlow(): Flow<List<CallLogEntry>>
    fun getFavoriteLogsFlow(): Flow<List<CallLogEntry>>
    fun getRecordingLogsFlow(): Flow<List<CallLogEntry>>
    fun getCallLogByIdFlow(id: Long): Flow<CallLogEntry?>
    
    suspend fun syncCallLogs()
    suspend fun getAllCallsSnapshot(): List<CallEntity>
    suspend fun getContacts(): List<ContactDto>
    
    suspend fun updateNotes(callId: Long, notes: String?)
    suspend fun updateTags(callId: Long, tags: List<String>)
    suspend fun toggleFavorite(callId: Long)
    suspend fun deleteCall(callId: Long)
    suspend fun clearAllData()
    
    // Reminders
    fun getPendingRemindersFlow(): Flow<List<ReminderWithCall>>
    suspend fun addReminder(reminder: ReminderEntity): Long
    suspend fun completeReminder(reminderId: Long)
    suspend fun deleteReminder(reminderId: Long)
}
