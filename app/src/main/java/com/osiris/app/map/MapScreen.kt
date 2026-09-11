package com.osiris.app.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private const val DEFAULT_STYLE_URL = "https://demotiles.maplibre.org/style.json"
private val DEFAULT_CAMERA = CameraPosition.Builder().target(LatLng(20.0, 0.0)).zoom(1.5).build()

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
    val cctvCameras by viewModel.cctvCameras.collectAsStateWithLifecycle()
    val osintPosts by viewModel.osintPosts.collectAsStateWithLifecycle()
    val selectedNewsFeed by viewModel.selectedNewsFeed.collectAsStateWithLifecycle()
    val selectedCctvCamera by viewModel.selectedCctvCamera.collectAsStateWithLifecycle()
    val selectedOsintPost by viewModel.selectedOsintPost.collectAsStateWithLifecycle()

    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var layersController by remember { mutableStateOf<LayersController?>(null) }

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
            map.setStyle(Style.Builder().fromUri(DEFAULT_STYLE_URL)) { style ->
                layersController = LayersController(style)
            }
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

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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
        CctvViewerDialog(camera = camera, onDismiss = { viewModel.selectCctvCamera(null) })
    }
    selectedOsintPost?.let { post ->
        OsintPostDialog(post = post, onDismiss = { viewModel.selectOsintPost(null) })
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
    }
}

@Composable
private fun LayerToggleChip(layer: MapLayer, enabled: Boolean, error: String?, onClick: () -> Unit) {
    BadgedBox(badge = { if (error != null) Badge(containerColor = MaterialTheme.colorScheme.error) }) {
        FilterChip(selected = enabled, onClick = onClick, label = { Text(layer.label) })
    }
}

@SuppressLint("MissingPermission")
private fun centerOnUserLocation(context: Context, map: MapLibreMap) {
    LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { location ->
        if (location != null) {
            map.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 8.0))
        }
    }
}
