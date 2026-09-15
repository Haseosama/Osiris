package com.osiris.app.globe

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * A standalone rotatable/zoomable 3D Earth (OpenGL ES, see [GlobeRenderer]) — MapLibre Native (the
 * engine behind [com.osiris.app.map.MapScreen]) has no globe/sphere projection at all, only flat
 * Mercator (confirmed by reading its source directly: the one "ProjectionMode" it has is for
 * axonometric/isometric rendering, unrelated). This is a separate screen rather than a mode the
 * main map switches into for exactly that reason — it isn't the same map engine underneath, so it
 * doesn't carry any of MapScreen's live layers (flights, ships, ...) over. Just the sphere itself
 * for now.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val globeView = remember { GlobeSurfaceView(context) }
    var isLoadingTexture by remember { mutableStateOf(true) }

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
        }
    }
}
