package com.osiris.app.recon

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.osiris.app.data.BackendPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReconViewModel(application: Application) : AndroidViewModel(application) {

    private val backendPreferences = BackendPreferences(application)
    private val repository = ReconRepository()

    val backendUrl: StateFlow<String> = backendPreferences.backendUrlFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

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
        val baseUrl = backendUrl.value
        if (baseUrl.isBlank()) {
            _errorText.value = "Configure l'URL du backend dans Réglages"
            return
        }
        if (tool.paramName.isNotEmpty() && _inputValue.value.isBlank()) {
            _errorText.value = "Renseigne une valeur à rechercher"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorText.value = null
            _result.value = null
            repository.query(baseUrl, tool, _inputValue.value, _secondaryValue.value.ifBlank { null })
                .onSuccess { _result.value = it }
                .onFailure { _errorText.value = it.message ?: "Erreur réseau" }
            _isLoading.value = false
        }
    }
}
