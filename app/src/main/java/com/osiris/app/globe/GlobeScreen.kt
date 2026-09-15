package com.osiris.app.globe

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.osiris.app.map.CctvViewerDialog
import com.osiris.app.map.EntityColors
import com.osiris.app.map.EntityInfoDialog
import com.osiris.app.map.HudIconButton
import com.osiris.app.map.LayerToggleChip
import com.osiris.app.map.MapLayer
import com.osiris.app.map.MapViewModel
import com.osiris.app.map.SATELLITE_CATEGORIES
import com.osiris.app.map.toInfoDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One result row in the globe's own search overlay — mirrors [com.osiris.app.map.MapScreen]'s
 * private `MapSearchResult`, kept as a separate small copy rather than shared: the map's version
 * centers a MapLibre camera on select, this one flies the sphere's rotation there instead (see
 * [flyGlobeTo]), different enough underlying action that sharing one type would need a callback
 * indirection for no real benefit. */
private data class GlobeSearchResult(val label: String, val subtitle: String?, val lat: Double, val lng: Double, val onSelect: () -> Unit)

private val FLIGHTS_COLOR = floatArrayOf(1f, 0.7f, 0.2f, 1f)
private val EARTHQUAKES_COLOR = floatArrayOf(1f, 0.25f, 0.2f, 1f)
private val FIRES_COLOR = floatArrayOf(1f, 0.45f, 0f, 1f)
private val WEATHER_COLOR = floatArrayOf(1f, 0.85f, 0.15f, 1f)
private val CONFLICTS_COLOR = floatArrayOf(0.95f, 0.15f, 0.45f, 1f)
private val SHIPS_COLOR = floatArrayOf(0.2f, 0.75f, 1f, 1f)
private val SATELLITES_COLOR = floatArrayOf(0.65f, 0.35f, 1f, 1f)
private val CYBER_ATTACKS_COLOR = floatArrayOf(1f, 0.1f, 0.6f, 1f)
private val TRAFFIC_COLOR = floatArrayOf(1f, 0.6f, 0.3f, 1f)

/**
 * A standalone rotatable/zoomable 3D Earth (OpenGL ES, see [GlobeRenderer]) — MapLibre Native (the
 * engine behind [com.osiris.app.map.MapScreen]) has no globe/sphere projection at all, only flat
 * Mercator (confirmed by reading its source directly: the one "ProjectionMode" it has is for
 * axonometric/isometric rendering, unrelated). This is a separate screen rather than a mode the
 * main map switches into for exactly that reason — it isn't the same map engine underneath, so a
 * pinch-zoom in past [ZOOM_TO_FLAT_MAP_THRESHOLD] hands off to [onZoomedToFlatMap] instead of the
 * globe trying (and failing — it's one whole-Earth texture, nothing to zoom into) to get any more
 * detailed.
 *
 * [viewModel] is shared with [com.osiris.app.map.MapScreen] (see `OsirisNavGraph`, which scopes
 * both screens' `viewModel()` calls to the MAP back-stack entry) rather than a second instance of
 * its own — the same layer polling, layer toggles, search-selection dialogs, and RECON/Settings
 * navigation the flat map uses all just work here for free, and stay in sync switching back and
 * forth, instead of duplicating a second independent poll loop. Every layer whose data is a bare
 * (lat, lng) point (flights, earthquakes, fires, weather, conflict zones, ships, satellites, cyber
 * attacks, traffic) is drawn as a colored dot cloud (see [GlobeRenderer.setPoints]); CCTV/news/
 * OSINT are deliberately not plotted as dots (CCTV alone runs to ~17k entries — a wall of dots at
 * whole-Earth scale would just be visual noise — and news/OSINT don't really have a "this exact
 * point" meaning the way a camera or a ship does) but remain searchable/selectable exactly like on
 * the map, since search doesn't need a permanent dot to jump somewhere and open a result's dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobeScreen(
    onBack: () -> Unit,
    onZoomedToFlatMap: (lat: Double, lng: Double) -> Unit,
    onOpenRecon: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MapViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val globeView = remember { GlobeSurfaceView(context) }
    val coroutineScope = rememberCoroutineScope()
    var isLoadingTexture by remember { mutableStateOf(true) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val layerToggles by viewModel.layerToggles.collectAsStateWithLifecycle()
    val layerErrors by viewModel.layerErrors.collectAsStateWithLifecycle()
    val flights by viewModel.flights.collectAsStateWithLifecycle()
    val earthquakes by viewModel.earthquakes.collectAsStateWithLifecycle()
    val fires by viewModel.fires.collectAsStateWithLifecycle()
    val weatherEvents by viewModel.weatherEvents.collectAsStateWithLifecycle()
    val conflictZones by viewModel.conflictZones.collectAsStateWithLifecycle()
    val maritime by viewModel.maritime.collectAsStateWithLifecycle()
    val satellites by viewModel.satellites.collectAsStateWithLifecycle()
    val cyberAttacks by viewModel.cyberAttacks.collectAsStateWithLifecycle()
    val trafficIncidents by viewModel.trafficIncidents.collectAsStateWithLifecycle()
    val cctvCameras by viewModel.cctvCameras.collectAsStateWithLifecycle()
    val disabledSatelliteCategories by viewModel.disabledSatelliteCategories.collectAsStateWithLifecycle()
    val selectedInfo by viewModel.selectedInfo.collectAsStateWithLifecycle()
    val selectedCctvCamera by viewModel.selectedCctvCamera.collectAsStateWithLifecycle()

    // Same capped-per-category shape as MapScreen's own search — see its own doc for why (CCTV
    // alone runs to ~17k entries and would otherwise crowd out every other category).
    val searchResults: List<GlobeSearchResult> = remember(searchQuery, flights, maritime, satellites, cctvCameras) {
        val query = searchQuery.trim()
        if (query.length < 2) {
            emptyList()
        } else {
            val flightMatches = flights.mapNotNull { marker ->
                val callsign = marker.flight.callsign?.trim().orEmpty()
                if (!callsign.contains(query, ignoreCase = true)) return@mapNotNull null
                GlobeSearchResult(callsign, EntityColors.flightCategoryLabel(marker.category.name), marker.flight.lat, marker.flight.lng) {
                    viewModel.selectFlight(marker)
                }
            }.take(8)
            val portMatches = maritime.ports.mapNotNull { port ->
                if (!port.name.contains(query, ignoreCase = true)) return@mapNotNull null
                GlobeSearchResult(port.name, EntityColors.portTypeLabel(port.type), port.lat, port.lng) { viewModel.selectInfo(port.toInfoDialog()) }
            }.take(8)
            val shipMatches = maritime.ships.mapNotNull { ship ->
                val name = ship.name.orEmpty()
                if (!name.contains(query, ignoreCase = true)) return@mapNotNull null
                GlobeSearchResult(name, EntityColors.shipTypeLabel(ship.type), ship.lat, ship.lng) { viewModel.selectShip(ship) }
            }.take(8)
            val satelliteMatches = satellites.mapNotNull { sat ->
                if (!sat.name.contains(query, ignoreCase = true)) return@mapNotNull null
                GlobeSearchResult(sat.name, EntityColors.satelliteCategoryLabel(sat.category), sat.lat, sat.lng) { viewModel.selectSatellite(sat) }
            }.take(8)
            val cctvMatches = cctvCameras.mapNotNull { camera ->
                val name = camera.name.orEmpty()
                val city = camera.city.orEmpty()
                if (!name.contains(query, ignoreCase = true) && !city.contains(query, ignoreCase = true)) return@mapNotNull null
                GlobeSearchResult(
                    name.ifBlank { "Caméra" },
                    listOfNotNull(camera.city, camera.source).joinToString(" · ").ifBlank { null },
                    camera.lat,
                    camera.lng,
                ) { viewModel.selectCctvCamera(camera) }
            }.take(8)
            (flightMatches + portMatches + shipMatches + satelliteMatches + cctvMatches).take(30)
        }
    }

    fun selectSearchResult(result: GlobeSearchResult) {
        coroutineScope.launch { flyGlobeTo(globeView.renderer, result.lat, result.lng) }
        result.onSelect()
        searchActive = false
        searchQuery = ""
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

    DisposableEffect(globeView, onZoomedToFlatMap) {
        globeView.onZoomedIn = onZoomedToFlatMap
        onDispose { globeView.onZoomedIn = null }
    }

    DisposableEffect(lifecycleOwner, globeView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> globeView.onResume()
                Lifecycle.Event.ON_PAUSE -> globeView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // GLSurfaceView stops its render thread automatically on detach-from-window, but pausing
        // it explicitly here too means the GL thread winds down the moment this screen is popped
        // off the back stack rather than whenever the detach callback happens to fire.
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            globeView.onPause()
        }
    }

    LaunchedEffect(Unit) {
        val bitmap = EarthTextureLoader.load(context)
        if (bitmap != null) globeView.renderer.pendingBitmap = bitmap
        isLoadingTexture = false
    }

    // One effect per point-representable layer — same shape as MapScreen's own
    // `LaunchedEffect(layersController, xxx) { layersController?.setXxx(xxx) }` wiring, just
    // targeting GlobeRenderer's generic setPoints() instead of a MapLibre source. An empty list
    // (layer toggled off) clears that layer's dots — see setPoints' own doc.
    LaunchedEffect(globeView, layerToggles, flights) {
        val points = if (layerToggles[MapLayer.FLIGHTS] == true) flights.map { it.flight.lat to it.flight.lng } else emptyList()
        globeView.renderer.setPoints("flights", points, FLIGHTS_COLOR)
    }
    LaunchedEffect(globeView, layerToggles, earthquakes) {
        val points = if (layerToggles[MapLayer.EARTHQUAKES] == true) earthquakes.map { it.lat to it.lng } else emptyList()
        globeView.renderer.setPoints("earthquakes", points, EARTHQUAKES_COLOR)
    }
    LaunchedEffect(globeView, layerToggles, fires) {
        val points = if (layerToggles[MapLayer.FIRES] == true) fires.map { it.lat to it.lng } else emptyList()
        globeView.renderer.setPoints("fires", points, FIRES_COLOR)
    }
    LaunchedEffect(globeView, layerToggles, weatherEvents) {
        val points = if (layerToggles[MapLayer.WEATHER] == true) weatherEvents.map { it.lat to it.lng } else emptyList()
        globeView.renderer.setPoints("weather", points, WEATHER_COLOR)
    }
    LaunchedEffect(globeView, layerToggles, conflictZones) {
        val points = if (layerToggles[MapLayer.CONFLICTS] == true) conflictZones.map { it.lat to it.lng } else emptyList()
        globeView.renderer.setPoints("conflicts", points, CONFLICTS_COLOR)
    }
    LaunchedEffect(globeView, layerToggles, maritime) {
        // Matches the flat map's own MARITIME toggle scope (ships + ports + chokepoints all under
        // one layer, see LayersController.setMaritime) rather than ships alone.
        val points = if (layerToggles[MapLayer.MARITIME] == true) {
            maritime.ships.map { it.lat to it.lng } +
                maritime.ports.map { it.lat to it.lng } +
                maritime.chokepoints.map { it.lat to it.lng }
        } else {
            emptyList()
        }
        globeView.renderer.setPoints("maritime", points, SHIPS_COLOR)
    }
    LaunchedEffect(globeView, layerToggles, satellites) {
        val points = if (layerToggles[MapLayer.SATELLITES] == true) satellites.map { it.lat to it.lng } else emptyList()
        globeView.renderer.setPoints("satellites", points, SATELLITES_COLOR)
    }
    LaunchedEffect(globeView, layerToggles, cyberAttacks) {
        val points = if (layerToggles[MapLayer.CYBER_ATTACKS] == true) cyberAttacks.map { it.dstLat to it.dstLng } else emptyList()
        globeView.renderer.setPoints("cyberattacks", points, CYBER_ATTACKS_COLOR)
    }
    LaunchedEffect(globeView, layerToggles, trafficIncidents) {
        val points = if (layerToggles[MapLayer.TRAFFIC] == true) trafficIncidents.map { it.lat to it.lng } else emptyList()
        globeView.renderer.setPoints("traffic", points, TRAFFIC_COLOR)
    }

    selectedCctvCamera?.let { camera ->
        CctvViewerDialog(camera = camera, backendUrl = "", onDismiss = { viewModel.selectCctvCamera(null) })
    }
    selectedInfo?.let { info ->
        EntityInfoDialog(content = info, onDismiss = { viewModel.selectInfo(null) })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Globe 3D") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                ),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(factory = { globeView }, modifier = Modifier.fillMaxSize())
            if (isLoadingTexture) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).padding(16.dp))
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 12.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    HudIconButton(
                        icon = if (searchActive) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = if (searchActive) "Fermer la recherche" else "Rechercher",
                        active = searchActive,
                        onClick = { searchActive = !searchActive; if (!searchActive) searchQuery = "" },
                    )
                    HudIconButton(icon = Icons.Filled.Build, contentDescription = "RECON", onClick = onOpenRecon)
                    HudIconButton(icon = Icons.Filled.Settings, contentDescription = "Réglages", onClick = onOpenSettings)
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
                    if (hasLocationPermission) {
                        locateAndCenterGlobe(context, globeView.renderer, coroutineScope)
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
                Icon(Icons.Filled.MyLocation, contentDescription = "Centrer le globe sur ma position")
            }
        }
    }
}

/** How close [flyGlobeTo] zooms in by default — noticeably closer than the resting `zoom = 1f`
 * (see [GlobeRenderer.zoom]) so a location/search jump actually reads as "zoom to this point," not
 * just a rotation, while staying comfortably above [ZOOM_TO_FLAT_MAP_THRESHOLD] (0.62f) so landing
 * here never itself triggers the globe-to-flat-map handoff — that stays a deliberate pinch gesture,
 * not a side effect of tapping a search result or the location button. */
private const val FLY_TO_ZOOM = 0.75f

/** Smoothly rotates and zooms [renderer] so [GlobeRenderer.centerLatLng] ends up at ([targetLat],
 * [targetLng]), used by both the location FAB and search-result selection. Plain per-frame field
 * writes (same `@Volatile` fields [GlobeSurfaceView]'s touch handling writes directly) rather than
 * a Compose `Animatable`: this is a fire-and-forget coroutine kicked off from a click callback, not
 * something composition needs to observe. [deltaY] takes the shorter way around the 360° wrap
 * (e.g. animating from -170° to 170° goes through 180°, a 20° turn, not the 340° long way) — [deltaX]
 * needs no such wrap since [GlobeRenderer.rotationX] never leaves [-90, 90]. */
private suspend fun flyGlobeTo(renderer: GlobeRenderer, targetLat: Double, targetLng: Double, targetZoom: Float = FLY_TO_ZOOM) {
    val (targetX, targetY) = renderer.rotationFor(targetLat, targetLng)
    val startX = renderer.rotationX
    val startY = renderer.rotationY
    val startZoom = renderer.zoom
    var deltaY = (targetY - startY) % 360f
    if (deltaY > 180f) deltaY -= 360f
    if (deltaY < -180f) deltaY += 360f
    val deltaX = targetX - startX
    val deltaZoom = targetZoom - startZoom
    val steps = 24
    repeat(steps) { i ->
        val t = (i + 1) / steps.toFloat()
        renderer.rotationX = startX + deltaX * t
        renderer.rotationY = startY + deltaY * t
        renderer.zoom = startZoom + deltaZoom * t
        delay(16L)
    }
}

/** Same cached-fix-first, fresh-request-fallback shape as MapScreen's own
 * `centerOnUserLocation`, just flying the globe's rotation to the result (see [flyGlobeTo])
 * instead of easing a MapLibre camera. */
@SuppressLint("MissingPermission")
private fun locateAndCenterGlobe(context: android.content.Context, renderer: GlobeRenderer, scope: kotlinx.coroutines.CoroutineScope) {
    val client = LocationServices.getFusedLocationProviderClient(context)
    client.lastLocation.addOnSuccessListener { location ->
        if (location != null) {
            scope.launch { flyGlobeTo(renderer, location.latitude, location.longitude) }
        } else {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { fresh ->
                    if (fresh != null) scope.launch { flyGlobeTo(renderer, fresh.latitude, fresh.longitude) }
                }
        }
    }
}
