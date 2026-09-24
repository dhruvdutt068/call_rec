package com.example.callog.presentation.screens.dialer

import androidx.compose.runtime.Immutable
import com.example.callog.core.telecom.T9MatchResult
import com.example.callog.domain.model.CallLogEntry
import com.example.callog.sim.SimInfo

/**
 * Operating mode of the unified Dialer + Call Logs screen.
 */
sealed interface DialerMode {
    /**
     * Default mode: Displays recent call logs and interaction history.
     */
    data object CallLogs : DialerMode

    /**
     * On-demand mode: Displays dialpad and phone number input.
     */
    data object Dialpad : DialerMode
}

/**
 * Immutable UI State for the unified Dialer screen.
 */
@Immutable
data class DialerUiState(
    val mode: DialerMode = DialerMode.CallLogs,
    val inputNumber: String = "",
    val callLogs: List<CallLogEntry> = emptyList(),
    val t9Results: List<T9MatchResult> = emptyList(),
    val activeSims: List<SimInfo> = emptyList(),
    val clipboardSuggestion: String? = null,
    val searchQuery: String = "",
    val activeFilter: String? = "ALL", // "ALL", "INCOMING", "OUTGOING", "MISSED", "REJECTED", "RECORDED"
    val activeSort: String = "NEWEST", // "NEWEST", "OLDEST", "LONGEST", "SHORTEST"
    val isSyncing: Boolean = false,
    val isDefaultDialer: Boolean = false,
    val error: String? = null
)

/**
 * Unidirectional user actions for the Dialer screen.
 */
sealed interface DialerUiAction {
    data object OpenDialpad : DialerUiAction
    data object CloseDialpad : DialerUiAction
    data object ToggleDialpad : DialerUiAction
    data class InputDigit(val digit: Char) : DialerUiAction
    data object LongPressZero : DialerUiAction
    data class SpeedDial(val digit: Int) : DialerUiAction
    data object Backspace : DialerUiAction
    data object ClearInput : DialerUiAction
    data class SetInputNumber(val number: String) : DialerUiAction
    data object PasteClipboard : DialerUiAction
    data class PlaceCall(val number: String, val simInfo: SimInfo?) : DialerUiAction
    data class DirectCall(val number: String) : DialerUiAction
    data class SetSearchQuery(val query: String) : DialerUiAction
    data class SetFilter(val filter: String) : DialerUiAction
    data class SetSort(val sort: String) : DialerUiAction
    data class ToggleFavorite(val callId: Long) : DialerUiAction
    data object SyncLogs : DialerUiAction
    data object CheckClipboard : DialerUiAction
    data object RefreshSims : DialerUiAction
}
