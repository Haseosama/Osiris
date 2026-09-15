package com.osiris.app.map

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.osiris.app.data.LayerCache
import com.osiris.app.data.LayerTogglePreferences
import com.osiris.app.data.PollIntervalPreferences
import com.osiris.app.data.SatelliteCategoryPreferences
import com.osiris.app.data.model.AircraftDetail
import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.model.ConflictZone
import com.osiris.app.data.model.CyberAttack
import com.osiris.app.data.model.Earthquake
import com.osiris.app.data.model.FireEvent
import com.osiris.app.data.model.FlightMarker
import com.osiris.app.data.model.FlightRoute
import com.osiris.app.data.model.followKey
import com.osiris.app.data.model.LiveNewsFeed
import com.osiris.app.data.model.MaritimeResponse
import com.osiris.app.data.model.OsintPost
import com.osiris.app.data.model.Satellite
import com.osiris.app.data.model.SatelliteNextPass
import com.osiris.app.data.model.Ship
import com.osiris.app.data.model.TrafficIncident
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
import com.osiris.app.data.repository.TrafficRepository
import com.osiris.app.data.repository.WeatherRepository
import com.osiris.app.widget.WIDGET_EARTHQUAKE_MIN_MAGNITUDE
import com.osiris.app.widget.WIDGET_TRAFFIC_MIN_MAGNITUDE
import com.osiris.app.widget.WidgetUpdater
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val layerCache = LayerCache(application)
    private val pollIntervalPreferences = PollIntervalPreferences(application)
    private val satelliteCategoryPreferences = SatelliteCategoryPreferences(application)
    private val layerTogglePreferences = LayerTogglePreferences(application)

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
    private val trafficRepo = TrafficRepository()

    private val _layerToggles = MutableStateFlow(
        MapLayer.entries.associateWith { it.defaultEnabled }
    )
    val layerToggles: StateFlow<Map<MapLayer, Boolean>> = _layerToggles.asStateFlow()

    private val _layerErrors = MutableStateFlow<Map<MapLayer, String?>>(emptyMap())
    val layerErrors: StateFlow<Map<MapLayer, String?>> = _layerErrors.asStateFlow()

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
    val trafficIncidents = MutableStateFlow<List<TrafficIncident>>(emptyList())
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

    /** Which flight/ship/satellite (if any) the currently-open info dialog is about — distinct
     * from the matching followedXKey below, since the dialog's own "Vue cockpit" toggle can turn
     * the chase camera on/off without closing the dialog, so these need to outlive that toggle.
     * Set by [selectFlight]/[selectShip]/[selectSatellite], cleared by every [selectInfo] call.
     * At most one of the three is ever non-null at a time. */
    val selectedFlightKey = MutableStateFlow<String?>(null)
    val selectedShipKey = MutableStateFlow<Long?>(null)
    val selectedSatelliteKey = MutableStateFlow<String?>(null)

    /** Whichever flight/ship/satellite the map's chase camera is *currently* tracking, or null
     * when nothing of that kind is being followed — each a subset of its matching selectedXKey
     * (an entity can be selected/its dialog open without being followed, via the toggle). See the
     * per-entity chase-camera LaunchedEffects in MapScreen for the actual camera movement. */
    val followedFlightKey = MutableStateFlow<String?>(null)
    val followedShipKey = MutableStateFlow<Long?>(null)
    val followedSatelliteKey = MutableStateFlow<String?>(null)

    /** Bumped whenever the chase camera should ease back to a flat, north-up view — a plain
     * counter rather than a boolean so two resets in a row (dialog closed, then another entity
     * tapped and un-followed again) each still fire their own LaunchedEffect in MapScreen, which
     * a StateFlow would otherwise collapse as "no change". Only bumped from the *programmatic*
     * stop paths ([selectInfo], [setFollowing]) — deliberately NOT from [stopFollowing], the
     * map's own gesture-detected exit: there, the user's own drag/pinch/rotate already IS the
     * camera state they want, so flattening it right back out from under their fingers would
     * fight the gesture instead of respecting it. */
    private val _cameraResetTick = MutableStateFlow(0)
    val cameraResetTick: StateFlow<Int> = _cameraResetTick.asStateFlow()

    /** Shared by flights/ships/satellites/earthquakes/fires/weather/conflicts/maritime — see
     * [InfoDialogContent]. News/CCTV/OSINT keep their own selectX functions above. Closing the
     * dialog (`content == null`) also drops any pending satellite/flight enrichment fetch and
     * the drawn flight route, so a slow reply landing after the dialog is gone can't resurrect
     * either. Also always drops whichever entity was followed (any selection change — including a
     * fresh one — should end whatever chase camera was running; [selectFlight]/[selectShip]/
     * [selectSatellite] re-engage it right after for their own entity) and requests a camera
     * reset if one was actually running. */
    fun selectInfo(content: InfoDialogContent?) {
        _selectedInfo.value = content
        if (followedFlightKey.value != null || followedShipKey.value != null || followedSatelliteKey.value != null) {
            _cameraResetTick.value++
        }
        selectedFlightKey.value = null
        followedFlightKey.value = null
        selectedShipKey.value = null
        followedShipKey.value = null
        selectedSatelliteKey.value = null
        followedSatelliteKey.value = null
        if (content == null) {
            pendingSatelliteOrbitKey = null
            pendingFlightKey = null
            flightEnrichment.value = null
        }
    }

    /** The info dialog's own "Vue cockpit" toggle — turns the chase camera on/off for whichever
     * of flight/ship/satellite is currently selected, without closing the dialog. Unlike
     * [stopFollowing], turning it off here does request a camera reset: this is a deliberate user
     * action on a button, not a mid-gesture interruption. */
    fun setFollowing(following: Boolean) {
        when {
            selectedFlightKey.value != null -> followedFlightKey.value = selectedFlightKey.value.takeIf { following }
            selectedShipKey.value != null -> followedShipKey.value = selectedShipKey.value.takeIf { following }
            selectedSatelliteKey.value != null -> followedSatelliteKey.value = selectedSatelliteKey.value.takeIf { following }
        }
        if (!following) _cameraResetTick.value++
    }

    /** The map's own exit path when the user manually pans/pinches/rotates away from whatever's
     * being followed — see MapScreen's OnCameraMoveStartedListener. Doesn't touch selectedInfo or
     * the selectedXKeys: the info dialog stays open (its toggle can re-engage following), only
     * the camera stops chasing — and, deliberately, doesn't request a reset either; see
     * [cameraResetTick]. */
    fun stopFollowing() {
        followedFlightKey.value = null
        followedShipKey.value = null
        followedSatelliteKey.value = null
    }

    // Tracks which satellite the most recent selectSatellite() call was for, so a slow orbit
    // fetch that lands after the user has since tapped something else (or closed the dialog)
    // doesn't clobber whatever's on screen by then.
    private var pendingSatelliteOrbitKey: String? = null

    /** Shows what's known about [sat] immediately, then enriches it with its orbital period and
     * next visible pass once those two independent on-demand fetches come back (see
     * [SatellitesRepository.fetchOrbitPeriod]/[fetchNextPass]) — a satellite only carries a bare
     * lat/lng/alt in the main poll, unlike every other tappable layer. Run as two separate
     * launches rather than one, each publishing its own partial update: the period is cheap local
     * math (near-instant) while the pass search can take a few seconds (SGP4 stepped forward
     * looking for the next horizon crossing) plus a location fix first — waiting for both before
     * showing either would make the period look just as slow as the pass. Also engages the chase
     * camera, same as [selectFlight]/[selectShip]. */
    fun selectSatellite(sat: Satellite) {
        selectInfo(sat.toInfoDialog())
        val noradId = sat.noradId ?: return
        selectedSatelliteKey.value = noradId
        followedSatelliteKey.value = noradId
        pendingSatelliteOrbitKey = noradId

        var latestPeriod: Double? = null
        var latestPass: SatelliteNextPass? = null
        var passAttempted = false

        fun publish() {
            if (pendingSatelliteOrbitKey == noradId) {
                _selectedInfo.value = sat.toInfoDialog(latestPeriod, latestPass, passAttempted)
            }
        }

        viewModelScope.launch {
            latestPeriod = satellitesRepo.fetchOrbitPeriod("", noradId, System.currentTimeMillis())
            publish()
        }
        viewModelScope.launch {
            val location = currentLocationOrNull()
            latestPass = location?.let { (lat, lng) -> satellitesRepo.fetchNextPass(noradId, lat, lng) }
            passAttempted = true
            publish()
        }
    }

    /** One-shot device location for [selectSatellite]'s next-pass search — same cached-fix-first,
     * fresh-request-fallback shape as MapScreen's centerOnUserLocation, just wrapped as a suspend
     * function since PassPredictor needs the result before it can even start. Never throws: no
     * permission, no location provider, or genuinely no fix available (indoors, GPS off) all just
     * mean "can't compute a pass right now" — the dialog shows "aucun trouvé" either way.
     *
     * Internal rather than private: [com.osiris.app.globe.GlobeScreen] needs exactly this (its own
     * location marker and its fly-to-my-position button) and reusing it beats a second copy of the
     * same fused-location callback dance. */
    @SuppressLint("MissingPermission")
    internal suspend fun currentLocationOrNull(): Pair<Double, Double>? {
        val context = getApplication<Application>()
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return null
        val client = runCatching { LocationServices.getFusedLocationProviderClient(context) }.getOrNull() ?: return null
        return suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        cont.resume(location.latitude to location.longitude)
                    } else {
                        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
                            .addOnSuccessListener { fresh -> cont.resume(fresh?.let { it.latitude to it.longitude }) }
                            .addOnFailureListener { cont.resume(null) }
                    }
                }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    /** Everything needed is already in the last maritime poll (unlike flights/satellites, no
     * on-demand enrichment fetch) — just shows the dialog and engages the chase camera. */
    fun selectShip(ship: Ship) {
        selectInfo(ship.toInfoDialog())
        val mmsi = ship.mmsi ?: return
        selectedShipKey.value = mmsi
        followedShipKey.value = mmsi
    }

    // Same "show now, enrich once the extra fetches land" shape as selectSatellite, but for a
    // flight, à la FlightRadar24: scheduled route/ETA from one endpoint, actual flown track +
    // airframe identity from another (see FlightEnrichment) — both on demand, both drawn/shown
    // once they arrive. See the LaunchedEffect(flightEnrichment) in MapScreen for the map line.
    private var pendingFlightKey: String? = null
    val flightEnrichment = MutableStateFlow<FlightEnrichment?>(null)

    fun selectFlight(marker: FlightMarker) {
        selectInfo(marker.toInfoDialog())
        selectedFlightKey.value = marker.flight.followKey
        followedFlightKey.value = marker.flight.followKey
        val callsign = marker.flight.callsign?.trim()?.takeIf { it.isNotBlank() } ?: return
        pendingFlightKey = callsign
        viewModelScope.launch {
            // Neither fetchRoute nor fetchAircraftDetail touch the backend anymore (adsbdb/
            // hexdb/airplanes.live and adsb.lol/adsbdb directly) — baseUrl is vestigial here now.
            val routeDeferred = async { flightsRepo.fetchRoute("", marker.flight) }
            val aircraftDeferred = marker.flight.icao24?.takeIf { it.isNotBlank() }
                ?.let { hex -> async { flightsRepo.fetchAircraftDetail("", hex) } }
            val route = routeDeferred.await()
            val aircraft = aircraftDeferred?.await()
            if (pendingFlightKey == callsign) {
                val enrichment = FlightEnrichment(route, aircraft)
                flightEnrichment.value = enrichment
                if (route != null || aircraft != null) {
                    _selectedInfo.value = marker.toInfoDialog(route, aircraft)
                }
            }
        }
    }

    private val _disabledSatelliteCategories = MutableStateFlow<Set<String>>(emptySet())
    val disabledSatelliteCategories: StateFlow<Set<String>> = _disabledSatelliteCategories.asStateFlow()

    /** Hides/shows one satellite category (comms, navigation, earth_obs, military, science,
     * other) — applied to the currently-displayed set immediately (no need to wait for the next
     * poll) and persisted in the background. This is the actual control over how many satellites
     * end up animated/rendered at once; [SatellitesRepository] itself no longer caps anything. */
    fun setSatelliteCategoryEnabled(category: String, enabled: Boolean) {
        val updated = if (enabled) _disabledSatelliteCategories.value - category else _disabledSatelliteCategories.value + category
        _disabledSatelliteCategories.value = updated
        viewModelScope.launch { satelliteCategoryPreferences.setDisabled(updated) }
        rawSatellites = filterSatellitesByCategory(allSatellitesFromLastPoll)
        satellites.value = rawSatellites
    }

    private fun filterSatellitesByCategory(all: List<Satellite>): List<Satellite> {
        val disabled = _disabledSatelliteCategories.value
        return if (disabled.isEmpty()) all else all.filter { (it.category ?: "other") !in disabled }
    }

    private val pollingJobs = mutableMapOf<MapLayer, Job>()
    private var pulseJob: Job? = null
    private var flightsAnimJob: Job? = null
    private var maritimeAnimJob: Job? = null
    private var satellitesAnimJob: Job? = null

    // Last real fix from the backend, kept separate from the public [flights]/[maritime] flows
    // (which the animation ticker below overwrites every second with a dead-reckoned position)
    // so each tick always extrapolates from the true last poll rather than from its own output.
    private var rawFlights: List<FlightMarker> = emptyList()
    private var flightsPolledAtMs = 0L
    private var rawMaritime = MaritimeResponse()
    private var maritimePolledAtMs = 0L

    // allSatellitesFromLastPoll is the full catalogue from the last poll, unfiltered — kept for
    // the replay buffer and for re-deriving rawSatellites when a category is toggled without
    // waiting for the next poll. rawSatellites is that same poll filtered by category (see
    // filterSatellitesByCategory) and is what actually gets animated (re-propagated live every
    // tick, see startSatellitesAnimation) and rendered.
    private var allSatellitesFromLastPoll: List<Satellite> = emptyList()
    private var rawSatellites: List<Satellite> = emptyList()
    private var satellitesPolledAtMs = 0L

    // Rolling buffer of what flights/ships/satellites actually looked like at each poll cycle —
    // there's no server-side history, so "replay" only ever means rewinding through what this
    // session has already seen since it started, not fetching real past data. Recorded from
    // rawFlights/rawMaritime/allSatellitesFromLastPoll (the true last-poll fixes), never from the
    // animated flights/maritime/satellites flows, so a frame is always a real observed position,
    // not a dead-reckoned guess. Capped at REPLAY_BUFFER_CAPACITY frames (~oldest few tens of
    // minutes at the default poll cadences) so this can't grow unbounded over a long session.
    private val replayBuffer = ArrayDeque<ReplayFrame>()
    val isReplaying = MutableStateFlow(false)
    val replayFrameIndex = MutableStateFlow(0)
    val replayFrameTimestamps = MutableStateFlow<List<Long>>(emptyList())

    private fun recordReplayFrame() {
        if (isReplaying.value) return // don't buffer the past while looking at the past
        replayBuffer.addLast(ReplayFrame(System.currentTimeMillis(), rawFlights, rawMaritime.ships, allSatellitesFromLastPoll))
        if (replayBuffer.size > REPLAY_BUFFER_CAPACITY) replayBuffer.removeFirst()
        replayFrameTimestamps.value = replayBuffer.map { it.timestampMs }
    }

    /** Freezes the flights/maritime/satellites flows onto the most recent buffered frame and
     * stops the live dead-reckoning loops from overwriting them — see the `isReplaying` guard in
     * each `startXAnimation`. Everything else (earthquakes, fires, CCTV...) keeps updating live;
     * only the three animated layers make sense to scrub through. */
    fun enterReplay() {
        if (replayBuffer.isEmpty()) return
        isReplaying.value = true
        seekReplay(replayBuffer.size - 1)
    }

    /** Hands flights/maritime/satellites back to the live animation loops — the very next 1s
     * tick of each overwrites the frozen replay frame with the current real (or dead-reckoned)
     * position, so there's nothing to manually restore here. */
    fun exitReplay() {
        isReplaying.value = false
    }

    fun seekReplay(index: Int) {
        if (replayBuffer.isEmpty()) return
        val clamped = index.coerceIn(0, replayBuffer.size - 1)
        replayFrameIndex.value = clamped
        val frame = replayBuffer.getOrNull(clamped) ?: return
        flights.value = frame.flights
        maritime.value = rawMaritime.copy(ships = frame.ships)
        satellites.value = filterSatellitesByCategory(frame.satellites)
    }

    // Local notifications: never fire on a layer's first successful poll (that's just the
    // current state, not a new event) — only when something changes on a later one. See
    // AlertNotifier.
    private var earthquakeAlertsBaseline = false
    private val seenEarthquakeIds = mutableSetOf<String>()
    private var conflictAlertsBaseline = false
    private val lastConflictSeverity = mutableMapOf<String, String>()

    init {
        viewModelScope.launch {
            // Loaded before anything else touches satellites, so the very first poll/cache read
            // already filters correctly instead of briefly showing everything.
            _disabledSatelliteCategories.value = satelliteCategoryPreferences.disabledCategoriesFlow.first()
            // Loaded before the polling loop below reads it, so a layer the user turned on/off
            // last session starts in the right state instead of always resetting to
            // MapLayer.defaultEnabled.
            _layerToggles.value = layerTogglePreferences.load()
            loadCachedData()
            _layerToggles.value.forEach { (layer, enabled) ->
                if (enabled) {
                    startPolling(layer)
                    when (layer) {
                        MapLayer.CYBER_ATTACKS -> startCyberAttackPulseAnimation()
                        MapLayer.FLIGHTS -> startFlightsAnimation()
                        MapLayer.MARITIME -> startMaritimeAnimation()
                        MapLayer.SATELLITES -> startSatellitesAnimation()
                        else -> Unit
                    }
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(REPLAY_RECORD_INTERVAL_MS)
                recordReplayFrame()
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
        layerCache.loadSatellites()?.let {
            allSatellitesFromLastPoll = it
            satellitesPolledAtMs = System.currentTimeMillis()
            rawSatellites = filterSatellitesByCategory(it)
            satellites.value = rawSatellites
        }
        layerCache.loadNews()?.let { newsFeeds.value = it }
        layerCache.loadCyberAttacks()?.let { cyberAttacks.value = it }
        layerCache.loadCctv()?.let { cctvCameras.value = it }
        layerCache.loadOsint()?.let { osintPosts.value = it }
        layerCache.loadTraffic()?.let { trafficIncidents.value = it }
    }

    fun toggleLayer(layer: MapLayer) {
        val nowEnabled = !(_layerToggles.value[layer] ?: false)
        _layerToggles.update { it + (layer to nowEnabled) }
        viewModelScope.launch { layerTogglePreferences.save(_layerToggles.value) }
        if (nowEnabled) {
            startPolling(layer)
            when (layer) {
                MapLayer.CYBER_ATTACKS -> startCyberAttackPulseAnimation()
                MapLayer.FLIGHTS -> startFlightsAnimation()
                MapLayer.MARITIME -> startMaritimeAnimation()
                MapLayer.SATELLITES -> startSatellitesAnimation()
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
                MapLayer.SATELLITES -> {
                    satellitesAnimJob?.cancel()
                    satellitesAnimJob = null
                }
                else -> Unit
            }
        }
    }

    /** Flights only get a fresh fix every poll (60s), which reads as teleporting — dead-reckon
     * the marker forward from its last real fix using heading+speed instead, reset to the true
     * position whenever [fetch] lands a new one. See [DeadReckoning].
     *
     * Ticks at [FLIGHTS_ANIM_TICK_MS] (100ms/10Hz), not once a second: a 500kt airliner covers
     * ~250m between one-second ticks, which read as a visible hop on anything but the widest
     * zoom — GeoJsonSource.setGeoJson() (see LayersController.setFlights) just replaces the data
     * outright, no interpolation of its own, so how smooth the glide looks is entirely down to
     * how often this pushes a new position. At 100ms that's ~25m/step, well under what's
     * perceptible while panning/zoomed in on a single aircraft. */
    private fun startFlightsAnimation() {
        flightsAnimJob?.cancel()
        flightsAnimJob = viewModelScope.launch {
            while (isActive) {
                if (!isReplaying.value) {
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
                }
                delay(FLIGHTS_ANIM_TICK_MS)
            }
        }
    }

    /** Same dead-reckoning treatment as flights, applied to AIS ships only — ports/chokepoints
     * are static so [rawMaritime] is copied through unchanged for those. */
    private fun startMaritimeAnimation() {
        maritimeAnimJob?.cancel()
        maritimeAnimJob = viewModelScope.launch {
            while (isActive) {
                if (!isReplaying.value) {
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
                }
                delay(MARITIME_ANIM_TICK_MS)
            }
        }
    }

    /** Unlike flights/ships, satellites don't need dead-reckoning at all: their raw TLEs stay
     * cached in memory (see [SatellitesRepository.propagateLive]), so every tick just re-runs the
     * real SGP4/SDP4 math for "now" on the currently-displayed set — the true live position, not
     * an approximation extrapolated from the last poll. That's also what makes it look properly
     * fast: a LEO satellite's real ground speed is ~7.5km/s, so showing its *actual* position
     * every tick (rather than an interpolated guess) is both more accurate and more dramatic than
     * dead-reckoning ever was. */
    private fun startSatellitesAnimation() {
        satellitesAnimJob?.cancel()
        satellitesAnimJob = viewModelScope.launch {
            while (isActive) {
                if (!isReplaying.value && rawSatellites.isNotEmpty()) {
                    val ids = rawSatellites.mapNotNull { it.noradId }.toSet()
                    val live = satellitesRepo.propagateLive(ids).associateBy { it.noradId }
                    satellites.value = rawSatellites.map { sat -> live[sat.noradId] ?: sat }
                }
                delay(SATELLITES_ANIM_TICK_MS)
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
                runCatching { fetch(layer, "") }
                    .onSuccess { setError(layer, null) }
                    .onFailure { setError(layer, it.message ?: "Erreur réseau") }
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
            MapLayer.SATELLITES -> satellitesRepo.fetch(baseUrl).also {
                allSatellitesFromLastPoll = it
                satellitesPolledAtMs = System.currentTimeMillis()
                rawSatellites = filterSatellitesByCategory(it)
                satellites.value = rawSatellites
                layerCache.saveSatellites(it)
            }
            MapLayer.NEWS -> liveNewsRepo.fetch(baseUrl).also { newsFeeds.value = it; layerCache.saveNews(it) }
            MapLayer.CYBER_ATTACKS -> cyberAttacksRepo.fetch(baseUrl).also { cyberAttacks.value = it; layerCache.saveCyberAttacks(it) }
            MapLayer.CCTV -> cctvRepo.fetch(baseUrl).also { cctvCameras.value = it; layerCache.saveCctv(it) }
            MapLayer.OSINT -> osintRepo.fetch(baseUrl).also { osintPosts.value = it; layerCache.saveOsint(it) }
            MapLayer.TRAFFIC -> trafficRepo.fetch(baseUrl).also { trafficIncidents.value = it; layerCache.saveTraffic(it) }
        }
        // Only these five feed the home screen widget — a quick "what's going on" glance, not a
        // full status board, so no point pushing an update for every other layer's poll too.
        when (layer) {
            MapLayer.FLIGHTS -> WidgetUpdater.update(getApplication(), flightsCount = flights.value.size)
            MapLayer.CYBER_ATTACKS -> WidgetUpdater.update(getApplication(), cyberCount = cyberAttacks.value.size)
            MapLayer.CONFLICTS -> WidgetUpdater.update(
                getApplication(),
                conflictsCount = conflictZones.value.count { it.severity in CONFLICT_ALERT_LEVELS },
            )
            MapLayer.EARTHQUAKES -> WidgetUpdater.update(
                getApplication(),
                earthquakesCount = earthquakes.value.count { (it.magnitude ?: 0.0) >= WIDGET_EARTHQUAKE_MIN_MAGNITUDE },
            )
            MapLayer.TRAFFIC -> WidgetUpdater.update(
                getApplication(),
                trafficCount = trafficIncidents.value.count { (it.magnitude ?: 0) >= WIDGET_TRAFFIC_MIN_MAGNITUDE },
            )
            else -> {}
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
        const val REPLAY_BUFFER_CAPACITY = 40
        const val REPLAY_RECORD_INTERVAL_MS = 30_000L

        /** Dead-reckoning push rate for flights/maritime — see [startFlightsAnimation]. 100ms
         * (10Hz) rather than the old 1s: DeadReckoning.project() already takes real elapsed time
         * so accuracy doesn't depend on cadence, only how often GeoJsonSource.setGeoJson() gets a
         * new (smaller-delta) position to snap to. */
        const val FLIGHTS_ANIM_TICK_MS = 100L
        const val MARITIME_ANIM_TICK_MS = 100L

        /** Live re-propagation rate for satellites — see [startSatellitesAnimation]. Coarser than
         * the other two because each tick is real SGP4 math (bounded to the displayed set, but
         * still actual CPU work, not a free interpolation) rather than arithmetic — 250ms is
         * already several km of motion per step at LEO orbital speed, plenty smooth without
         * repropagating 4x more often than needed. */
        const val SATELLITES_ANIM_TICK_MS = 250L
    }
}

/** One buffered instant of the three animated layers, as they actually were at the last poll —
 * see [MapViewModel.recordReplayFrame]. */
private data class ReplayFrame(
    val timestampMs: Long,
    val flights: List<FlightMarker>,
    val ships: List<Ship>,
    val satellites: List<Satellite>,
)

/** The two on-demand flight fetches combined — [route] has the scheduled origin/destination/ETA,
 * [aircraft] has the airframe's identity and its actual flown track. Either can be null on its
 * own (a route lookup miss, or an aircraft with no trace history) without the other. */
data class FlightEnrichment(val route: FlightRoute?, val aircraft: AircraftDetail?) {
    /** The real flown path when adsb.lol has one for this airframe, else the synthetic
     * great-circle route — whichever MapScreen should actually draw on the map. */
    val trackForMap: List<List<Double>>?
        get() = aircraft?.track?.takeIf { it.isNotEmpty() } ?: route?.arc?.takeIf { it.isNotEmpty() }
}
