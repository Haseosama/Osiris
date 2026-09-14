package com.osiris.app.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.pow
import kotlin.random.Random

/** The map camera state this frame, as read straight off `MapLibreMap.cameraPosition` —
 * everything the HUD prints is derived from this, no separate polling of its own. */
data class HudCameraSnapshot(val lat: Double, val lng: Double, val zoom: Double, val bearing: Double)

/** The source HUD's own "reskin reality" concept — a handful of sensor-look presets, each with
 * its own accent color, that the same corner-bracket layout re-renders under. Values copied
 * straight from its `HUD_COLORS` map in `src/hud.js` (main-color alpha only; glow derived the
 * same way this file already derives it elsewhere, not re-specified per theme there). */
enum class HudTheme(val label: String, val accent: Color) {
    DEFAULT("RECON", Color(0xFF00E5FF)),
    SURVEILLANCE("SURVEILLANCE", Color(0xFF33FF33)),
    THERMAL("THERMAL", Color(0xFFFFFFFF)),
    RETRO("RETRO", Color(0xFFFFAA00)),
}

private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

/**
 * "Reconnaissance HUD" skin over the map — corner-bracket telemetry readout, blinking REC
 * indicator, live UTC clock. Visually modelled on the intelligence-HUD overlay in
 * [God's Eye View](https://github.com/bilawalsidhu/gods-eye-view) (an open-source CesiumJS globe
 * the user pointed at directly, `src/hud.js`/`style.css`) — that project renders its globe in
 * WebGL/Cesium, a different engine from MapLibre Native, so this isn't a port of its code, just
 * its visual language: corner brackets, monospace glow, classification-banner flavor text,
 * blinking REC dot, coordinate/zoom readouts pinned to the corners. No MGRS conversion (would
 * need a geodesy library for one cosmetic line) and no AI-written summary line (that reads from
 * an OpenAI-backed endpoint upstream that doesn't exist here) — everything shown is a real,
 * locally-derived value: the current camera center and zoom.
 *
 * Purely a cosmetic overlay: it reads the camera position passed in and draws over the map, and
 * only the small theme-picker row intercepts touch — everything else ignores clicks so the map
 * underneath still pans/zooms normally.
 */
@Composable
fun IntelHudOverlay(
    snapshot: HudCameraSnapshot?,
    theme: HudTheme,
    onThemeChange: (HudTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = theme.accent
    val glow = accent.copy(alpha = 0.6f)

    // Session-consistent pseudo-identifiers, generated once — flavor only, not derived from
    // anything real (mirrors the source HUD's own KH11-####/OPS-#### convention).
    val missionId = remember { "OSR-${4000 + Random.nextInt(200)}" }
    val sensorId = remember { "OPS-${4100 + Random.nextInt(100)}" }

    var recOn by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(800)
            recOn = !recOn
        }
    }
    var timestamp by remember { mutableStateOf(TIMESTAMP_FORMAT.format(Instant.now())) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000)
            timestamp = TIMESTAMP_FORMAT.format(Instant.now())
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        HudCornerBlock(
            alignment = Alignment.TopStart,
            bracket = "┌",
            bracketAtStart = true,
            accent = accent,
            glow = glow,
            content = {
                HudLine("OSIRIS // LIVE-FEED // NOFORN", accent, glow, weight = FontWeight.SemiBold, size = 11.sp)
                HudLine("$missionId  $sensorId", accent, glow, alpha = 0.8f)
                HudLine("MODE: ${theme.label}", accent, glow, weight = FontWeight.Bold, size = 15.sp)
            },
        )
        HudCornerBlock(
            alignment = Alignment.TopEnd,
            bracket = "┐",
            bracketAtStart = false,
            accent = accent,
            glow = glow,
            horizontalAlignment = Alignment.End,
            content = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    HudLine(if (recOn) "●" else " ", Color(0xFFFF3333), Color(0xFFFF3333).copy(alpha = 0.8f))
                    HudLine("REC  $timestamp", accent, glow)
                }
            },
        )
        HudCornerBlock(
            alignment = Alignment.BottomStart,
            bracket = "└",
            bracketAtStart = true,
            accent = accent,
            glow = glow,
            content = {
                HudLine(snapshot?.let { formatLatLon(it.lat, it.lng) } ?: "LAT: --  LON: --", accent, glow)
                HudLine(snapshot?.let { "HDG: ${it.bearing.toInt().mod(360)}°" } ?: "HDG: --", accent, glow, alpha = 0.7f)
            },
        )
        HudCornerBlock(
            alignment = Alignment.BottomEnd,
            bracket = "┘",
            bracketAtStart = false,
            accent = accent,
            glow = glow,
            horizontalAlignment = Alignment.End,
            content = {
                HudLine(snapshot?.let { "ZOOM: %.1f".format(it.zoom) } ?: "ZOOM: --", accent, glow)
                HudLine(snapshot?.let { "ALT: ~${formatAlt(it.zoom)}" } ?: "ALT: --", accent, glow, alpha = 0.7f)
            },
        )

        HudEdgeLabel("OSIRIS INTEL FEED", accent, glow, modifier = Modifier.align(Alignment.CenterStart).rotate(-90f))
        HudEdgeLabel("SRC: ESRI/OSM", accent, glow, modifier = Modifier.align(Alignment.CenterEnd).rotate(90f))

        HudThemePicker(
            current = theme,
            onSelect = onThemeChange,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp),
        )
    }
}

/** The "reskin reality" picker — four dots, one per [HudTheme], the selected one ringed. Sits
 * low-center where nothing else is drawn; the only touch-sensitive part of the whole overlay. */
@Composable
private fun HudThemePicker(current: HudTheme, onSelect: (HudTheme) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        HudTheme.entries.forEach { theme ->
            val selected = theme == current
            Box(
                modifier = Modifier
                    .size(if (selected) 16.dp else 12.dp)
                    .clickable { onSelect(theme) }
                    .background(theme.accent.copy(alpha = if (selected) 0.9f else 0.5f), CircleShape)
                    .then(
                        if (selected) {
                            Modifier.border(BorderStroke(1.dp, theme.accent), CircleShape)
                        } else {
                            Modifier
                        }
                    ),
            ) {}
        }
    }
}

@Composable
private fun BoxScope.HudCornerBlock(
    alignment: Alignment,
    bracket: String,
    bracketAtStart: Boolean,
    accent: Color,
    glow: Color,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .align(alignment)
            .padding(horizontal = 20.dp, vertical = 130.dp)
            .wrapContentSize(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (bracketAtStart) HudBracket(bracket, accent, glow)
        Column(horizontalAlignment = horizontalAlignment) { content() }
        if (!bracketAtStart) HudBracket(bracket, accent, glow)
    }
}

@Composable
private fun HudBracket(glyph: String, accent: Color, glow: Color) {
    Text(
        glyph,
        style = TextStyle(
            color = accent.copy(alpha = 0.6f),
            fontSize = 26.sp,
            fontFamily = FontFamily.Monospace,
            shadow = Shadow(glow, blurRadius = 8f),
        ),
    )
}

@Composable
private fun HudLine(
    text: String,
    accent: Color,
    glow: Color,
    alpha: Float = 1f,
    weight: FontWeight = FontWeight.Normal,
    size: TextUnit = 12.sp,
) {
    Text(
        text,
        style = TextStyle(
            color = accent.copy(alpha = accent.alpha * alpha),
            fontSize = size,
            fontFamily = FontFamily.Monospace,
            fontWeight = weight,
            letterSpacing = 1.2.sp,
            shadow = Shadow(glow, blurRadius = 6f),
        ),
    )
}

@Composable
private fun HudEdgeLabel(text: String, accent: Color, glow: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color.Transparent)) {
        HudLine(text, accent, glow, alpha = 0.5f, size = 10.sp)
    }
}

private fun formatLatLon(lat: Double, lng: Double): String {
    val latHemi = if (lat >= 0) "N" else "S"
    val lngHemi = if (lng >= 0) "E" else "W"
    return "%.4f°%s  %.4f°%s".format(kotlin.math.abs(lat), latHemi, kotlin.math.abs(lng), lngHemi)
}

/** A cosmetic "sensor altitude" line, not a real one — derived from the standard web-Mercator
 * zoom→ground-resolution relationship (halves every zoom level) purely so the readout moves the
 * way a real one would as you zoom, not an actual camera/satellite altitude estimate. */
private fun formatAlt(zoom: Double): String {
    val metersPerPixelAtEquator = 156543.03392 * 2.0.pow(-zoom)
    val pseudoAltKm = (metersPerPixelAtEquator * 500) / 1000
    return when {
        pseudoAltKm >= 1000 -> "${(pseudoAltKm / 1000).toInt()}K KM"
        pseudoAltKm >= 1 -> "${pseudoAltKm.toInt()} KM"
        else -> "${(pseudoAltKm * 1000).toInt()} M"
    }
}
