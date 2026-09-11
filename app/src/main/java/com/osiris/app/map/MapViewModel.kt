package com.osiris.app.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.osiris.app.data.BackendPreferences
import com.osiris.app.data.LayerCache
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
    private val layerCache = LayerCache(application)

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
    val cyberAttackPulses = MutableStateFlow<List<CyberAttackPulse>>(emptyList())

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

    private val _selectedInfo = MutableStateFlow<InfoDialogContent?>(null)
    val selectedInfo: StateFlow<InfoDialogContent?> = _selectedInfo.asStateFlow()

    /** Shared by flights/earthquakes/fires/weather/conflicts/maritime/satellites — see
     * [InfoDialogContent]. News/CCTV/OSINT keep their own selectX functions above. */
    fun selectInfo(content: InfoDialogContent?) {
        _selectedInfo.value = content
    }

    private val pollingJobs = mutableMapOf<MapLayer, Job>()
    private var pulseJob: Job? = null

    init {
        viewModelScope.launch {
            loadCachedData()
            _layerToggles.value.forEach { (layer, enabled) ->
                if (enabled) {
                    startPolling(layer)
                    if (layer == MapLayer.CYBER_ATTACKS) startCyberAttackPulseAnimation()
                }
            }
        }
    }

    /** Fills every layer with whatever was cached last session, before polling starts, so a
     * fresh network fetch never gets clobbered by a slower cache read landing after it. */
    private suspend fun loadCachedData() {
        layerCache.loadFlights()?.let { flights.value = it }
        layerCache.loadEarthquakes()?.let { earthquakes.value = it }
        layerCache.loadFires()?.let { fires.value = it }
        layerCache.loadWeather()?.let { weatherEvents.value = it }
        layerCache.loadConflicts()?.let { conflictZones.value = it }
        layerCache.loadMaritime()?.let { maritime.value = it }
        layerCache.loadSatellites()?.let { satellites.value = it }
        layerCache.loadNews()?.let { newsFeeds.value = it }
        layerCache.loadCyberAttacks()?.let { cyberAttacks.value = it }
        layerCache.loadCctv()?.let { cctvCameras.value = it }
        layerCache.loadOsint()?.let { osintPosts.value = it }
    }

    fun toggleLayer(layer: MapLayer) {
        val nowEnabled = !(_layerToggles.value[layer] ?: false)
        _layerToggles.update { it + (layer to nowEnabled) }
        if (nowEnabled) {
            startPolling(layer)
            if (layer == MapLayer.CYBER_ATTACKS) startCyberAttackPulseAnimation()
        } else {
            pollingJobs.remove(layer)?.cancel()
            if (layer == MapLayer.CYBER_ATTACKS) {
                pulseJob?.cancel()
                pulseJob = null
                cyberAttackPulses.value = emptyList()
            }
        }
    }

    /** Recomputes each attack's dot position along its [ArcMath] curve every ~80ms while the
     * layer is enabled — cancelled the moment it's toggled off so a hidden layer costs nothing. */
    private fun startCyberAttackPulseAnimation() {
        pulseJob?.cancel()
        pulseJob = viewModelScope.launch {
            var t = 0.0
            while (isActive) {
                val attacks = cyberAttacks.value
                cyberAttackPulses.value = attacks.map { attack ->
                    val phase = (t + (attack.id.hashCode().mod(100)) / 100.0) % 1.0
                    val (lng, lat) = ArcMath.pointAt(attack.srcLng, attack.srcLat, attack.dstLng, attack.dstLat, phase)
                    CyberAttackPulse(attack.id, lng, lat, attack.severity)
                }
                t = (t + 0.015) % 1.0
                delay(80L)
            }
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
            MapLayer.FLIGHTS -> flightsRepo.fetch(baseUrl).also { flights.value = it; layerCache.saveFlights(it) }
            MapLayer.EARTHQUAKES -> earthquakesRepo.fetch(baseUrl).also { earthquakes.value = it; layerCache.saveEarthquakes(it) }
            MapLayer.FIRES -> firesRepo.fetch(baseUrl).also { fires.value = it; layerCache.saveFires(it) }
            MapLayer.WEATHER -> weatherRepo.fetch(baseUrl).also { weatherEvents.value = it; layerCache.saveWeather(it) }
            MapLayer.CONFLICTS -> conflictsRepo.fetch(baseUrl).also { conflictZones.value = it; layerCache.saveConflicts(it) }
            MapLayer.MARITIME -> maritimeRepo.fetch(baseUrl).also { maritime.value = it; layerCache.saveMaritime(it) }
            MapLayer.SATELLITES -> satellitesRepo.fetch(baseUrl).also { satellites.value = it; layerCache.saveSatellites(it) }
            MapLayer.NEWS -> liveNewsRepo.fetch(baseUrl).also { newsFeeds.value = it; layerCache.saveNews(it) }
            MapLayer.CYBER_ATTACKS -> cyberAttacksRepo.fetch(baseUrl).also { cyberAttacks.value = it; layerCache.saveCyberAttacks(it) }
            MapLayer.CCTV -> cctvRepo.fetch(baseUrl).also { cctvCameras.value = it; layerCache.saveCctv(it) }
            MapLayer.OSINT -> osintRepo.fetch(baseUrl).also { osintPosts.value = it; layerCache.saveOsint(it) }
        }
    }

    private fun setError(layer: MapLayer, message: String?) {
        _layerErrors.update { it + (layer to message) }
    }
}
