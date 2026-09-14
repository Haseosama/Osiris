package com.osiris.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.osiris.app.MainActivity
import com.osiris.app.data.BackendPreferences
import com.osiris.app.data.repository.ConflictsRepository
import com.osiris.app.data.repository.CyberAttacksRepository
import com.osiris.app.data.repository.EarthquakesRepository
import com.osiris.app.data.repository.FlightsRepository
import com.osiris.app.data.repository.TrafficRepository
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Keys for the small per-instance state Glance already persists for every placed widget — no
 * separate DataStore of our own needed, [WidgetUpdater]/[RefreshWidgetAction] just write into
 * that existing store. */
object WidgetKeys {
    val FLIGHTS_COUNT = intPreferencesKey("flights_count")
    val CYBER_COUNT = intPreferencesKey("cyber_count")
    val CONFLICTS_COUNT = intPreferencesKey("conflicts_count")
    val EARTHQUAKES_COUNT = intPreferencesKey("earthquakes_count")
    val TRAFFIC_COUNT = intPreferencesKey("traffic_count")
    val UPDATED_AT = stringPreferencesKey("updated_at")
}

private val CONFLICT_ALERT_LEVELS = setOf("high", "war")

/** Thresholds shared with [com.osiris.app.map.MapViewModel] so the widget's counts mean the same
 * thing everywhere: a magnitude-4.5+ quake ("ressenti" territory, well below the 6.0 push-alert
 * bar) and a modéré-or-worse (TomTom scale 2+) traffic incident — not every minor jam. */
const val WIDGET_EARTHQUAKE_MIN_MAGNITUDE = 4.5
const val WIDGET_TRAFFIC_MIN_MAGNITUDE = 2

private val COLOR_GOLD = Color(0xFFD4AF37)
private val COLOR_BG = Color(0xFF06060C)
private val COLOR_CYAN = Color(0xFF00E5FF)
private val COLOR_ALERT_RED = Color(0xFFFF4D5E)
private val COLOR_CONFLICT = Color(0xFFFF8A3D)
private val COLOR_QUAKE = Color(0xFFFFC64B)
private val COLOR_TRAFFIC = Color(0xFFFF6E6E)
private val COLOR_NEUTRAL_VALUE = Color(0xFF6B7280)
private val COLOR_LABEL = Color(0xFFB8B4AA)
private val COLOR_MUTED = Color(0xFF6E6A61)
private val COLOR_LIVE_DOT = Color(0xFF3DDC84)

/**
 * A glanceable status card — vols/cyberattaques/conflits/séismes/trafic currently tracked, last
 * refreshed at. Normally just a display of whatever [WidgetUpdater] last wrote while the app was
 * open and polling — the ↻ button is the exception: it runs [RefreshWidgetAction], a one-off
 * direct backend fetch, so the widget can still be nudged even with the app closed, without
 * needing a background worker to duplicate the app's own polling loop.
 *
 * The thin gold frame (an outer Box painted gold, holding an inner Box inset by 1.5dp painted the
 * real background) is the cheapest way to get a hairline border out of Glance, which has no
 * border modifier of its own — it mirrors the HUD-styled frame already used around cards in the
 * phone-sized app UI.
 */
class OsirisWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val flights = prefs[WidgetKeys.FLIGHTS_COUNT] ?: 0
            val cyber = prefs[WidgetKeys.CYBER_COUNT] ?: 0
            val conflicts = prefs[WidgetKeys.CONFLICTS_COUNT] ?: 0
            val quakes = prefs[WidgetKeys.EARTHQUAKES_COUNT] ?: 0
            val traffic = prefs[WidgetKeys.TRAFFIC_COUNT] ?: 0
            val updatedAt = prefs[WidgetKeys.UPDATED_AT]

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(COLOR_GOLD)
                    .cornerRadius(20.dp)
                    .padding(1.5.dp),
            ) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(COLOR_BG)
                        .cornerRadius(19.dp)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                ) {
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                "OSIRIS",
                                style = TextStyle(color = ColorProvider(COLOR_GOLD), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                            )
                            Text(
                                "Dashboard OSINT",
                                style = TextStyle(color = ColorProvider(COLOR_MUTED), fontSize = 9.sp),
                            )
                        }
                        Text(
                            "●",
                            style = TextStyle(color = ColorProvider(COLOR_LIVE_DOT), fontSize = 10.sp),
                            modifier = GlanceModifier.padding(end = 8.dp, top = 2.dp),
                        )
                        Text(
                            "↻",
                            style = TextStyle(color = ColorProvider(COLOR_CYAN), fontSize = 17.sp, fontWeight = FontWeight.Bold),
                            modifier = GlanceModifier.clickable(actionRunCallback<RefreshWidgetAction>()),
                        )
                    }
                    Spacer(GlanceModifier.height(10.dp))
                    WidgetStatRow("✈", "Vols", flights, COLOR_CYAN)
                    WidgetStatRow("⚠", "Cyberattaques", cyber, COLOR_ALERT_RED)
                    WidgetStatRow("⚔", "Conflits actifs", conflicts, COLOR_CONFLICT)
                    WidgetStatRow("〜", "Séismes M4.5+", quakes, COLOR_QUAKE)
                    WidgetStatRow("🚧", "Trafic perturbé", traffic, COLOR_TRAFFIC)
                    Spacer(GlanceModifier.height(8.dp))
                    Text(
                        if (updatedAt != null) "Mis à jour à $updatedAt" else "En attente de données…",
                        style = TextStyle(color = ColorProvider(COLOR_MUTED), fontSize = 9.sp),
                    )
                }
            }
        }
    }
}

/** One stat line: a small emoji glyph standing in for a per-category icon, the label, and the
 * count — dimmed to [COLOR_NEUTRAL_VALUE] at zero (nothing to see) and lit up in [accent] the
 * moment there's something active, so the eye is drawn straight to whatever actually needs it. */
@Composable
private fun WidgetStatRow(glyph: String, label: String, count: Int, accent: Color) {
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            glyph,
            style = TextStyle(color = ColorProvider(accent), fontSize = 12.sp),
            modifier = GlanceModifier.width(20.dp),
        )
        Text(
            label,
            style = TextStyle(color = ColorProvider(COLOR_LABEL), fontSize = 12.sp),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            count.toString(),
            style = TextStyle(
                color = ColorProvider(if (count > 0) accent else COLOR_NEUTRAL_VALUE),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/** Writes whichever counts are non-null into [id]'s own Glance state, leaving any `null` one
 * (a fetch that failed, or was never asked for) at its last known value rather than blanking it
 * to 0 — a stale-but-real number beats a fresh, misleading zero. */
private suspend fun writeWidgetCounts(
    context: Context,
    id: GlanceId,
    flightsCount: Int?,
    cyberCount: Int?,
    conflictsCount: Int?,
    earthquakesCount: Int?,
    trafficCount: Int?,
) {
    updateAppWidgetState(context, id) { prefs: MutablePreferences ->
        flightsCount?.let { prefs[WidgetKeys.FLIGHTS_COUNT] = it }
        cyberCount?.let { prefs[WidgetKeys.CYBER_COUNT] = it }
        conflictsCount?.let { prefs[WidgetKeys.CONFLICTS_COUNT] = it }
        earthquakesCount?.let { prefs[WidgetKeys.EARTHQUAKES_COUNT] = it }
        trafficCount?.let { prefs[WidgetKeys.TRAFFIC_COUNT] = it }
        prefs[WidgetKeys.UPDATED_AT] = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    }
}

/** Called from [com.osiris.app.map.MapViewModel] whenever flights/cyberattacks/conflicts/séismes/
 * trafic finish a poll — pushes the latest counts into every placed widget instance's own state
 * and asks Glance to redraw them. A no-op (no widget placed) resolves instantly since
 * `getGlanceIds` returns an empty list rather than throwing. */
object WidgetUpdater {
    suspend fun update(
        context: Context,
        flightsCount: Int? = null,
        cyberCount: Int? = null,
        conflictsCount: Int? = null,
        earthquakesCount: Int? = null,
        trafficCount: Int? = null,
    ) {
        val ids = GlanceAppWidgetManager(context).getGlanceIds(OsirisWidget::class.java)
        if (ids.isEmpty()) return
        ids.forEach { id -> writeWidgetCounts(context, id, flightsCount, cyberCount, conflictsCount, earthquakesCount, trafficCount) }
        OsirisWidget().updateAll(context)
    }
}

/** The widget's ↻ button — unlike [WidgetUpdater] (fed by the already-running app poll), this
 * does its own one-off fetch straight from the repositories, so it works even with the app
 * process dead. Each call fails independently (`runCatching`) so one dead layer doesn't blank the
 * others. Cyberattaques/Séismes fetch straight from their upstream now (no backend involved) so
 * they still refresh with no backend URL configured; Vols/Conflits/Trafic still need one. */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val backendUrl = BackendPreferences(context).backendUrlFlow.first()

        val cyberCount = runCatching { CyberAttacksRepository().fetch(backendUrl).size }.getOrNull()
        val earthquakesCount = runCatching {
            EarthquakesRepository().fetch(backendUrl).count { (it.magnitude ?: 0.0) >= WIDGET_EARTHQUAKE_MIN_MAGNITUDE }
        }.getOrNull()

        var flightsCount: Int? = null
        var conflictsCount: Int? = null
        var trafficCount: Int? = null
        if (backendUrl.isNotBlank()) {
            flightsCount = runCatching { FlightsRepository().fetch(backendUrl).size }.getOrNull()
            conflictsCount = runCatching {
                ConflictsRepository().fetch(backendUrl).count { it.severity in CONFLICT_ALERT_LEVELS }
            }.getOrNull()
            trafficCount = runCatching {
                TrafficRepository().fetch(backendUrl).count { (it.magnitude ?: 0) >= WIDGET_TRAFFIC_MIN_MAGNITUDE }
            }.getOrNull()
        }

        writeWidgetCounts(context, glanceId, flightsCount, cyberCount, conflictsCount, earthquakesCount, trafficCount)
        OsirisWidget().update(context, glanceId)
    }
}
