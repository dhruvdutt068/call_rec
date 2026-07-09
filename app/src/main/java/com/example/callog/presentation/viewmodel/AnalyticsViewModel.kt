package com.example.callog.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callog.domain.model.CallLogEntry
import com.example.callog.domain.repository.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

data class TopContactStat(
    val name: String,
    val number: String,
    val count: Int,
    val totalDuration: Int,
    val photoUri: String?
)

data class AnalyticsUiState(
    val totalCalls: Int = 0,
    val incomingCount: Int = 0,
    val outgoingCount: Int = 0,
    val missedCount: Int = 0,
    val rejectedCount: Int = 0,
    val recordedCount: Int = 0,
    val avgDurationSeconds: Int = 0,
    val longestCallSeconds: Int = 0,
    val longestCallName: String = "",
    val callsByDayOfWeek: Map<String, Int> = emptyMap(), // e.g. "Mon" -> 4
    val callsByHourOfDay: Map<Int, Int> = emptyMap(),    // e.g. 14 -> 3 (2 PM)
    val callTypePercentages: Map<String, Float> = emptyMap(), // e.g. "Incoming" -> 0.45f
    val topContacts: List<TopContactStat> = emptyList()
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    repository: CallRepository
) : ViewModel() {

    // Process calls list into analytics structures reactively
    val uiState: StateFlow<AnalyticsUiState> = repository.getCallLogsFlow()
        .map { calls ->
            if (calls.isEmpty()) {
                AnalyticsUiState()
            } else {
                calculateAnalytics(calls)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AnalyticsUiState()
        )

    private fun calculateAnalytics(calls: List<CallLogEntry>): AnalyticsUiState {
        val total = calls.size
        val incoming = calls.count { it.callType == "INCOMING" }
        val outgoing = calls.count { it.callType == "OUTGOING" }
        val missed = calls.count { it.callType == "MISSED" }
        val rejected = calls.count { it.callType == "REJECTED" || it.callType == "BLOCKED" }
        val recorded = calls.count { it.recordingPath != null }

        // Durations (only for completed calls)
        val completedCalls = calls.filter { it.callType == "INCOMING" || it.callType == "OUTGOING" }
        val totalDuration = completedCalls.sumOf { it.duration }
        val avgDuration = if (completedCalls.isNotEmpty()) totalDuration / completedCalls.size else 0
        
        val longestCall = completedCalls.maxByOrNull { it.duration }
        val longestCallSec = longestCall?.duration ?: 0
        val longestCallName = longestCall?.displayName ?: "None"

        // Day of Week volume
        val dayOfWeekMap = mutableMapOf("Mon" to 0, "Tue" to 0, "Wed" to 0, "Thu" to 0, "Fri" to 0, "Sat" to 0, "Sun" to 0)
        val hourOfDayMap = (0..23).associateWith { 0 }.toMutableMap()
        
        val calendar = Calendar.getInstance()
        val dayNames = arrayOf("", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

        calls.forEach { call ->
            calendar.time = Date(call.timestamp)
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek in 1..7) {
                val name = dayNames[dayOfWeek]
                dayOfWeekMap[name] = (dayOfWeekMap[name] ?: 0) + 1
            }
            
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            hourOfDayMap[hour] = (hourOfDayMap[hour] ?: 0) + 1
        }

        // Call type percentages
        val percentages = mapOf(
            "Incoming" to (incoming.toFloat() / total),
            "Outgoing" to (outgoing.toFloat() / total),
            "Missed" to (missed.toFloat() / total),
            "Rejected/Spam" to (rejected.toFloat() / total)
        )

        // Top Contact Stats
        val topContactsList = calls
            .groupBy { it.number }
            .map { (number, contactCalls) ->
                val firstCall = contactCalls.first()
                TopContactStat(
                    name = firstCall.displayName,
                    number = number,
                    count = contactCalls.size,
                    totalDuration = contactCalls.filter { it.callType == "INCOMING" || it.callType == "OUTGOING" }.sumOf { it.duration },
                    photoUri = firstCall.contactPhotoUri
                )
            }
            .sortedByDescending { it.count }
            .take(5)

        return AnalyticsUiState(
            totalCalls = total,
            incomingCount = incoming,
            outgoingCount = outgoing,
            missedCount = missed,
            rejectedCount = rejected,
            recordedCount = recorded,
            avgDurationSeconds = avgDuration,
            longestCallSeconds = longestCallSec,
            longestCallName = longestCallName,
            callsByDayOfWeek = dayOfWeekMap,
            callsByHourOfDay = hourOfDayMap,
            callTypePercentages = percentages,
            topContacts = topContactsList
        )
    }
}
