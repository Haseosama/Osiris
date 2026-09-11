package com.osiris.app.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.osiris.app.data.BackendPreferences
import com.osiris.app.data.remote.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ConnectionTestState { IDLE, TESTING, SUCCESS, FAILED }

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val backendPreferences = BackendPreferences(application)

    val backendUrl: StateFlow<String> = backendPreferences.backendUrlFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

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
