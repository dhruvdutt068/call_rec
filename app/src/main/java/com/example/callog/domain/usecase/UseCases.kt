package com.example.callog.domain.usecase

import com.example.callog.data.local.dao.ReminderWithCall
import com.example.callog.data.local.entity.ReminderEntity
import com.example.callog.domain.model.CallLogEntry
import com.example.callog.domain.repository.CallRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetCallLogsUseCase @Inject constructor(
    private val repository: CallRepository
) {
    operator fun invoke(
        searchQuery: String = "",
        callTypeFilter: String? = null, // "INCOMING", "OUTGOING", "MISSED", "REJECTED", "RECORDED"
        favoriteFilter: Boolean = false,
        sortBy: String = "NEWEST" // "NEWEST", "OLDEST", "LONGEST", "SHORTEST"
    ): Flow<List<CallLogEntry>> {
        val baseFlow = if (favoriteFilter) {
            repository.getFavoriteLogsFlow()
        } else if (callTypeFilter == "RECORDED") {
            repository.getRecordingLogsFlow()
        } else {
            repository.getCallLogsFlow()
        }

        return baseFlow.map { list ->
            var result = list

            // 1. Filter by Search Query
            if (searchQuery.isNotEmpty()) {
                val q = searchQuery.trim().lowercase()
                result = result.filter { entry ->
                    entry.name?.lowercase()?.contains(q) == true ||
                    entry.number.contains(q) ||
                    entry.notes?.lowercase()?.contains(q) == true ||
                    entry.tags.any { it.lowercase().contains(q) }
                }
            }

            // 2. Filter by Call Type (excluding RECORDED, which is already handled above by getRecordingLogsFlow)
            if (callTypeFilter != null && callTypeFilter != "RECORDED" && callTypeFilter != "ALL") {
                result = result.filter { it.callType.equals(callTypeFilter, ignoreCase = true) }
            }

            // 3. Sorting
            when (sortBy.uppercase()) {
                "OLDEST" -> result.sortedBy { it.timestamp }
                "LONGEST" -> result.sortedByDescending { it.duration }
                "SHORTEST" -> result.sortedBy { it.duration }
                "NEWEST" -> result.sortedByDescending { it.timestamp }
                else -> result.sortedByDescending { it.timestamp }
            }
        }
    }
}

class SyncCallLogsUseCase @Inject constructor(
    private val repository: CallRepository
) {
    suspend operator fun invoke() = repository.syncCallLogs()
}

class UpdateCallNotesUseCase @Inject constructor(
    private val repository: CallRepository
) {
    suspend operator fun invoke(callId: Long, notes: String?) = repository.updateNotes(callId, notes)
}

class UpdateCallTagsUseCase @Inject constructor(
    private val repository: CallRepository
) {
    suspend operator fun invoke(callId: Long, tags: List<String>) = repository.updateTags(callId, tags)
}

class ToggleCallFavoriteUseCase @Inject constructor(
    private val repository: CallRepository
) {
    suspend operator fun invoke(callId: Long) = repository.toggleFavorite(callId)
}

class DeleteCallUseCase @Inject constructor(
    private val repository: CallRepository
) {
    suspend operator fun invoke(callId: Long) = repository.deleteCall(callId)
}

class ClearAllDataUseCase @Inject constructor(
    private val repository: CallRepository
) {
    suspend operator fun invoke() = repository.clearAllData()
}

class GetPendingRemindersUseCase @Inject constructor(
    private val repository: CallRepository
) {
    operator fun invoke(): Flow<List<ReminderWithCall>> = repository.getPendingRemindersFlow()
}

class AddReminderUseCase @Inject constructor(
    private val repository: CallRepository
) {
    suspend operator fun invoke(reminder: ReminderEntity): Long = repository.addReminder(reminder)
}

class CompleteReminderUseCase @Inject constructor(
    private val repository: CallRepository
) {
    suspend operator fun invoke(reminderId: Long) = repository.completeReminder(reminderId)
}

class DeleteReminderUseCase @Inject constructor(
    private val repository: CallRepository
) {
    suspend operator fun invoke(reminderId: Long) = repository.deleteReminder(reminderId)
}
