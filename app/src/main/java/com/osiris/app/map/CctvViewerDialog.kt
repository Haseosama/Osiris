package com.osiris.app.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.osiris.app.data.model.CctvCamera
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val SNAPSHOT_REFRESH_MS = 3000L

/**
 * Shows the closest thing to "live" each camera actually offers: real MP4 playback (Media3)
 * for the handful of cameras with [CctvCamera.streamUrl] (e.g. Quebec 511, APRR/AREA), or an
 * auto-refreshing [CctvCamera.feedUrl] snapshot for the rest — most Osiris CCTV sources
 * (TfL, Caltrans, WSDOT...) only ever expose a periodically-updated JPEG, not real video, so
 * that refresh loop *is* the live view for them.
 *
 * Some video sources (APRR/AREA in particular) are a short, regularly re-recorded clip rather
 * than a true continuous stream — without looping it plays once and then just freezes on its
 * last frame, reading as "the feed stopped". Tapping the video/snapshot (or the link below it,
 * when [CctvCamera.externalUrl] is known) opens the camera's own page instead, so a frozen or
 * broken feed isn't a dead end.
 */
@Composable
fun CctvViewerDialog(camera: CctvCamera, backendUrl: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val openExternal: () -> Unit = {
        camera.externalUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
    }
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
                val hasExternalUrl = camera.externalUrl != null
                when {
                    streamUrl != null -> CctvVideoStream(streamUrl, onClick = openExternal.takeIf { hasExternalUrl })
                    feedUrl != null -> CctvLiveSnapshot(feedUrl, camera.name, onClick = openExternal.takeIf { hasExternalUrl })
                    else -> Text(
                        "Pas de flux disponible pour cette caméra",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (hasExternalUrl) {
                    Spacer(Modifier.height(10.dp))
                    CctvExternalLinkButton(onClick = openExternal)
                }

                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss) { Text("Fermer") }
            }
        }
    }
}

@Composable
private fun CctvVideoStream(streamUrl: String, onClick: (() -> Unit)?) {
    val context = LocalContext.current
    val exoPlayer = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            // APRR/AREA's clip is short and only re-recorded server-side every so often —
            // looping it locally keeps the view "moving" between server-side refreshes instead
            // of freezing on the last frame once Media3 reaches the end.
            repeatMode = Player.REPEAT_MODE_ONE
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
            .clip(MaterialTheme.shapes.small)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    )
}

@Composable
private fun CctvLiveSnapshot(feedUrl: String, cameraName: String?, onClick: (() -> Unit)?) {
    val context = LocalContext.current
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
        model = remember(snapshotUrl) { snapshotImageRequest(context, snapshotUrl) },
        contentDescription = cameraName,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
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

@Composable
private fun CctvExternalLinkButton(onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "VOIR SUR LE SITE DE LA CAMÉRA",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun cacheBust(url: String): String {
    val separator = if (url.contains('?')) '&' else '?'
    return "$url${separator}t=${System.currentTimeMillis()}"
}

/** Hosts that answer *worse* with a Referer than without one — Taiwan's THB encoders emit a
 * malformed response header whenever the request carries one, which breaks OkHttp's parser
 * entirely (see `osiris-backend/src/app/api/cctv/proxy/route.ts`'s own NO_REFERER_HOSTS). */
private val NO_REFERER_HOSTS = setOf("thb.gov.tw")

private const val SNAPSHOT_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

/**
 * A handful of CCTV CDNs (SkylineWebcams, Rijkswaterstaat's `stream.inmoves.nl`, Spain's
 * `etraffic.dgt.es`...) answer a bare hotlinked request with a 401/empty response — they only
 * serve the frame when the request carries a `Referer` naming their own origin, a check the
 * backend used to satisfy through its image proxy (`/api/cctv/proxy`). On-device there's no such
 * proxy hop: Coil/OkHttp can just add the same header directly to the request itself, so this
 * builds a plain URL string into a request carrying it — self-referencing (`Referer` = the same
 * host being requested) rather than the app's own origin, which is what actually defeats this
 * particular check; a URL whose host can't be parsed falls back to no special headers at all.
 */
private fun snapshotImageRequest(context: android.content.Context, url: String): ImageRequest {
    val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase()
    val builder = ImageRequest.Builder(context).data(url)
    if (host == null) return builder.build()
    builder.addHeader("User-Agent", SNAPSHOT_USER_AGENT)
    builder.addHeader("Accept", "image/*,*/*")
    if (NO_REFERER_HOSTS.none { host == it || host.endsWith(".$it") }) {
        builder.addHeader("Referer", "https://$host/")
    }
    return builder.build()
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
