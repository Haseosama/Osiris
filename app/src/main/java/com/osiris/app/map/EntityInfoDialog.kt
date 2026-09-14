package com.osiris.app.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** Shared "tap a dot, see its facts" dialog for flights, earthquakes, fires, weather, conflicts,
 * maritime, satellites and cyberattacks — see [InfoDialogContent]. News/CCTV/OSINT keep their
 * own richer dialogs (player, image, link-out) since a flat fact list doesn't fit those.
 *
 * A route (flights only) gets a FlightRadar24-style header: big airport codes either side of a
 * progress bar, ETA underneath. Every fact below is grouped into bordered "tile" cards, two
 * facts per row, instead of the plain "label : value" text list this used to be. The whole card
 * borders in [InfoDialogContent.accentHex] when the entity has one (severity, or a satellite's
 * own category color) — the app's default gold otherwise.
 *
 * [onToggleFollow] is non-null only for a flight's dialog (see MapScreen) — renders a "Vue
 * cockpit" chase-camera toggle right under the route header, [isFollowing] driving its on/off
 * state. Every other entity type passes null and gets no such row. */
@Composable
fun EntityInfoDialog(
    content: InfoDialogContent,
    onDismiss: () -> Unit,
    isFollowing: Boolean = false,
    onToggleFollow: (() -> Unit)? = null,
) {
    val accent = content.accentHex?.let(::parseHexColorOrNull) ?: MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        ) {
            Column(modifier = Modifier.widthIn(max = 380.dp)) {
                // A rule in the entity's own color, right under the rounded top corners — same
                // idea as the reference web app's satellite card ("a rule in the satellite's own
                // colour, matching its marker and its track").
                Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(accent.copy(alpha = 0.7f)))

                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(content.title, style = MaterialTheme.typography.titleMedium)
                            content.subtitle?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = accent)
                            }
                        }
                        Row {
                            IconButton(
                                onClick = { context.startActivity(Intent.createChooser(buildShareIntent(content), null)) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = "Partager", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Fermer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    content.routeHeader?.let { RouteHeader(it, accent) }

                    onToggleFollow?.let { toggle -> CockpitViewToggle(isFollowing, toggle, accent) }

                    content.sections.forEach { section -> InfoSectionCard(section, accent) }

                    content.externalUrl?.let { url -> ExternalLinkButton(url, content.externalUrlLabel, accent, context) }
                }
            }
        }
    }
}

/** Flattens [InfoDialogContent] into a plain-text summary for the OS share sheet — same facts
 * the dialog shows, "Label : value" per line, grouped under each section's own header when it
 * has one. Any [InfoDialogContent.externalUrl] is appended last so the recipient has a source
 * to open, not just a text dump. */
private fun buildShareIntent(content: InfoDialogContent): Intent {
    val body = buildString {
        appendLine(content.title)
        content.subtitle?.let { appendLine(it) }
        content.sections.forEach { section ->
            if (section.rows.isEmpty()) return@forEach
            appendLine()
            section.title?.let { appendLine(it.uppercase()) }
            section.rows.forEach { row -> appendLine("${row.label} : ${row.value}") }
        }
        content.externalUrl?.let {
            appendLine()
            appendLine(it)
        }
    }.trim()
    return Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, content.title)
        putExtra(Intent.EXTRA_TEXT, body)
    }
}

/** `content.accentHex` is a plain `"#RRGGBB"`/`"#AARRGGBB"` string chosen from [EntityColors],
 * always a literal constant from that object — never user- or network-supplied — so a parse
 * failure here would only ever mean a typo in this codebase, not bad external data. */
private fun parseHexColorOrNull(hex: String): Color? =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()

@Composable
private fun RouteHeader(route: FlightRouteHeader, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(route.originCode ?: "—", style = MaterialTheme.typography.titleLarge, color = accent)
                route.originCity?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(route.destCode ?: "—", style = MaterialTheme.typography.titleLarge, color = accent)
                route.destCity?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(2.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (route.progress ?: 0f).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(accent, RoundedCornerShape(2.dp)),
            )
        }
        if (route.departureTime != null || route.arrivalTime != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    route.departureTime?.let { "Départ $it" } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    route.arrivalTime?.let { "Arrivée $it" } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoSectionCard(section: InfoSection, accent: Color) {
    if (section.rows.isEmpty()) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            section.title?.let {
                Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = accent)
            }
            section.rows.chunked(2).forEach { pair ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { row -> InfoTile(row, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Toggle row for the chase camera (see MapScreen's LaunchedEffect(followedFlightKey)) — same
 * pill shape as [ExternalLinkButton] so it reads as one family of action rows, but its own fill
 * lights up in [accent] while following so the dialog itself confirms it's live, not just the
 * map having quietly tilted somewhere behind it. */
@Composable
private fun CockpitViewToggle(isFollowing: Boolean, onToggle: () -> Unit, accent: Color) {
    Surface(
        color = if (isFollowing) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, if (isFollowing) accent.copy(alpha = 0.5f) else accent.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                (if (isFollowing) "Vue cockpit activée" else "Vue cockpit").uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = if (isFollowing) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                Icons.Filled.Videocam,
                contentDescription = null,
                tint = if (isFollowing) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** "OPEN SOURCE ↗" style link-out row — matches the pattern the reference web app puts on
 * almost every popup (USGS, NASA FIRMS, N2YO, FlightAware, MarineTraffic...). Opens in the
 * device's browser rather than embedding a WebView. */
@Composable
private fun ExternalLinkButton(url: String, label: String?, accent: Color, context: android.content.Context) {
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                (label ?: "Ouvrir la source").uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
            Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun InfoTile(row: InfoRow, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(row.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(row.value, style = MaterialTheme.typography.bodyMedium)
    }
}
