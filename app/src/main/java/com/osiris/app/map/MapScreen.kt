package com.osiris.app.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.osiris.app.R
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.osiris.app.data.MapViewPreferences
import com.osiris.app.data.SavedMapView
import com.osiris.app.data.model.followKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

/** OpenFreeMap's hosted vector style — free, keyless, unlimited, no account needed (in the
 * same "no third-party billing account" spirit as OpenStreetMap in Romurbex). Noticeably more
 * detailed than MapLibre's own bare demo tiles: buildings, land use, transit lines. */
private const val STREET_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

/** Esri World Imagery — same free, keyless satellite source Romurbex already uses via osmdroid
 * (SatelliteTileSource.kt), here wired up as a plain MapLibre raster source instead, plus its
 * matching boundaries/places overlay since satellite imagery alone carries no place names. */
private const val ESRI_IMAGERY_URL =
    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
private const val ESRI_LABELS_URL =
    "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}"

private enum class MapStyleMode { STREET, SATELLITE }

/** Chase-camera settings for a followed flight (see the LaunchedEffect(followedFlightKey)
 * below) — a tilted, zoomed-in, heading-aligned view that tracks the aircraft. [FOLLOW_EASE_MS]
 * is kept just under the flights animation loop's 1s tick (MapViewModel.startFlightsAnimation)
 * so each ease finishes just before the next position update arrives, instead of visibly
 * catching up or overshooting. */
private const val FLIGHT_FOLLOW_ZOOM = 14.5
private const val FLIGHT_FOLLOW_TILT = 55.0
private const val FLIGHT_FOLLOW_EASE_MS = 950

private fun satelliteStyleBuilder(): Style.Builder =
    Style.Builder()
        .withSource(RasterSource("esri-imagery", TileSet("2.1.0", ESRI_IMAGERY_URL), 256))
        .withLayer(RasterLayer("esri-imagery-layer", "esri-imagery"))
        .withSource(RasterSource("esri-labels", TileSet("2.1.0", ESRI_LABELS_URL), 256))
        .withLayer(RasterLayer("esri-labels-layer", "esri-labels"))

private val DEFAULT_CAMERA = CameraPosition.Builder().target(LatLng(20.0, 0.0)).zoom(1.5).build()

/** Matches the categories LayersController already tints satellites by. Shown as a filter row
 * under the layer chips only while Satellites is active — this is the user's own control over
 * how many of the ~18-19k satellites end up animated/rendered, since comms (Starlink) and other
 * (tracked debris) alone are each in the thousands. */
private val SATELLITE_CATEGORIES = listOf(
    "comms" to "Comms (Starlink…)",
    "navigation" to "Navigation",
    "earth_obs" to "Observation Terre",
    "military" to "Militaire",
    "science" to "Science",
    "other" to "Autres / débris",
)

/** One match in the search overlay — a name/place worth jumping to across the layers that carry
 * one (flights by callsign, cameras by name/city, satellites/ports/ships by name). [onSelect]
 * opens whatever dialog that entity normally opens on a map tap. */
private data class MapSearchResult(val label: String, val subtitle: String?, val lat: Double, val lng: Double, val onSelect: () -> Unit)

@Composable
fun MapScreen(onOpenSettings: () -> Unit, onOpenRecon: () -> Unit, viewModel: MapViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val backendUrl by viewModel.backendUrl.collectAsStateWithLifecycle()
    val backendUnreachable by viewModel.backendUnreachable.collectAsStateWithLifecycle()
    val layerToggles by viewModel.layerToggles.collectAsStateWithLifecycle()
    val layerErrors by viewModel.layerErrors.collectAsStateWithLifecycle()
    val flights by viewModel.flights.collectAsStateWithLifecycle()
    val earthquakes by viewModel.earthquakes.collectAsStateWithLifecycle()
    val fires by viewModel.fires.collectAsStateWithLifecycle()
    val weatherEvents by viewModel.weatherEvents.collectAsStateWithLifecycle()
    val conflictZones by viewModel.conflictZones.collectAsStateWithLifecycle()
    val maritime by viewModel.maritime.collectAsStateWithLifecycle()
    val satellites by viewModel.satellites.collectAsStateWithLifecycle()
    val newsFeeds by viewModel.newsFeeds.collectAsStateWithLifecycle()
    val cyberAttacks by viewModel.cyberAttacks.collectAsStateWithLifecycle()
    val cyberAttackPulses by viewModel.cyberAttackPulses.collectAsStateWithLifecycle()
    val cctvCameras by viewModel.cctvCameras.collectAsStateWithLifecycle()
    val osintPosts by viewModel.osintPosts.collectAsStateWithLifecycle()
    val trafficIncidents by viewModel.trafficIncidents.collectAsStateWithLifecycle()
    val selectedNewsFeed by viewModel.selectedNewsFeed.collectAsStateWithLifecycle()
    val selectedCctvCamera by viewModel.selectedCctvCamera.collectAsStateWithLifecycle()
    val selectedOsintPost by viewModel.selectedOsintPost.collectAsStateWithLifecycle()
    val selectedInfo by viewModel.selectedInfo.collectAsStateWithLifecycle()
    val followedFlightKey by viewModel.followedFlightKey.collectAsStateWithLifecycle()
    val disabledSatelliteCategories by viewModel.disabledSatelliteCategories.collectAsStateWithLifecycle()
    val flightEnrichment by viewModel.flightEnrichment.collectAsStateWithLifecycle()
    val isReplaying by viewModel.isReplaying.collectAsStateWithLifecycle()
    val replayFrameIndex by viewModel.replayFrameIndex.collectAsStateWithLifecycle()
    val replayFrameTimestamps by viewModel.replayFrameTimestamps.collectAsStateWithLifecycle()

    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var layersController by remember { mutableStateOf<LayersController?>(null) }
    var mapStyleMode by remember { mutableStateOf(MapStyleMode.SATELLITE) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showIntelHud by remember { mutableStateOf(false) }
    var hudCameraSnapshot by remember { mutableStateOf<HudCameraSnapshot?>(null) }
    var hudTheme by remember { mutableStateOf(HudTheme.DEFAULT) }
    var isReplayAutoPlaying by remember { mutableStateOf(false) }

    val mapViewPreferences = remember { MapViewPreferences(context) }
    val coroutineScope = rememberCoroutineScope()
    var savedViewOnLaunch by remember { mutableStateOf<SavedMapView?>(null) }
    var hasCheckedForSavedView by remember { mutableStateOf(false) }

    // Only recomputed when the query or one of these lists changes — cctvCameras alone can run
    // to ~17k entries, so this must stay memoized rather than re-filtering on every recomposition
    // (flights re-renders every second once DeadReckoning kicks in, which would otherwise refilter
    // CCTV for nothing every tick).
    val searchResults: List<MapSearchResult> = remember(searchQuery, flights, cctvCameras, satellites, maritime) {
        val query = searchQuery.trim()
        if (query.length < 2) {
            emptyList()
        } else {
            buildList {
                flights.forEach { marker ->
                    val callsign = marker.flight.callsign?.trim().orEmpty()
                    if (callsign.contains(query, ignoreCase = true)) {
                        add(
                            MapSearchResult(callsign, marker.category.name, marker.flight.lat, marker.flight.lng) {
                                viewModel.selectFlight(marker)
                            }
                        )
                    }
                }
                cctvCameras.forEach { camera ->
                    val name = camera.name.orEmpty()
                    val city = camera.city.orEmpty()
                    if (name.contains(query, ignoreCase = true) || city.contains(query, ignoreCase = true)) {
                        add(
                            MapSearchResult(
                                name.ifBlank { "Caméra" },
                                listOfNotNull(camera.city, camera.source).joinToString(" · ").ifBlank { null },
                                camera.lat,
                                camera.lng,
                            ) { viewModel.selectCctvCamera(camera) }
                        )
                    }
                }
                satellites.forEach { sat ->
                    if (sat.name.contains(query, ignoreCase = true)) {
                        add(MapSearchResult(sat.name, sat.category, sat.lat, sat.lng) { viewModel.selectSatellite(sat) })
                    }
                }
                maritime.ports.forEach { port ->
                    if (port.name.contains(query, ignoreCase = true)) {
                        add(MapSearchResult(port.name, port.type, port.lat, port.lng) { viewModel.selectInfo(port.toInfoDialog()) })
                    }
                }
                maritime.ships.forEach { ship ->
                    val name = ship.name.orEmpty()
                    if (name.contains(query, ignoreCase = true)) {
                        add(MapSearchResult(name, ship.type, ship.lat, ship.lng) { viewModel.selectInfo(ship.toInfoDialog()) })
                    }
                }
            }.take(30)
        }
    }

    fun selectSearchResult(result: MapSearchResult) {
        maplibreMap?.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(result.lat, result.lng), 10.0))
        result.onSelect()
        searchActive = false
        searchQuery = ""
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map -> maplibreMap = map }
    }

    // Loaded once at launch, before the camera/style effects below need it — the saved style
    // (if any) is applied here; the saved camera position is applied by the effect further down,
    // once both this check and the map itself are ready.
    LaunchedEffect(Unit) {
        savedViewOnLaunch = mapViewPreferences.load()
        savedViewOnLaunch?.let { saved ->
            mapStyleMode = runCatching { MapStyleMode.valueOf(saved.styleMode) }.getOrDefault(MapStyleMode.SATELLITE)
        }
        hasCheckedForSavedView = true
    }

    // Positions the camera once the map is ready and we know whether there's a saved view: that
    // view wins if present, otherwise the world-view default (which the location effect below
    // then replaces with the user's GPS position — but only when nothing was saved to begin with).
    LaunchedEffect(maplibreMap, hasCheckedForSavedView) {
        val map = maplibreMap ?: return@LaunchedEffect
        if (!hasCheckedForSavedView) return@LaunchedEffect
        val saved = savedViewOnLaunch
        val position = if (saved != null) {
            CameraPosition.Builder()
                .target(LatLng(saved.lat, saved.lng))
                .zoom(saved.zoom)
                .bearing(saved.bearing)
                .tilt(saved.tilt)
                .build()
        } else {
            DEFAULT_CAMERA
        }
        map.moveCamera(CameraUpdateFactory.newCameraPosition(position))
    }

    // Persists the current camera + style whenever the user stops moving the map, so the next
    // launch can restore it — cheap enough to write on every idle, no debouncing needed.
    LaunchedEffect(maplibreMap) {
        val map = maplibreMap ?: return@LaunchedEffect
        map.addOnCameraIdleListener {
            val target = map.cameraPosition.target ?: return@addOnCameraIdleListener
            coroutineScope.launch {
                mapViewPreferences.save(
                    SavedMapView(
                        lat = target.latitude,
                        lng = target.longitude,
                        zoom = map.cameraPosition.zoom,
                        bearing = map.cameraPosition.bearing,
                        tilt = map.cameraPosition.tilt,
                        styleMode = mapStyleMode.name,
                    )
                )
            }
        }
    }

    // Steps the replay one frame forward on a timer while playing — stops itself (rather than
    // looping) once it reaches the newest buffered frame, since "replaying" past the point where
    // live data resumes wouldn't mean anything.
    LaunchedEffect(isReplayAutoPlaying, isReplaying) {
        if (!isReplayAutoPlaying || !isReplaying) return@LaunchedEffect
        while (coroutineContext.isActive) {
            delay(800L)
            val next = replayFrameIndex + 1
            if (next >= replayFrameTimestamps.size) {
                isReplayAutoPlaying = false
                break
            }
            viewModel.seekReplay(next)
        }
    }

    // Feeds the intel-HUD overlay's live coordinate/zoom readout — only attached while the HUD
    // is actually showing, since this fires on every single pan/zoom frame (unlike the idle
    // listener above, which only needs to fire once movement stops).
    DisposableEffect(maplibreMap, showIntelHud) {
        val map = maplibreMap
        if (map == null || !showIntelHud) return@DisposableEffect onDispose {}
        val listener = MapLibreMap.OnCameraMoveListener {
            val target = map.cameraPosition.target ?: return@OnCameraMoveListener
            hudCameraSnapshot = HudCameraSnapshot(
                lat = target.latitude,
                lng = target.longitude,
                zoom = map.cameraPosition.zoom,
                bearing = map.cameraPosition.bearing,
            )
        }
        map.addOnCameraMoveListener(listener)
        // Seed it immediately — otherwise the readout sits on its "--" placeholder until the
        // user's first pan/zoom, even though the real position was already known.
        map.cameraPosition.target?.let { target ->
            hudCameraSnapshot = HudCameraSnapshot(
                lat = target.latitude,
                lng = target.longitude,
                zoom = map.cameraPosition.zoom,
                bearing = map.cameraPosition.bearing,
            )
        }
        onDispose { map.removeOnCameraMoveListener(listener) }
    }

    // Also persist right away on a style toggle, so switching to satellite and immediately
    // killing the app (no further camera movement) still remembers the new style.
    LaunchedEffect(mapStyleMode) {
        val map = maplibreMap ?: return@LaunchedEffect
        if (!hasCheckedForSavedView) return@LaunchedEffect
        val target = map.cameraPosition.target ?: return@LaunchedEffect
        mapViewPreferences.save(
            SavedMapView(
                lat = target.latitude,
                lng = target.longitude,
                zoom = map.cameraPosition.zoom,
                bearing = map.cameraPosition.bearing,
                tilt = map.cameraPosition.tilt,
                styleMode = mapStyleMode.name,
            )
        )
    }

    // Re-runs on every mode switch, not just once — setStyle() replaces the whole Style object,
    // so layersController is rebuilt against it; every LaunchedEffect below that's keyed on
    // layersController then reruns automatically and repaints all current layer data onto the
    // new style, without needing to wait for the next network poll.
    LaunchedEffect(maplibreMap, mapStyleMode) {
        val map = maplibreMap ?: return@LaunchedEffect
        val builder = when (mapStyleMode) {
            MapStyleMode.STREET -> Style.Builder().fromUri(STREET_STYLE_URL)
            MapStyleMode.SATELLITE -> satelliteStyleBuilder()
        }
        map.setStyle(builder) { style ->
            layersController = LayersController(style, context)
        }
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasLocationPermission = granted }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        // Below Android 13, notifications don't need a runtime prompt — POST_NOTIFICATIONS
        // itself doesn't exist as a concept pre-Tiramisu, they're just allowed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(maplibreMap, hasLocationPermission, hasCheckedForSavedView) {
        val map = maplibreMap
        // Only on a fresh install: once a view has ever been saved, restoring it takes priority
        // over re-centering on wherever the user happens to physically be right now.
        if (map != null && hasLocationPermission && hasCheckedForSavedView && savedViewOnLaunch == null) {
            centerOnUserLocation(context, map)
        }
    }

    // A persistent "blue dot" marker for the device's own position — distinct from the
    // MyLocation button above, which only recenters the camera once on tap. Keyed on
    // layersController (not just maplibreMap) since a style switch replaces the whole Style
    // object and wipes the component's layers with it, so it needs reactivating against the
    // new one each time — same reason layersController itself is rebuilt on every style change.
    // Wrapped in runCatching: an uncaught exception here (a flaky location engine, a permission
    // race) would otherwise propagate out of this LaunchedEffect and take the whole composition
    // down with it — every other layer (flights included) stops repainting, not just this one.
    LaunchedEffect(maplibreMap, layersController, hasLocationPermission) {
        val map = maplibreMap ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        if (!hasLocationPermission) return@LaunchedEffect
        runCatching {
            val locationComponent = map.locationComponent
            locationComponent.activateLocationComponent(
                LocationComponentActivationOptions.builder(context, style).build()
            )
            locationComponent.isLocationComponentEnabled = true
            // NONE: the dot never drives the camera itself, only the MyLocation button does —
            // two independent behaviours would otherwise fight over what "recentering" means.
            locationComponent.cameraMode = CameraMode.NONE
            locationComponent.renderMode = RenderMode.COMPASS
        }
    }

    // Chase camera for a tapped flight (MapViewModel.selectFlight/followedFlightKey) — re-runs
    // every time `flights` ticks (the dead-reckoning loop updates it every second while the
    // layer is live, see startFlightsAnimation), easing the camera to the aircraft's current
    // position with the map bearing aligned to its heading, cockpit/chase-view style. Ends when
    // followedFlightKey goes back to null: the info dialog closing (selectInfo(null)) or the
    // user manually moving the map (the OnCameraMoveStartedListener below) both clear it.
    LaunchedEffect(maplibreMap, flights, followedFlightKey) {
        val map = maplibreMap ?: return@LaunchedEffect
        val key = followedFlightKey ?: return@LaunchedEffect
        val marker = flights.firstOrNull { it.flight.followKey == key } ?: return@LaunchedEffect
        val position = CameraPosition.Builder()
            .target(LatLng(marker.flight.lat, marker.flight.lng))
            .zoom(FLIGHT_FOLLOW_ZOOM)
            .tilt(FLIGHT_FOLLOW_TILT)
            .bearing(marker.flight.heading ?: map.cameraPosition.bearing)
            .build()
        map.easeCamera(CameraUpdateFactory.newCameraPosition(position), FLIGHT_FOLLOW_EASE_MS)
    }

    LaunchedEffect(layersController, flights) { layersController?.setFlights(flights) }
    LaunchedEffect(layersController, flightEnrichment) { layersController?.setFlightTrack(flightEnrichment?.trackForMap) }
    LaunchedEffect(layersController, earthquakes) { layersController?.setEarthquakes(earthquakes) }
    LaunchedEffect(layersController, fires) { layersController?.setFires(fires) }
    LaunchedEffect(layersController, weatherEvents) { layersController?.setWeatherEvents(weatherEvents) }
    LaunchedEffect(layersController, conflictZones) { layersController?.setConflictZones(conflictZones) }
    LaunchedEffect(layersController, maritime) { layersController?.setMaritime(maritime) }
    LaunchedEffect(layersController, satellites) { layersController?.setSatellites(satellites) }
    LaunchedEffect(layersController, newsFeeds) { layersController?.setNewsFeeds(newsFeeds) }
    LaunchedEffect(layersController, cyberAttacks) { layersController?.setCyberAttacks(cyberAttacks) }
    LaunchedEffect(layersController, cyberAttackPulses) { layersController?.setCyberAttackPulses(cyberAttackPulses) }
    LaunchedEffect(layersController, cctvCameras) { layersController?.setCctv(cctvCameras) }
    LaunchedEffect(layersController, osintPosts) { layersController?.setOsintPosts(osintPosts) }
    LaunchedEffect(layersController, trafficIncidents) { layersController?.setTrafficIncidents(trafficIncidents) }
    LaunchedEffect(layersController, layerToggles) {
        layersController?.let { controller ->
            layerToggles.forEach { (layer, enabled) -> controller.setLayerVisible(layer, enabled) }
        }
    }

    LaunchedEffect(maplibreMap) {
        val map = maplibreMap ?: return@LaunchedEffect

        map.addOnMapClickListener { latLng ->
            val screenPoint = map.projection.toScreenLocation(latLng)

            /** Looks up the tapped feature's `idx` property (see LayersController) in [items]
             * and, if found, shows its info dialog. True if this layer consumed the tap. */
            fun <T> handleInfoTap(layerId: String, items: List<T>, toInfo: (T) -> InfoDialogContent): Boolean {
                val idx = map.queryRenderedFeatures(screenPoint, layerId).firstOrNull()
                    ?.getNumberProperty("idx")?.toInt() ?: return false
                val item = items.getOrNull(idx) ?: return false
                viewModel.selectInfo(toInfo(item))
                return true
            }

            /** Zooms into a tapped cluster bubble (flights/satellites/CCTV all cluster densely
             * packed points, see LayersController) instead of doing nothing on tap. */
            fun tryClusterTap(clusterLayerId: String, sourceId: String): Boolean {
                val clusterFeature = map.queryRenderedFeatures(screenPoint, clusterLayerId).firstOrNull() ?: return false
                val expansionZoom = layersController?.clusterExpansionZoom(sourceId, clusterFeature) ?: return false
                map.easeCamera(CameraUpdateFactory.newLatLngZoom(latLng, expansionZoom.toDouble()))
                return true
            }

            val newsId = map.queryRenderedFeatures(screenPoint, "news-layer").firstOrNull()
                ?.getStringProperty("id")
            val tappedFeed = newsFeeds.firstOrNull { it.id == newsId }
            if (tappedFeed != null) {
                if (tappedFeed.embedAllowed) {
                    viewModel.selectNewsFeed(tappedFeed)
                } else {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tappedFeed.url)))
                }
                return@addOnMapClickListener true
            }

            if (tryClusterTap("cctv-clusters", "cctv-source")) return@addOnMapClickListener true

            // Not routed through handleInfoTap: selecting a flight needs two extra on-demand
            // network calls (route + aircraft trace), unlike every other layer here which has
            // everything it needs from the last poll already. See MapViewModel.selectFlight.
            val flightIdx = map.queryRenderedFeatures(screenPoint, "flights-layer").firstOrNull()
                ?.getNumberProperty("idx")?.toInt()
            val tappedFlight = flightIdx?.let { flights.getOrNull(it) }
            if (tappedFlight != null) {
                viewModel.selectFlight(tappedFlight)
                return@addOnMapClickListener true
            }

            if (handleInfoTap("earthquakes-layer", earthquakes) { it.toInfoDialog() }) return@addOnMapClickListener true
            if (handleInfoTap("fires-layer", fires) { it.toInfoDialog() }) return@addOnMapClickListener true
            if (handleInfoTap("weather-layer", weatherEvents) { it.toInfoDialog() }) return@addOnMapClickListener true
            if (handleInfoTap("conflicts-layer", conflictZones) { it.toInfoDialog() }) return@addOnMapClickListener true
            if (handleInfoTap("ports-layer", maritime.ports) { it.toInfoDialog() }) return@addOnMapClickListener true
            if (handleInfoTap("chokepoints-layer", maritime.chokepoints) { it.toInfoDialog() }) return@addOnMapClickListener true
            if (handleInfoTap("ships-layer", maritime.ships) { it.toInfoDialog() }) return@addOnMapClickListener true
            if (handleInfoTap("cyber-attacks-layer", cyberAttacks) { it.toInfoDialog() }) return@addOnMapClickListener true
            if (handleInfoTap("traffic-layer", trafficIncidents) { it.toInfoDialog() }) return@addOnMapClickListener true

            // Not routed through handleInfoTap: selecting a satellite needs an extra on-demand
            // network call for its orbital period (see MapViewModel.selectSatellite), unlike
            // every other layer here which has everything it needs from the last poll already.
            val satelliteIdx = map.queryRenderedFeatures(screenPoint, "satellites-layer").firstOrNull()
                ?.getNumberProperty("idx")?.toInt()
            val tappedSatellite = satelliteIdx?.let { satellites.getOrNull(it) }
            if (tappedSatellite != null) {
                viewModel.selectSatellite(tappedSatellite)
                return@addOnMapClickListener true
            }

            val cameraId = map.queryRenderedFeatures(screenPoint, "cctv-unclustered").firstOrNull()
                ?.getStringProperty("id")
            val tappedCamera = cctvCameras.firstOrNull { it.id == cameraId }
            if (tappedCamera != null) {
                viewModel.selectCctvCamera(tappedCamera)
                return@addOnMapClickListener true
            }

            val postId = map.queryRenderedFeatures(screenPoint, "osint-layer").firstOrNull()
                ?.getStringProperty("id")
            val tappedPost = osintPosts.firstOrNull { it.id == postId }
            if (tappedPost != null) {
                viewModel.selectOsintPost(tappedPost)
                return@addOnMapClickListener true
            }

            false
        }

        // Chase-camera exit path: only a real finger gesture (drag/pinch/rotate) should cancel
        // following — the follow effect's own easeCamera() calls report REASON_API_ANIMATION,
        // not REASON_API_GESTURE, so this doesn't fight itself every tick.
        map.addOnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                viewModel.stopFollowingFlight()
            }
        }
    }

    selectedNewsFeed?.let { feed ->
        NewsPlayerDialog(feed = feed, onDismiss = { viewModel.selectNewsFeed(null) })
    }
    selectedCctvCamera?.let { camera ->
        CctvViewerDialog(camera = camera, backendUrl = backendUrl, onDismiss = { viewModel.selectCctvCamera(null) })
    }
    selectedOsintPost?.let { post ->
        OsintPostDialog(post = post, onDismiss = { viewModel.selectOsintPost(null) })
    }
    selectedInfo?.let { info ->
        EntityInfoDialog(content = info, onDismiss = { viewModel.selectInfo(null) })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        if (showIntelHud) {
            IntelHudOverlay(
                snapshot = hudCameraSnapshot,
                theme = hudTheme,
                onThemeChange = { hudTheme = it },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = 48.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(
                        // Not R.mipmap.ic_launcher: at API 26+ that resource id resolves to the
                        // mipmap-anydpi-v26 <adaptive-icon> XML, which painterResource() can't
                        // load (it only supports VectorDrawable XML or raster PNG/JPG/WEBP) —
                        // crashes with IllegalArgumentException on every launch. app_icon is a
                        // plain per-density raster copy with no adaptive-icon XML shadowing it.
                        painter = painterResource(R.mipmap.app_icon),
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Text(
                        "OSIRIS",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HudIconButton(
                        icon = if (searchActive) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = if (searchActive) "Fermer la recherche" else "Rechercher",
                        active = searchActive,
                        onClick = { searchActive = !searchActive; if (!searchActive) searchQuery = "" },
                    )
                    HudIconButton(
                        icon = Icons.Filled.Layers,
                        contentDescription = "Vue satellite",
                        active = mapStyleMode == MapStyleMode.SATELLITE,
                        onClick = {
                            mapStyleMode = if (mapStyleMode == MapStyleMode.STREET) {
                                MapStyleMode.SATELLITE
                            } else {
                                MapStyleMode.STREET
                            }
                        },
                    )
                    HudIconButton(
                        icon = Icons.Filled.CenterFocusStrong,
                        contentDescription = "HUD renseignement",
                        active = showIntelHud,
                        onClick = { showIntelHud = !showIntelHud },
                    )
                    HudIconButton(
                        icon = Icons.Filled.History,
                        contentDescription = if (isReplaying) "Revenir au direct" else "Rejouer",
                        active = isReplaying,
                        onClick = {
                            if (isReplaying) {
                                isReplayAutoPlaying = false
                                viewModel.exitReplay()
                            } else {
                                viewModel.enterReplay()
                            }
                        },
                    )
                    HudIconButton(icon = Icons.Filled.Build, contentDescription = "RECON", onClick = onOpenRecon)
                    HudIconButton(icon = Icons.Filled.Settings, contentDescription = "Réglages", onClick = onOpenSettings)
                }
            }

            if (searchActive) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    tonalElevation = 3.dp,
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Vol, caméra, navire, port, satellite…") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (searchResults.isNotEmpty()) {
                            LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                                items(searchResults) { result ->
                                    Surface(
                                        onClick = { selectSearchResult(result) },
                                        color = MaterialTheme.colorScheme.surface,
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp)) {
                                            Text(result.label, style = MaterialTheme.typography.bodyMedium)
                                            result.subtitle?.let {
                                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (searchQuery.trim().length >= 2) {
                            Text(
                                "Aucun résultat parmi les couches actuellement chargées",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
            }

            if (backendUrl.isBlank()) {
                Surface(
                    onClick = onOpenSettings,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                ) {
                    Text(
                        "Configure le backend dans Réglages →",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (backendUnreachable) {
                Surface(
                    onClick = onOpenSettings,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                ) {
                    Text(
                        "Backend injoignable — vérifie qu'il est démarré et l'URL dans Réglages →",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MapLayer.entries) { layer ->
                    LayerToggleChip(
                        layer = layer,
                        enabled = layerToggles[layer] == true,
                        error = layerErrors[layer],
                        onClick = { viewModel.toggleLayer(layer) },
                    )
                }
            }

            if (layerToggles[MapLayer.SATELLITES] == true) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(SATELLITE_CATEGORIES) { (category, label) ->
                        val enabled = category !in disabledSatelliteCategories
                        FilterChip(
                            selected = enabled,
                            onClick = { viewModel.setSatelliteCategoryEnabled(category, !enabled) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                val map = maplibreMap
                if (map == null) return@FloatingActionButton
                if (hasLocationPermission) {
                    centerOnUserLocation(context, map)
                } else {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = "Actualiser ma position")
        }

        if (isReplaying) {
            ReplayBar(
                frameIndex = replayFrameIndex,
                frameTimestamps = replayFrameTimestamps,
                isAutoPlaying = isReplayAutoPlaying,
                onSeek = { viewModel.seekReplay(it) },
                onTogglePlay = { isReplayAutoPlaying = !isReplayAutoPlaying },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 90.dp),
            )
        }
    }
}

/** No server-side history exists — this only ever scrubs through the flights/ships/satellites
 * positions this session has already buffered locally (see [MapViewModel.enterReplay]), oldest
 * frame at index 0. [onTogglePlay] just flips a local "auto-advance" flag the caller owns; the
 * actual stepping happens in a `LaunchedEffect` in [MapScreen] so it survives this composable's
 * own recompositions. */
@Composable
private fun ReplayBar(
    frameIndex: Int,
    frameTimestamps: List<Long>,
    isAutoPlaying: Boolean,
    onSeek: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (frameTimestamps.isEmpty()) return
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "REJEU — ${formatReplayTimestamp(frameTimestamps.getOrNull(frameIndex))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "${frameIndex + 1} / ${frameTimestamps.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HudIconButton(
                    icon = if (isAutoPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isAutoPlaying) "Pause" else "Lecture",
                    active = isAutoPlaying,
                    onClick = onTogglePlay,
                )
                Slider(
                    value = frameIndex.toFloat(),
                    onValueChange = { onSeek(it.toInt()) },
                    valueRange = 0f..(frameTimestamps.size - 1).coerceAtLeast(1).toFloat(),
                    steps = (frameTimestamps.size - 2).coerceAtLeast(0),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
            }
        }
    }
}

private fun formatReplayTimestamp(epochMs: Long?): String {
    if (epochMs == null) return "--:--:--"
    val instant = java.time.Instant.ofEpochMilli(epochMs)
    val local = instant.atZone(java.time.ZoneId.systemDefault())
    return "%02d:%02d:%02d".format(local.hour, local.minute, local.second)
}

/** A "glass panel" control button — dark translucent circle with a cyan ring, brighter when
 * [active] — used for every icon control on the map instead of Material's plain flat IconButton,
 * for a HUD-console look consistent across the whole top bar. */
@Composable
private fun HudIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, active: Boolean = false) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        border = BorderStroke(1.dp, accent.copy(alpha = if (active) 0.9f else 0.35f)),
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (active) accent else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LayerToggleChip(layer: MapLayer, enabled: Boolean, error: String?, onClick: () -> Unit) {
    BadgedBox(badge = { if (error != null) Badge(containerColor = MaterialTheme.colorScheme.error) }) {
        FilterChip(
            selected = enabled,
            onClick = onClick,
            label = { Text(layer.label, style = MaterialTheme.typography.labelSmall) },
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                selectedLabelColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

/**
 * `lastLocation` is often null on a cold start (no cached fix yet), which is exactly when we
 * most want to center the map on the user — so fall back to requesting a fresh fix instead of
 * silently doing nothing.
 */
@SuppressLint("MissingPermission")
private fun centerOnUserLocation(context: Context, map: MapLibreMap) {
    val client = LocationServices.getFusedLocationProviderClient(context)
    client.lastLocation.addOnSuccessListener { location ->
        if (location != null) {
            map.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 8.0))
        } else {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { fresh ->
                    if (fresh != null) {
                        map.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(fresh.latitude, fresh.longitude), 8.0))
                    }
                }
        }
    }
}
