package com.osiris.app.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.osiris.app.data.PollIntervalPreferences
import com.osiris.app.map.MapLayer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val pollIntervalPreferences = PollIntervalPreferences(application)

    val pollIntervals: StateFlow<Map<MapLayer, Long>> = combine(
        MapLayer.entries.map { layer -> pollIntervalPreferences.intervalFlow(layer).map { layer to it } }
    ) { pairs -> pairs.toMap() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MapLayer.entries.associateWith { it.pollIntervalMs })

    fun setPollInterval(layer: MapLayer, intervalMs: Long) {
        viewModelScope.launch { pollIntervalPreferences.setInterval(layer, intervalMs) }
    }
}
