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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
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

private fun satelliteStyleBuilder(): Style.Builder =
    Style.Builder()
        .withSource(RasterSource("esri-imagery", TileSet("2.1.0", ESRI_IMAGERY_URL), 256))
        .withLayer(RasterLayer("esri-imagery-layer", "esri-imagery"))
        .withSource(RasterSource("esri-labels", TileSet("2.1.0", ESRI_LABELS_URL), 256))
        .withLayer(RasterLayer("esri-labels-layer", "esri-labels"))

private val DEFAULT_CAMERA = CameraPosition.Builder().target(LatLng(20.0, 0.0)).zoom(1.5).build()

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
    val selectedNewsFeed by viewModel.selectedNewsFeed.collectAsStateWithLifecycle()
    val selectedCctvCamera by viewModel.selectedCctvCamera.collectAsStateWithLifecycle()
    val selectedOsintPost by viewModel.selectedOsintPost.collectAsStateWithLifecycle()
    val selectedInfo by viewModel.selectedInfo.collectAsStateWithLifecycle()

    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var layersController by remember { mutableStateOf<LayersController?>(null) }
    var mapStyleMode by remember { mutableStateOf(MapStyleMode.SATELLITE) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

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
                                viewModel.selectInfo(marker.toInfoDialog())
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
                        add(MapSearchResult(sat.name, sat.category, sat.lat, sat.lng) { viewModel.selectInfo(sat.toInfoDialog()) })
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
        mapView.getMapAsync { map ->
            maplibreMap = map
            map.moveCamera(CameraUpdateFactory.newCameraPosition(DEFAULT_CAMERA))
        }
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
    LaunchedEffect(maplibreMap, hasLocationPermission) {
        val map = maplibreMap
        if (map != null && hasLocationPermission) centerOnUserLocation(context, map)
    }

    LaunchedEffect(layersController, flights) { layersController?.setFlights(flights) }
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

            if (handleInfoTap("flights-layer", flights) { it.toInfoDialog() }) return@addOnMapClickListener true
            if (handleInfoTap("earthquakes-layer", earthquakes) { it.toInfoDialog() }) return@addOnMapClickListener true
            if (handleInfoTap("fires-layer", fires) { it.toInfoDialog() }) return@addOnMapClickListener true
            if (handleInfoTap("weather-layer", weatherEvents) { it.toInfoDialog() }) return@addOnMapClickListener true
            if (handleInfoTap("conflicts-layer", conflictZones) { it.toInfoDialog() }) return@addOnMapClickListener true
            if (handleInfoTap("ports-layer", maritime.ports) { it.toInfoDialog() }) return@addOnMapClickListener true
            if (handleInfoTap("chokepoints-layer", maritime.chokepoints) { it.toInfoDialog() }) return@addOnMapClickListener true
            if (handleInfoTap("ships-layer", maritime.ships) { it.toInfoDialog() }) return@addOnMapClickListener true
            if (handleInfoTap("satellites-layer", satellites) { it.toInfoDialog() }) return@addOnMapClickListener true

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
                Text(
                    "OSIRIS",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row {
                    IconButton(onClick = { searchActive = !searchActive; if (!searchActive) searchQuery = "" }) {
                        Icon(
                            if (searchActive) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (searchActive) "Fermer la recherche" else "Rechercher",
                            tint = if (searchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    IconButton(
                        onClick = {
                            mapStyleMode = if (mapStyleMode == MapStyleMode.STREET) {
                                MapStyleMode.SATELLITE
                            } else {
                                MapStyleMode.STREET
                            }
                        },
                    ) {
                        Icon(
                            Icons.Filled.Layers,
                            contentDescription = "Vue satellite",
                            tint = if (mapStyleMode == MapStyleMode.SATELLITE) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            },
                        )
                    }
                    IconButton(onClick = onOpenRecon) {
                        Icon(
                            Icons.Filled.Build,
                            contentDescription = "RECON",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Réglages",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }

            if (searchActive) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small,
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
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
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
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
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
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = "Actualiser ma position")
        }
    }
}

@Composable
private fun LayerToggleChip(layer: MapLayer, enabled: Boolean, error: String?, onClick: () -> Unit) {
    BadgedBox(badge = { if (error != null) Badge(containerColor = MaterialTheme.colorScheme.error) }) {
        FilterChip(selected = enabled, onClick = onClick, label = { Text(layer.label) })
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
