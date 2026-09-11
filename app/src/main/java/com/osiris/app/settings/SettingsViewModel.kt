package com.osiris.app.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.osiris.app.data.BackendPreferences
import com.osiris.app.data.PollIntervalPreferences
import com.osiris.app.data.remote.NetworkModule
import com.osiris.app.map.MapLayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ConnectionTestState { IDLE, TESTING, SUCCESS, FAILED }

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val backendPreferences = BackendPreferences(application)
    private val pollIntervalPreferences = PollIntervalPreferences(application)

    val backendUrl: StateFlow<String> = backendPreferences.backendUrlFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val pollIntervals: StateFlow<Map<MapLayer, Long>> = combine(
        MapLayer.entries.map { layer -> pollIntervalPreferences.intervalFlow(layer).map { layer to it } }
    ) { pairs -> pairs.toMap() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MapLayer.entries.associateWith { it.pollIntervalMs })

    fun setPollInterval(layer: MapLayer, intervalMs: Long) {
        viewModelScope.launch { pollIntervalPreferences.setInterval(layer, intervalMs) }
    }

    private val _connectionTestState = MutableStateFlow(ConnectionTestState.IDLE)
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    fun saveBackendUrl(url: String) {
        viewModelScope.launch {
            backendPreferences.setBackendUrl(url)
            _connectionTestState.value = ConnectionTestState.IDLE
        }
    }

    fun testConnection(url: String) {
        viewModelScope.launch {
            _connectionTestState.value = ConnectionTestState.TESTING
            runCatching { NetworkModule.apiFor(url).health() }
                .onSuccess { response ->
                    if (response.isSuccessful) {
                        _connectionTestState.value = ConnectionTestState.SUCCESS
                    } else {
                        _connectionError.value = "HTTP ${response.code()}"
                        _connectionTestState.value = ConnectionTestState.FAILED
                    }
                }
                .onFailure {
                    _connectionError.value = it.message ?: "Connexion impossible"
                    _connectionTestState.value = ConnectionTestState.FAILED
                }
        }
    }
}
