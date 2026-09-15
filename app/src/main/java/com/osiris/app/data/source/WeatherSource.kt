package com.osiris.app.data.source

import com.osiris.app.data.model.WeatherEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Severe weather / anomaly events from three merged sources, called directly from the phone —
 * mirrors `osiris-backend/src/app/api/weather/route.ts`. NASA EONET (global storms/volcanoes/
 * sea ice), NOAA/NWS active alerts (US-only), and GDACS (global cyclones/floods/droughts,
 * scraped from its RSS feed with regex — same approach the backend used, there's no JSON API).
 * All three are keyless; a normal browser-like User-Agent stands in for the backend's
 * `stealthFetch` (its IP-spoofing headers do nothing from a real device anyway — only the UA
 * rotation could plausibly matter, and a single realistic one is enough).
 */
object WeatherSource {

    private val BROWSER_UA = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
    )

    suspend fun fetch(): List<WeatherEvent> = coroutineScope {
        val eonetDeferred = async { runCatching { fetchEonet() }.getOrNull() }
        val nwsDeferred = async { runCatching { fetchNws() }.getOrNull() }
        val gdacsDeferred = async { runCatching { fetchGdacs() }.getOrNull() }
        listOfNotNull(eonetDeferred.await(), nwsDeferred.await(), gdacsDeferred.await()).flatten()
    }

    // ── NASA EONET ────────────────────────────────────────────────────

    @Serializable
    private data class EonetResponse(val events: List<EonetEvent> = emptyList())

    @Serializable
    private data class EonetEvent(
        val id: String? = null,
        val title: String? = null,
        val categories: List<EonetCategory> = emptyList(),
        val geometry: List<EonetGeometry> = emptyList(),
        val sources: List<EonetSource> = emptyList(),
    )

    @Serializable private data class EonetCategory(val id: String? = null, val title: String? = null)
    @Serializable private data class EonetGeometry(val type: String? = null, val coordinates: List<Double> = emptyList(), val date: String? = null)
    @Serializable private data class EonetSource(val url: String? = null)

    private suspend fun fetchEonet(): List<WeatherEvent> {
        val data = DirectHttp.getJson<EonetResponse>(
            "https://eonet.gsfc.nasa.gov/api/v3/events?status=open&limit=100",
            headers = BROWSER_UA,
        )
        return data.events.mapNotNull { event ->
            val geom = event.geometry.lastOrNull() ?: return@mapNotNull null
            if (geom.type != "Point" || geom.coordinates.size < 2) return@mapNotNull null

            val category = event.categories.firstOrNull()?.id ?: "unknown"
            if (category == "wildfires" || category == "earthquakes") return@mapNotNull null

            val (typeLabel, icon, severity) = when (category) {
                "severeStorms" -> Triple("Tempête sévère", "cyclone", "high")
                "volcanoes" -> Triple("Éruption volcanique", "volcano", "high")
                "seaIce" -> Triple("Iceberg / banquise", "ice", "medium")
                else -> Triple(event.categories.firstOrNull()?.title ?: "Anomalie", "alert", "low")
            }

            WeatherEvent(
                id = "eonet-${event.id}",
                title = event.title,
                category = category,
                type = typeLabel,
                icon = icon,
                severity = severity,
                lat = geom.coordinates[1],
                lng = geom.coordinates[0],
                date = geom.date,
                source = event.sources.firstOrNull()?.url ?: "NASA EONET",
            )
        }
    }

    // ── NOAA/NWS ──────────────────────────────────────────────────────

    @Serializable
    private data class NwsResponse(val features: List<NwsFeature> = emptyList())

    @Serializable
    private data class NwsFeature(val geometry: NwsGeometry? = null, val properties: NwsProperties? = null)

    @Serializable
    private data class NwsGeometry(val type: String? = null, val coordinates: JsonElement? = null)

    @Serializable
    private data class NwsProperties(
        val id: String? = null,
        val headline: String? = null,
        val event: String? = null,
        val severity: String? = null,
        val effective: String? = null,
        val sent: String? = null,
        val expires: String? = null,
        val areaDesc: String? = null,
    )

    private suspend fun fetchNws(): List<WeatherEvent> {
        val data = DirectHttp.getJson<NwsResponse>(
            "https://api.weather.gov/alerts/active?status=actual&message_type=alert",
            headers = mapOf("Accept" to "application/geo+json", "User-Agent" to "OSIRIS Severe Weather Layer"),
        )
        return data.features.mapNotNull { feature ->
            val props = feature.properties ?: return@mapNotNull null
            val point = representativePoint(feature.geometry) ?: return@mapNotNull null
            WeatherEvent(
                id = "nws-${props.id ?: props.event ?: point.first}",
                title = props.headline ?: props.event ?: "Alerte météo NWS",
                category = "weatherAlerts",
                type = props.event ?: "Alerte météo",
                icon = "weather",
                severity = normalizeNwsSeverity(props.severity),
                lat = point.first,
                lng = point.second,
                date = props.effective ?: props.sent,
                expires = props.expires,
                source = props.id ?: "https://api.weather.gov/alerts/active",
            )
        }
    }

    private fun normalizeNwsSeverity(severity: String?): String = when (severity) {
        "Extreme", "Severe" -> "high"
        "Moderate" -> "medium"
        else -> "low"
    }

    /** NWS alert geometry is Point, Polygon or MultiPolygon — a flattened `[lng,lat]` list for a
     * Point, or nested rings for the others. Represent a Polygon/MultiPolygon by the average of
     * its outer ring, same as the backend. */
    private fun representativePoint(geometry: NwsGeometry?): Pair<Double, Double>? {
        val el = geometry?.coordinates ?: return null
        return when (geometry.type) {
            "Point" -> {
                val arr = el as? JsonArray ?: return null
                val lng = arr.getOrNull(0)?.let { (it as? JsonPrimitive)?.doubleOrNull } ?: return null
                val lat = arr.getOrNull(1)?.let { (it as? JsonPrimitive)?.doubleOrNull } ?: return null
                lat to lng
            }
            "Polygon" -> {
                val outerRing = ((el as? JsonArray)?.getOrNull(0) as? JsonArray)
                averageRing(outerRing)
            }
            "MultiPolygon" -> {
                val firstPolygon = (el as? JsonArray)?.getOrNull(0) as? JsonArray
                val outerRing = firstPolygon?.getOrNull(0) as? JsonArray
                averageRing(outerRing)
            }
            else -> null
        }
    }

    private fun averageRing(ring: JsonArray?): Pair<Double, Double>? {
        if (ring.isNullOrEmpty()) return null
        var sumLat = 0.0
        var sumLng = 0.0
        var count = 0
        for (point in ring) {
            val arr = point as? JsonArray ?: continue
            val lng = (arr.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: continue
            val lat = (arr.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: continue
            sumLng += lng
            sumLat += lat
            count++
        }
        if (count == 0) return null
        return (sumLat / count) to (sumLng / count)
    }

    // ── GDACS (RSS/XML, regex-scraped — no JSON API) ──────────────────

    private val GDACS_TYPE_MAP = mapOf(
        "TC" to ("Cyclone tropical" to "cyclone"),
        "FL" to ("Inondation" to "flood"),
        "DR" to ("Sécheresse" to "drought"),
    )

    private suspend fun fetchGdacs(): List<WeatherEvent> {
        val xml = DirectHttp.getText("https://www.gdacs.org/xml/rss.xml", headers = BROWSER_UA)
        return parseGdacsRss(xml)
    }

    private fun gdacsTag(itemXml: String, tag: String): String {
        val m = Regex("<$tag[^>]*>([\\s\\S]*?)</$tag>", RegexOption.IGNORE_CASE).find(itemXml)
        return m?.groupValues?.get(1)?.trim().orEmpty()
    }

    private fun normalizeGdacsSeverity(alertLevel: String): String = when (alertLevel.lowercase()) {
        "red" -> "high"
        "orange" -> "medium"
        else -> "low"
    }

    private fun parseGdacsRss(xml: String): List<WeatherEvent> {
        val events = mutableListOf<WeatherEvent>()
        val itemRegex = Regex("<item>([\\s\\S]*?)</item>", RegexOption.IGNORE_CASE)
        for (match in itemRegex.findAll(xml)) {
            val itemXml = match.groupValues[1]
            val eventType = gdacsTag(itemXml, "gdacs:eventtype")
            val mapped = GDACS_TYPE_MAP[eventType] ?: continue

            val latMatch = Regex("<geo:lat>([-\\d.]+)</geo:lat>", RegexOption.IGNORE_CASE).find(itemXml) ?: continue
            val lonMatch = Regex("<geo:long>([-\\d.]+)</geo:long>", RegexOption.IGNORE_CASE).find(itemXml) ?: continue

            val eventId = gdacsTag(itemXml, "gdacs:eventid")
            val episodeId = gdacsTag(itemXml, "gdacs:episodeid")
            val title = gdacsTag(itemXml, "title").replace("&amp;", "&").replace("&gt;", ">").replace("&lt;", "<")
            val country = gdacsTag(itemXml, "gdacs:country")
            val alertLevel = gdacsTag(itemXml, "gdacs:alertlevel")
            val link = gdacsTag(itemXml, "link").replace("&amp;", "&")

            events += WeatherEvent(
                id = "gdacs-$eventType-$eventId-$episodeId",
                title = title,
                category = "gdacs",
                type = mapped.first,
                icon = mapped.second,
                severity = normalizeGdacsSeverity(alertLevel),
                lat = latMatch.groupValues[1].toDouble(),
                lng = lonMatch.groupValues[1].toDouble(),
                date = gdacsTag(itemXml, "pubDate"),
                source = link.ifBlank { "https://www.gdacs.org/" },
            )
        }
        return events
    }
}
