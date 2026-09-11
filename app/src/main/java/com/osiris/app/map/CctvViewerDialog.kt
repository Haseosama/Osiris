package com.osiris.app.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.osiris.app.data.model.CctvCamera
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val SNAPSHOT_REFRESH_MS = 3000L

/**
 * Shows the closest thing to "live" each camera actually offers: real MP4 playback (Media3)
 * for the handful of cameras with [CctvCamera.streamUrl] (e.g. Quebec 511), or an
 * auto-refreshing [CctvCamera.feedUrl] snapshot for the rest — most Osiris CCTV sources
 * (TfL, Caltrans, WSDOT...) only ever expose a periodically-updated JPEG, not real video, so
 * that refresh loop *is* the live view for them.
 */
@Composable
fun CctvViewerDialog(camera: CctvCamera, backendUrl: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 360.dp)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(camera.name ?: "Caméra", style = MaterialTheme.typography.titleMedium)
                val subtitle = listOfNotNull(camera.city, camera.source).joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))

                val streamUrl = camera.streamUrl?.let { resolveUrl(it, backendUrl) }
                val feedUrl = camera.feedUrl?.let { resolveUrl(it, backendUrl) }
                when {
                    streamUrl != null -> CctvVideoStream(streamUrl)
                    feedUrl != null -> CctvLiveSnapshot(feedUrl, camera.name)
                    else -> Text(
                        "Pas de flux disponible pour cette caméra",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss) { Text("Fermer") }
            }
        }
    }
}

@Composable
private fun CctvVideoStream(streamUrl: String) {
    val context = LocalContext.current
    val exoPlayer = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        factory = { PlayerView(context).apply { player = exoPlayer; useController = true } },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.small),
    )
}

@Composable
private fun CctvLiveSnapshot(feedUrl: String, cameraName: String?) {
    var snapshotUrl by remember(feedUrl) { mutableStateOf(cacheBust(feedUrl)) }
    LaunchedEffect(feedUrl) {
        while (isActive) {
            delay(SNAPSHOT_REFRESH_MS)
            snapshotUrl = cacheBust(feedUrl)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.error, modifier = Modifier.size(8.dp)) {}
        Spacer(Modifier.width(6.dp))
        Text(
            "EN DIRECT (image actualisée toutes les ${SNAPSHOT_REFRESH_MS / 1000}s)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(6.dp))
    SubcomposeAsyncImage(
        model = snapshotUrl,
        contentDescription = cameraName,
        modifier = Modifier.fillMaxWidth().height(220.dp),
        contentScale = ContentScale.Fit,
    ) {
        when (val state = painter.state) {
            is AsyncImagePainter.State.Loading -> CircularProgressIndicator()
            is AsyncImagePainter.State.Error -> Text(
                "Échec du chargement : ${state.result.throwable.message ?: state.result.throwable::class.simpleName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            else -> SubcomposeAsyncImageContent()
        }
    }
}

private fun cacheBust(url: String): String {
    val separator = if (url.contains('?')) '&' else '?'
    return "$url${separator}t=${System.currentTimeMillis()}"
}

/**
 * Some Osiris CCTV sources (e.g. SkylineWebcams) expose a [CctvCamera.feedUrl] that's actually
 * a relative path into the backend's own proxy (`/api/cctv/proxy?url=...`), not an absolute
 * URL — unlike direct sources (TfL, Quebec 511...). Prepend the configured backend base URL so
 * Coil/ExoPlayer get something resolvable instead of treating it as a local file path.
 */
private fun resolveUrl(url: String, backendUrl: String): String {
    if (!url.startsWith("/")) return url
    val trimmed = backendUrl.trim().trimEnd('/')
    if (trimmed.isEmpty()) return url
    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
    return "$withScheme$url"
}
