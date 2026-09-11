package com.osiris.app.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.osiris.app.data.BackendPreferences
import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.model.ConflictZone
import com.osiris.app.data.model.CyberAttack
import com.osiris.app.data.model.Earthquake
import com.osiris.app.data.model.FireEvent
import com.osiris.app.data.model.FlightMarker
import com.osiris.app.data.model.LiveNewsFeed
import com.osiris.app.data.model.MaritimeResponse
import com.osiris.app.data.model.OsintPost
import com.osiris.app.data.model.Satellite
import com.osiris.app.data.model.WeatherEvent
import com.osiris.app.data.repository.CctvRepository
import com.osiris.app.data.repository.ConflictsRepository
import com.osiris.app.data.repository.CyberAttacksRepository
import com.osiris.app.data.repository.EarthquakesRepository
import com.osiris.app.data.repository.FiresRepository
import com.osiris.app.data.repository.FlightsRepository
import com.osiris.app.data.repository.LiveNewsRepository
import com.osiris.app.data.repository.MaritimeRepository
import com.osiris.app.data.repository.OsintRepository
import com.osiris.app.data.repository.SatellitesRepository
import com.osiris.app.data.repository.WeatherRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val backendPreferences = BackendPreferences(application)

    private val flightsRepo = FlightsRepository()
    private val earthquakesRepo = EarthquakesRepository()
    private val firesRepo = FiresRepository()
    private val weatherRepo = WeatherRepository()
    private val conflictsRepo = ConflictsRepository()
    private val maritimeRepo = MaritimeRepository()
    private val satellitesRepo = SatellitesRepository()
    private val liveNewsRepo = LiveNewsRepository()
    private val cyberAttacksRepo = CyberAttacksRepository()
    private val cctvRepo = CctvRepository()
    private val osintRepo = OsintRepository()

    val backendUrl: StateFlow<String> = backendPreferences.backendUrlFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _layerToggles = MutableStateFlow(
        MapLayer.entries.associateWith { it.defaultEnabled }
    )
    val layerToggles: StateFlow<Map<MapLayer, Boolean>> = _layerToggles.asStateFlow()

    private val _layerErrors = MutableStateFlow<Map<MapLayer, String?>>(emptyMap())
    val layerErrors: StateFlow<Map<MapLayer, String?>> = _layerErrors.asStateFlow()

    /** True once every currently-enabled layer has failed at least once against a configured
     * backend — distinct from "no backend configured", which already has its own banner. */
    val backendUnreachable: StateFlow<Boolean> = combine(backendUrl, layerToggles, layerErrors) { url, toggles, errors ->
        if (url.isBlank()) {
            false
        } else {
            val enabledLayers = toggles.filterValues { it }.keys
            enabledLayers.isNotEmpty() && enabledLayers.all { errors[it] != null }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val flights = MutableStateFlow<List<FlightMarker>>(emptyList())
    val earthquakes = MutableStateFlow<List<Earthquake>>(emptyList())
    val fires = MutableStateFlow<List<FireEvent>>(emptyList())
    val weatherEvents = MutableStateFlow<List<WeatherEvent>>(emptyList())
    val conflictZones = MutableStateFlow<List<ConflictZone>>(emptyList())
    val maritime = MutableStateFlow(MaritimeResponse())
    val satellites = MutableStateFlow<List<Satellite>>(emptyList())
    val newsFeeds = MutableStateFlow<List<LiveNewsFeed>>(emptyList())
    val cyberAttacks = MutableStateFlow<List<CyberAttack>>(emptyList())
    val cctvCameras = MutableStateFlow<List<CctvCamera>>(emptyList())
    val osintPosts = MutableStateFlow<List<OsintPost>>(emptyList())

    private val _selectedNewsFeed = MutableStateFlow<LiveNewsFeed?>(null)
    val selectedNewsFeed: StateFlow<LiveNewsFeed?> = _selectedNewsFeed.asStateFlow()

    fun selectNewsFeed(feed: LiveNewsFeed?) {
        _selectedNewsFeed.value = feed
    }

    private val _selectedCctvCamera = MutableStateFlow<CctvCamera?>(null)
    val selectedCctvCamera: StateFlow<CctvCamera?> = _selectedCctvCamera.asStateFlow()

    fun selectCctvCamera(camera: CctvCamera?) {
        _selectedCctvCamera.value = camera
    }

    private val _selectedOsintPost = MutableStateFlow<OsintPost?>(null)
    val selectedOsintPost: StateFlow<OsintPost?> = _selectedOsintPost.asStateFlow()

    fun selectOsintPost(post: OsintPost?) {
        _selectedOsintPost.value = post
    }

    private val pollingJobs = mutableMapOf<MapLayer, Job>()

    init {
        _layerToggles.value.forEach { (layer, enabled) -> if (enabled) startPolling(layer) }
    }

    fun toggleLayer(layer: MapLayer) {
        val nowEnabled = !(_layerToggles.value[layer] ?: false)
        _layerToggles.update { it + (layer to nowEnabled) }
        if (nowEnabled) {
            startPolling(layer)
        } else {
            pollingJobs.remove(layer)?.cancel()
        }
    }

    private fun startPolling(layer: MapLayer) {
        pollingJobs[layer]?.cancel()
        pollingJobs[layer] = viewModelScope.launch {
            while (isActive) {
                val baseUrl = backendUrl.value
                if (baseUrl.isBlank()) {
                    setError(layer, "Configure l'URL du backend dans Réglages")
                } else {
                    runCatching { fetch(layer, baseUrl) }
                        .onSuccess { setError(layer, null) }
                        .onFailure { setError(layer, it.message ?: "Erreur réseau") }
                }
                delay(layer.pollIntervalMs)
            }
        }
    }

    private suspend fun fetch(layer: MapLayer, baseUrl: String) {
        when (layer) {
            MapLayer.FLIGHTS -> flights.value = flightsRepo.fetch(baseUrl)
            MapLayer.EARTHQUAKES -> earthquakes.value = earthquakesRepo.fetch(baseUrl)
            MapLayer.FIRES -> fires.value = firesRepo.fetch(baseUrl)
            MapLayer.WEATHER -> weatherEvents.value = weatherRepo.fetch(baseUrl)
            MapLayer.CONFLICTS -> conflictZones.value = conflictsRepo.fetch(baseUrl)
            MapLayer.MARITIME -> maritime.value = maritimeRepo.fetch(baseUrl)
            MapLayer.SATELLITES -> satellites.value = satellitesRepo.fetch(baseUrl)
            MapLayer.NEWS -> newsFeeds.value = liveNewsRepo.fetch(baseUrl)
            MapLayer.CYBER_ATTACKS -> cyberAttacks.value = cyberAttacksRepo.fetch(baseUrl)
            MapLayer.CCTV -> cctvCameras.value = cctvRepo.fetch(baseUrl)
            MapLayer.OSINT -> osintPosts.value = osintRepo.fetch(baseUrl)
        }
    }

    private fun setError(layer: MapLayer, message: String?) {
        _layerErrors.update { it + (layer to message) }
    }
}
