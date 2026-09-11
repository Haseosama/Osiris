package com.osiris.app.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.osiris.app.data.model.CctvCamera

/** Shows a static JPEG snapshot ([CctvCamera.feedUrl]) or, for MP4-only cameras, a link out. */
@Composable
fun CctvViewerDialog(camera: CctvCamera, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(16.dp),
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

                val feedUrl = camera.feedUrl
                val streamUrl = camera.streamUrl
                when {
                    feedUrl != null -> AsyncImage(
                        model = feedUrl,
                        contentDescription = camera.name,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                        contentScale = ContentScale.Fit,
                    )
                    streamUrl != null -> Button(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(streamUrl))) },
                    ) {
                        Text("Ouvrir le flux vidéo")
                    }
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
