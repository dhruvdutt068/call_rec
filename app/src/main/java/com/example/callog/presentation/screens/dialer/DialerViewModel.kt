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
import com.example.callog.domain.model.Lead
import com.example.callog.domain.model.Person
import com.example.callog.domain.repository.LeadRepository
import com.example.callog.domain.repository.PersonRepository
import com.example.callog.sim.SimInfo
import com.example.callog.sim.SimRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DialerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val personRepository: PersonRepository,
    private val leadRepository: LeadRepository,
    private val simRepository: SimRepository,
    private val t9SearchEngine: T9SearchEngine,
    private val telecomDialerManager: TelecomDialerManager,
    private val dtmfTonePlayer: DtmfTonePlayer
) : ViewModel() {

    private val _inputNumber = MutableStateFlow("")
    val inputNumber: StateFlow<String> = _inputNumber.asStateFlow()

    private val _activeSims = MutableStateFlow<List<SimInfo>>(emptyList())
    val activeSims: StateFlow<List<SimInfo>> = _activeSims.asStateFlow()

    private val _clipboardSuggestion = MutableStateFlow<String?>(null)
    val clipboardSuggestion: StateFlow<String?> = _clipboardSuggestion.asStateFlow()

    private val allPeople: StateFlow<List<Person>> = personRepository.getAllPeopleFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val allLeads: StateFlow<List<Lead>> = leadRepository.getAllLeadsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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

    init {
        refreshSims()
        checkClipboard()
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
        // Find favorite or top lead for speed dial index
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

    override fun onCleared() {
        super.onCleared()
        dtmfTonePlayer.release()
    }
}
