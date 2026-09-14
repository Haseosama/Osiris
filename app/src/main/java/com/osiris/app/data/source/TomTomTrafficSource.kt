package com.osiris.app.data.source

import com.osiris.app.BuildConfig
import com.osiris.app.data.model.TrafficIncident
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.net.URLEncoder
import kotlin.math.floor

/**
 * TomTom road traffic incidents, called directly from the phone — mirrors
 * `osiris-backend/src/app/api/traffic/route.ts`, key embedded via `BuildConfig.TOMTOM_API_KEY`
 * (see local.properties). Same 10,000km² bbox cap workaround: fans out to 8 fixed French
 * metro/highway hub tiles instead of one nationwide request (mainland France alone is
 * ~550,000km²), and normalizes TomTom's Point-vs-LineString geometry inconsistency the same
 * way the backend fix does.
 */
object TomTomTrafficSource {

    private data class Hub(val name: String, val lat: Double, val lng: Double)

    private val FRANCE_HUBS = listOf(
        Hub("Paris", 48.85, 2.35),
        Hub("Lyon", 45.75, 4.85),
        Hub("Marseille", 43.30, 5.37),
        Hub("Toulouse", 43.60, 1.44),
        Hub("Bordeaux", 44.84, -0.58),
        Hub("Nantes", 47.22, -1.55),
        Hub("Lille", 50.63, 3.06),
        Hub("Strasbourg", 48.58, 7.75),
    )
    private const val HUB_HALF_LNG = 0.5
    private const val HUB_HALF_LAT = 0.4

    private val ICON_CATEGORY_LABEL = mapOf(
        0 to "inconnu", 1 to "accident", 2 to "brouillard", 3 to "conditions dangereuses",
        4 to "pluie", 5 to "verglas", 6 to "bouchon", 7 to "voie fermée", 8 to "route fermée",
        9 to "travaux", 10 to "vent", 11 to "inondation", 14 to "véhicule en panne",
    )

    private const val FIELDS = "{incidents{type,geometry{type,coordinates},properties{iconCategory," +
        "magnitudeOfDelay,events{description,code,iconCategory},startTime,endTime,from,to," +
        "length,delay,roadNumbers}}}"

    suspend fun fetch(): List<TrafficIncident> = coroutineScope {
        val apiKey = BuildConfig.TOMTOM_API_KEY
        if (apiKey.isBlank()) return@coroutineScope emptyList()

        val perHub = FRANCE_HUBS.map { hub ->
            async {
                val bbox = listOf(
                    hub.lng - HUB_HALF_LNG, hub.lat - HUB_HALF_LAT,
                    hub.lng + HUB_HALF_LNG, hub.lat + HUB_HALF_LAT,
                ).joinToString(",")
                fetchIncidents(bbox, apiKey).mapIndexed { i, inc -> toOsirisIncident(inc, i, hub.name) }
            }
        }
        perHub.flatMap { it.await() }.filter { it.lat.isFinite() && it.lng.isFinite() && (it.lat != 0.0 || it.lng != 0.0) }
    }

    @Serializable
    private data class TomTomResponse(val incidents: List<TomTomIncident> = emptyList())

    @Serializable
    private data class TomTomIncident(
        val properties: TomTomProperties? = null,
        val geometry: TomTomGeometry? = null,
    )

    @Serializable
    private data class TomTomProperties(
        val iconCategory: Int? = null,
        val magnitudeOfDelay: Int? = null,
        val startTime: String? = null,
        val endTime: String? = null,
        val from: String? = null,
        val to: String? = null,
        val length: Double? = null,
        val roadNumbers: List<String> = emptyList(),
        val events: List<TomTomEvent> = emptyList(),
    )

    @Serializable private data class TomTomEvent(val description: String? = null)

    @Serializable
    private data class TomTomGeometry(
        val type: String? = null,
        val coordinates: JsonElement? = null,
    )

    private suspend fun fetchIncidents(bbox: String, apiKey: String): List<TomTomIncident> {
        val url = "https://api.tomtom.com/traffic/services/5/incidentDetails" +
            "?bbox=${URLEncoder.encode(bbox, "UTF-8")}&fields=${URLEncoder.encode(FIELDS, "UTF-8")}" +
            "&language=fr-FR&key=$apiKey"
        return runCatching { DirectHttp.getJson<TomTomResponse>(url).incidents }.getOrDefault(emptyList())
    }

    /** Normalizes TomTom's geometry to a nested `[lng,lat][]` shape regardless of whether it
     * sent a LineString (already nested) or a Point (a bare `[lng,lat]` pair) — a flat pair's
     * first element is a number, never an array, which is what tells the two shapes apart. */
    private fun normalizeCoords(geometry: TomTomGeometry?): List<List<Double>> {
        val raw = geometry?.coordinates as? JsonArray ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        val first = raw[0]
        return if (first is JsonPrimitive && first.doubleOrNull != null) {
            // Flat [lng, lat] pair (Point) — wrap as a single-point "line".
            listOf(raw.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull })
        } else {
            raw.mapNotNull { pair ->
                (pair as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }
            }
        }
    }

    private fun toOsirisIncident(inc: TomTomIncident, index: Int, idPrefix: String): TrafficIncident {
        val coords = normalizeCoords(inc.geometry)
        val mid = coords.getOrNull(floor(coords.size / 2.0).toInt()) ?: coords.firstOrNull() ?: listOf(0.0, 0.0)
        val lng = mid.getOrElse(0) { 0.0 }
        val lat = mid.getOrElse(1) { 0.0 }
        val props = inc.properties
        val iconCategory = props?.iconCategory
        return TrafficIncident(
            id = "tomtom-$idPrefix-$index-$lng-$lat",
            lat = lat,
            lng = lng,
            category = ICON_CATEGORY_LABEL[iconCategory] ?: "incident",
            iconCategory = iconCategory,
            magnitude = props?.magnitudeOfDelay,
            description = props?.events?.firstOrNull()?.description,
            from = props?.from,
            to = props?.to,
            road = props?.roadNumbers?.firstOrNull(),
            lengthM = props?.length,
            startTime = props?.startTime,
            geometry = coords,
        )
    }
}
