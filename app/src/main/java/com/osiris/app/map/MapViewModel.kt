package com.osiris.app.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.osiris.app.data.BackendPreferences
import com.osiris.app.data.LayerCache
import com.osiris.app.data.PollIntervalPreferences
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val backendPreferences = BackendPreferences(application)
    private val layerCache = LayerCache(application)
    private val pollIntervalPreferences = PollIntervalPreferences(application)

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
    private var flightsAnimJob: Job? = null
    private var maritimeAnimJob: Job? = null

    // Last real fix from the backend, kept separate from the public [flights]/[maritime] flows
    // (which the animation ticker below overwrites every second with a dead-reckoned position)
    // so each tick always extrapolates from the true last poll rather than from its own output.
    private var rawFlights: List<FlightMarker> = emptyList()
    private var flightsPolledAtMs = 0L
    private var rawMaritime = MaritimeResponse()
    private var maritimePolledAtMs = 0L

    // Local notifications: never fire on a layer's first successful poll (that's just the
    // current state, not a new event) — only when something changes on a later one. See
    // AlertNotifier.
    private var earthquakeAlertsBaseline = false
    private val seenEarthquakeIds = mutableSetOf<String>()
    private var conflictAlertsBaseline = false
    private val lastConflictSeverity = mutableMapOf<String, String>()

    init {
        viewModelScope.launch {
            loadCachedData()
            _layerToggles.value.forEach { (layer, enabled) ->
                if (enabled) {
                    startPolling(layer)
                    when (layer) {
                        MapLayer.CYBER_ATTACKS -> startCyberAttackPulseAnimation()
                        MapLayer.FLIGHTS -> startFlightsAnimation()
                        MapLayer.MARITIME -> startMaritimeAnimation()
                        else -> Unit
                    }
                }
            }
        }
    }

    /** Fills every layer with whatever was cached last session, before polling starts, so a
     * fresh network fetch never gets clobbered by a slower cache read landing after it. */
    private suspend fun loadCachedData() {
        layerCache.loadFlights()?.let { rawFlights = it; flightsPolledAtMs = System.currentTimeMillis(); flights.value = it }
        layerCache.loadEarthquakes()?.let { earthquakes.value = it }
        layerCache.loadFires()?.let { fires.value = it }
        layerCache.loadWeather()?.let { weatherEvents.value = it }
        layerCache.loadConflicts()?.let { conflictZones.value = it }
        layerCache.loadMaritime()?.let { rawMaritime = it; maritimePolledAtMs = System.currentTimeMillis(); maritime.value = it }
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
            when (layer) {
                MapLayer.CYBER_ATTACKS -> startCyberAttackPulseAnimation()
                MapLayer.FLIGHTS -> startFlightsAnimation()
                MapLayer.MARITIME -> startMaritimeAnimation()
                else -> Unit
            }
        } else {
            pollingJobs.remove(layer)?.cancel()
            when (layer) {
                MapLayer.CYBER_ATTACKS -> {
                    pulseJob?.cancel()
                    pulseJob = null
                    cyberAttackPulses.value = emptyList()
                }
                MapLayer.FLIGHTS -> {
                    flightsAnimJob?.cancel()
                    flightsAnimJob = null
                }
                MapLayer.MARITIME -> {
                    maritimeAnimJob?.cancel()
                    maritimeAnimJob = null
                }
                else -> Unit
            }
        }
    }

    /** Flights only get a fresh fix every poll (60s), which reads as teleporting — dead-reckon
     * the marker forward from its last real fix using heading+speed every second instead, reset
     * to the true position whenever [fetch] lands a new one. See [DeadReckoning]. */
    private fun startFlightsAnimation() {
        flightsAnimJob?.cancel()
        flightsAnimJob = viewModelScope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - flightsPolledAtMs
                flights.value = rawFlights.map { marker ->
                    val heading = marker.flight.heading
                    val speed = marker.flight.speedKnots
                    if (heading == null || speed == null) {
                        marker
                    } else {
                        val (lat, lng) = DeadReckoning.project(marker.flight.lat, marker.flight.lng, heading, speed, elapsed)
                        marker.copy(flight = marker.flight.copy(lat = lat, lng = lng))
                    }
                }
                delay(1000L)
            }
        }
    }

    /** Same dead-reckoning treatment as flights, applied to AIS ships only — ports/chokepoints
     * are static so [rawMaritime] is copied through unchanged for those. */
    private fun startMaritimeAnimation() {
        maritimeAnimJob?.cancel()
        maritimeAnimJob = viewModelScope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - maritimePolledAtMs
                val ships = rawMaritime.ships.map { ship ->
                    val heading = ship.heading
                    val speed = ship.speed
                    if (heading == null || speed == null) {
                        ship
                    } else {
                        val (lat, lng) = DeadReckoning.project(ship.lat, ship.lng, heading, speed, elapsed)
                        ship.copy(lat = lat, lng = lng)
                    }
                }
                maritime.value = rawMaritime.copy(ships = ships)
                delay(1000L)
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
                // Read fresh every loop rather than once, so a cadence changed in Réglages takes
                // effect on the next tick instead of requiring the layer to be toggled off/on.
                delay(pollIntervalPreferences.intervalFlow(layer).first())
            }
        }
    }

    private suspend fun fetch(layer: MapLayer, baseUrl: String) {
        when (layer) {
            MapLayer.FLIGHTS -> flightsRepo.fetch(baseUrl).also {
                rawFlights = it
                flightsPolledAtMs = System.currentTimeMillis()
                flights.value = it
                layerCache.saveFlights(it)
            }
            MapLayer.EARTHQUAKES -> earthquakesRepo.fetch(baseUrl).also {
                earthquakes.value = it
                layerCache.saveEarthquakes(it)
                checkEarthquakeAlerts(it)
            }
            MapLayer.FIRES -> firesRepo.fetch(baseUrl).also { fires.value = it; layerCache.saveFires(it) }
            MapLayer.WEATHER -> weatherRepo.fetch(baseUrl).also { weatherEvents.value = it; layerCache.saveWeather(it) }
            MapLayer.CONFLICTS -> conflictsRepo.fetch(baseUrl).also {
                conflictZones.value = it
                layerCache.saveConflicts(it)
                checkConflictAlerts(it)
            }
            MapLayer.MARITIME -> maritimeRepo.fetch(baseUrl).also {
                rawMaritime = it
                maritimePolledAtMs = System.currentTimeMillis()
                maritime.value = it
                layerCache.saveMaritime(it)
            }
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

    /** Notifies on any earthquake at or above [EARTHQUAKE_ALERT_MAGNITUDE] not already seen in a
     * previous poll — skipped entirely on the first poll of a session, which is the current
     * state rather than a batch of new events. */
    private fun checkEarthquakeAlerts(quakes: List<Earthquake>) {
        if (earthquakeAlertsBaseline) {
            quakes.forEach { quake ->
                val id = quake.id ?: return@forEach
                if (id !in seenEarthquakeIds && (quake.magnitude ?: 0.0) >= EARTHQUAKE_ALERT_MAGNITUDE) {
                    AlertNotifier.notifyEarthquake(getApplication(), quake)
                }
            }
        } else {
            earthquakeAlertsBaseline = true
        }
        seenEarthquakeIds += quakes.mapNotNull { it.id }
    }

    /** Notifies when a zone's severity crosses into [CONFLICT_ALERT_LEVELS] that it wasn't
     * already at — same first-poll skip as [checkEarthquakeAlerts]. */
    private fun checkConflictAlerts(zones: List<ConflictZone>) {
        if (conflictAlertsBaseline) {
            zones.forEach { zone ->
                val previous = lastConflictSeverity[zone.id]
                if (zone.severity in CONFLICT_ALERT_LEVELS && previous !in CONFLICT_ALERT_LEVELS) {
                    AlertNotifier.notifyConflictEscalation(getApplication(), zone)
                }
            }
        } else {
            conflictAlertsBaseline = true
        }
        zones.forEach { lastConflictSeverity[it.id] = it.severity }
    }

    private companion object {
        const val EARTHQUAKE_ALERT_MAGNITUDE = 6.0
        val CONFLICT_ALERT_LEVELS = setOf("high", "war")
    }
}
