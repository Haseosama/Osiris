package com.osiris.app.recon

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReconViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ReconRepository()
    private val watchlistPreferences = WatchlistPreferences(application)

    private val _selectedTool = MutableStateFlow(ReconTool.DNS)
    val selectedTool: StateFlow<ReconTool> = _selectedTool.asStateFlow()

    private val _inputValue = MutableStateFlow("")
    val inputValue: StateFlow<String> = _inputValue.asStateFlow()

    private val _secondaryValue = MutableStateFlow(ReconTool.DNS.secondaryParam?.default.orEmpty())
    val secondaryValue: StateFlow<String> = _secondaryValue.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _result = MutableStateFlow<ReconResult?>(null)
    val result: StateFlow<ReconResult?> = _result.asStateFlow()

    private val _errorText = MutableStateFlow<String?>(null)
    val errorText: StateFlow<String?> = _errorText.asStateFlow()

    val watchlist: StateFlow<List<WatchlistEntry>> =
        watchlistPreferences.entriesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Whether the *current* tool/input/secondary combo is already on the watchlist — drives the
     * star toggle's on/off state. Compares by [WatchlistEntry.idFor] rather than object identity
     * so it stays correct across recompositions. */
    val isCurrentQueryWatched: StateFlow<Boolean> = combine(watchlist, selectedTool, inputValue, secondaryValue) { entries, tool, value, secondary ->
        entries.any { it.id == WatchlistEntry.idFor(tool, value, secondary.ifBlank { null }) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun selectTool(tool: ReconTool) {
        _selectedTool.value = tool
        _inputValue.value = ""
        _secondaryValue.value = tool.secondaryParam?.default.orEmpty()
        _result.value = null
        _errorText.value = null
    }

    fun updateInput(value: String) {
        _inputValue.value = value
    }

    fun updateSecondary(value: String) {
        _secondaryValue.value = value
    }

    fun runQuery() {
        val tool = _selectedTool.value
        if (tool.paramName.isNotEmpty() && _inputValue.value.isBlank()) {
            _errorText.value = "Renseigne une valeur à rechercher"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorText.value = null
            _result.value = null
            repository.query(tool, _inputValue.value, _secondaryValue.value.ifBlank { null })
                .onSuccess { _result.value = it }
                .onFailure { _errorText.value = it.message ?: "Erreur réseau" }
            _isLoading.value = false
        }
    }

    /** Adds/removes the current query from the watchlist — only meaningful once a result has
     * come back at least once (nothing to establish a baseline hash from otherwise), same
     * tool/input/secondary the "Rechercher" button above just ran. */
    fun toggleWatch() {
        val tool = _selectedTool.value
        val value = _inputValue.value
        val secondary = _secondaryValue.value.ifBlank { null }
        val id = WatchlistEntry.idFor(tool, value, secondary)
        viewModelScope.launch {
            if (isCurrentQueryWatched.value) {
                watchlistPreferences.remove(id)
            } else {
                val result = _result.value ?: return@launch
                watchlistPreferences.add(
                    WatchlistEntry(
                        id = id,
                        tool = tool,
                        value = value,
                        secondaryValue = secondary,
                        label = value.ifBlank { tool.label },
                        lastSnapshotHash = result.snapshotHash(),
                        lastCheckedMs = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    fun removeFromWatchlist(id: String) {
        viewModelScope.launch { watchlistPreferences.remove(id) }
    }
}
