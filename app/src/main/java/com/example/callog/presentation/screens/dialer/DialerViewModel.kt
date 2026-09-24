package com.example.callog.presentation.screens.dialer

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callog.core.telecom.DtmfTonePlayer
import com.example.callog.core.telecom.T9MatchResult
import com.example.callog.core.telecom.T9SearchEngine
import com.example.callog.core.telecom.TelecomDialerManager
import com.example.callog.core.telecom.TelecomRoleHelper
import com.example.callog.domain.model.CallLogEntry
import com.example.callog.domain.model.Lead
import com.example.callog.domain.model.Person
import com.example.callog.domain.repository.LeadRepository
import com.example.callog.domain.repository.PersonRepository
import com.example.callog.domain.service.SyncManager
import com.example.callog.domain.service.simulator.CallSimulatorManager
import com.example.callog.domain.usecase.GetCallLogsUseCase
import com.example.callog.domain.usecase.ToggleCallFavoriteUseCase
import com.example.callog.sim.SimInfo
import com.example.callog.sim.SimRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DialerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val personRepository: PersonRepository,
    private val leadRepository: LeadRepository,
    private val simRepository: SimRepository,
    private val t9SearchEngine: T9SearchEngine,
    private val telecomDialerManager: TelecomDialerManager,
    private val dtmfTonePlayer: DtmfTonePlayer,
    val simulatorManager: CallSimulatorManager,
    private val getCallLogsUseCase: GetCallLogsUseCase,
    private val toggleCallFavoriteUseCase: ToggleCallFavoriteUseCase,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _mode = MutableStateFlow<DialerMode>(DialerMode.CallLogs)
    val mode: StateFlow<DialerMode> = _mode.asStateFlow()

    private val _inputNumber = MutableStateFlow("")
    val inputNumber: StateFlow<String> = _inputNumber.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _callTypeFilter = MutableStateFlow<String?>("ALL")
    val callTypeFilter: StateFlow<String?> = _callTypeFilter.asStateFlow()

    private val _sortBy = MutableStateFlow("NEWEST")
    val sortBy: StateFlow<String> = _sortBy.asStateFlow()

    private val _activeSims = MutableStateFlow<List<SimInfo>>(emptyList())
    val activeSims: StateFlow<List<SimInfo>> = _activeSims.asStateFlow()

    private val _clipboardSuggestion = MutableStateFlow<String?>(null)
    val clipboardSuggestion: StateFlow<String?> = _clipboardSuggestion.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)

    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val allPeople: StateFlow<List<Person>> = personRepository.getAllPeopleFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val allLeads: StateFlow<List<Lead>> = leadRepository.getAllLeadsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val callLogs: StateFlow<List<CallLogEntry>> = combine(
        _searchQuery,
        _callTypeFilter,
        _sortBy
    ) { query, type, sort ->
        val apiType = if (type == "ALL") null else type
        Triple(query, apiType, sort)
    }.flatMapLatest { (query, type, sort) ->
        getCallLogsUseCase(
            searchQuery = query,
            callTypeFilter = type,
            favoriteFilter = false,
            sortBy = sort
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val t9Results: StateFlow<List<T9MatchResult>> = combine(
        _inputNumber,
        allPeople,
        allLeads
    ) { query, people, leads ->
        if (query.isBlank()) {
            emptyList()
        } else {
            val leadsMap = leads.associateBy { it.personId }
            t9SearchEngine.search(query, people, leadsMap, limit = 15)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<DialerUiState> = combine(
        combine(_mode, _inputNumber, _searchQuery, _callTypeFilter, _sortBy) { mode, input, search, filter, sort ->
            FiveState(mode, input, search, filter, sort)
        },
        combine(callLogs, t9Results, _activeSims, _clipboardSuggestion) { logs, t9, sims, clip ->
            FourState(logs, t9, sims, clip)
        },
        isSyncing,
        _errorMessage
    ) { five, four, syncing, error ->
        DialerUiState(
            mode = five.mode,
            inputNumber = five.input,
            callLogs = four.logs,
            t9Results = four.t9,
            activeSims = four.sims,
            clipboardSuggestion = four.clip,
            searchQuery = five.search,
            activeFilter = five.filter,
            activeSort = five.sort,
            isSyncing = syncing,
            isDefaultDialer = TelecomRoleHelper.isDefaultDialer(context),
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DialerUiState()
    )

    private data class FiveState(
        val mode: DialerMode,
        val input: String,
        val search: String,
        val filter: String?,
        val sort: String
    )

    private data class FourState(
        val logs: List<CallLogEntry>,
        val t9: List<T9MatchResult>,
        val sims: List<SimInfo>,
        val clip: String?
    )

    init {
        refreshSims()
        checkClipboard()
    }

    fun onAction(action: DialerUiAction) {
        when (action) {
            is DialerUiAction.OpenDialpad -> openDialpad()
            is DialerUiAction.CloseDialpad -> closeDialpad()
            is DialerUiAction.ToggleDialpad -> toggleDialpad()
            is DialerUiAction.InputDigit -> onDigitClick(action.digit)
            is DialerUiAction.LongPressZero -> onPlusLongClick()
            is DialerUiAction.SpeedDial -> onSpeedDialLongPress(action.digit)
            is DialerUiAction.Backspace -> onBackspaceClick()
            is DialerUiAction.ClearInput -> onClearInput()
            is DialerUiAction.SetInputNumber -> setInputNumber(action.number)
            is DialerUiAction.PasteClipboard -> onPasteClipboard()
            is DialerUiAction.PlaceCall -> placeCall(action.number, action.simInfo)
            is DialerUiAction.DirectCall -> directCall(action.number)
            is DialerUiAction.SetSearchQuery -> setSearchQuery(action.query)
            is DialerUiAction.SetFilter -> setFilter(action.filter)
            is DialerUiAction.SetSort -> setSort(action.sort)
            is DialerUiAction.ToggleFavorite -> toggleFavorite(action.callId)
            is DialerUiAction.SyncLogs -> syncLogs()
            is DialerUiAction.CheckClipboard -> checkClipboard()
            is DialerUiAction.RefreshSims -> refreshSims()
        }
    }

    fun openDialpad() {
        _mode.value = DialerMode.Dialpad
        checkClipboard()
    }

    fun closeDialpad() {
        _mode.value = DialerMode.CallLogs
    }

    fun toggleDialpad() {
        _mode.update { current ->
            if (current == DialerMode.Dialpad) DialerMode.CallLogs else {
                checkClipboard()
                DialerMode.Dialpad
            }
        }
    }

    fun refreshSims() {
        _activeSims.value = simRepository.getInstalledSims()
    }

    fun onDigitClick(digit: Char) {
        dtmfTonePlayer.playTone(digit, durationMs = 120)
        _inputNumber.update { it + digit }
    }

    fun onBackspaceClick() {
        _inputNumber.update { if (it.isNotEmpty()) it.dropLast(1) else "" }
    }

    fun onClearInput() {
        _inputNumber.value = ""
    }

    fun onPlusLongClick() {
        dtmfTonePlayer.playTone('0', durationMs = 150)
        _inputNumber.update { it + "+" }
    }

    fun setInputNumber(number: String) {
        _inputNumber.value = number
    }

    fun onPasteClipboard() {
        val suggestion = _clipboardSuggestion.value
        if (!suggestion.isNullOrBlank()) {
            _inputNumber.value = suggestion
            _clipboardSuggestion.value = null
        }
    }

    fun checkClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard?.hasPrimaryClip() == true &&
                clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true) {
                val item = clipboard.primaryClip?.getItemAt(0)
                val text = item?.text?.toString()?.trim() ?: ""
                val cleanDigits = text.filter { it.isDigit() || it == '+' }
                if (cleanDigits.length in 5..15 && cleanDigits != _inputNumber.value) {
                    _clipboardSuggestion.value = text
                } else {
                    _clipboardSuggestion.value = null
                }
            }
        } catch (_: Exception) {
            _clipboardSuggestion.value = null
        }
    }

    fun onSpeedDialLongPress(digit: Int) {
        val people = allPeople.value
        val speedDialContact = people.getOrNull(digit - 1)
        val phone = speedDialContact?.phoneNumbers?.firstOrNull()?.phoneNumber
        if (!phone.isNullOrBlank()) {
            _inputNumber.value = phone
            dtmfTonePlayer.playTone(digit.digitToChar(), durationMs = 200)
        }
    }

    fun placeCall(phoneNumber: String = _inputNumber.value, simInfo: SimInfo?) {
        val cleanNumber = phoneNumber.ifBlank { _inputNumber.value }.trim()
        if (cleanNumber.isBlank()) return

        telecomDialerManager.placeCall(cleanNumber, simInfo)
    }

    fun directCall(phoneNumber: String) {
        if (phoneNumber.isBlank()) return
        val defaultSim = _activeSims.value.firstOrNull()
        telecomDialerManager.placeCall(phoneNumber.trim(), defaultSim)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: String) {
        _callTypeFilter.value = filter
    }

    fun setSort(sort: String) {
        _sortBy.value = sort
    }

    fun toggleFavorite(callId: Long) {
        viewModelScope.launch {
            toggleCallFavoriteUseCase(callId)
        }
    }

    fun syncLogs() {
        viewModelScope.launch {
            syncManager.startSync()
        }
    }

    override fun onCleared() {
        super.onCleared()
        dtmfTonePlayer.release()
    }
}
