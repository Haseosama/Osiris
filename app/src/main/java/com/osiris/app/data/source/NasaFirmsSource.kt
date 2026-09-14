package com.osiris.app.data.source

import com.osiris.app.data.model.FireEvent
import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.round

/**
 * NASA FIRMS active-fire CSV (VIIRS, falling back to MODIS) plus NASA EONET volcanoes, called
 * directly from the phone — mirrors `osiris-backend/src/app/api/fires/route.ts`. Both sources
 * are keyless open data; `FIRMS_API_KEY` in the backend's `.env.example` was for a different,
 * unused per-area API and was never actually required.
 */
object NasaFirmsSource {

    private val FIRMS_URLS = listOf(
        "https://firms.modaps.eosdis.nasa.gov/data/active_fire/suomi-npp-viirs-c2/csv/SUOMI_VIIRS_C2_Global_24h.csv",
        "https://firms.modaps.eosdis.nasa.gov/data/active_fire/modis-c6.1/csv/MODIS_C6_1_Global_24h.csv",
    )
    private const val EONET_VOLCANOES_URL =
        "https://eonet.gsfc.nasa.gov/api/v3/events?status=open&category=volcanoes&limit=50"
    private const val MAX_POINTS = 2000

    suspend fun fetch(): List<FireEvent> {
        val fires = mutableListOf<FireEvent>()

        for (url in FIRMS_URLS) {
            val csv = runCatching { DirectHttp.getText(url, headers = UA) }.getOrNull() ?: continue
            if (!csv.contains("latitude") || csv.length <= 200) continue
            val parsed = parseCsv(csv)
            if (parsed.isNotEmpty()) {
                fires += parsed
                break
            }
        }

        runCatching { fetchVolcanoes() }.getOrNull()?.let { fires += it }

        return fires
    }

    private val UA = mapOf("User-Agent" to "OSIRIS-Android/1.0")

    private fun parseCsv(csv: String): List<FireEvent> {
        val lines = csv.trim().split("\n")
        if (lines.size < 2) return emptyList()

        val header = lines[0].split(",")
        val latIdx = header.indexOf("latitude")
        val lngIdx = header.indexOf("longitude")
        val brightIdx = header.indexOf("bright_ti4").takeIf { it != -1 } ?: header.indexOf("brightness")
        val confIdx = header.indexOf("confidence")
        val dateIdx = header.indexOf("acq_date")
        val timeIdx = header.indexOf("acq_time")
        val frpIdx = header.indexOf("frp")

        val step = if (lines.size > MAX_POINTS) ceil(lines.size.toDouble() / MAX_POINTS).toInt() else 1
        val out = mutableListOf<FireEvent>()

        var i = 1
        while (i < lines.size) {
            val cols = lines[i].split(",")
            val lat = cols.getOrNull(latIdx)?.toDoubleOrNull()
            val lng = cols.getOrNull(lngIdx)?.toDoubleOrNull()
            if (lat != null && lng != null) {
                out += FireEvent(
                    lat = round(lat * 1000) / 1000,
                    lng = round(lng * 1000) / 1000,
                    brightness = cols.getOrNull(brightIdx)?.toDoubleOrNull() ?: 0.0,
                    confidence = cols.getOrNull(confIdx)?.takeIf { it.isNotBlank() } ?: "unknown",
                    date = cols.getOrNull(dateIdx).orEmpty(),
                    time = cols.getOrNull(timeIdx).orEmpty(),
                    frp = cols.getOrNull(frpIdx)?.toDoubleOrNull() ?: 0.0,
                    type = "fire",
                )
            }
            i += step
        }
        return out
    }

    @Serializable
    private data class EonetResponse(val events: List<EonetEvent> = emptyList())

    @Serializable
    private data class EonetEvent(
        val title: String? = null,
        val geometry: List<EonetGeometry> = emptyList(),
    )

    @Serializable
    private data class EonetGeometry(
        val date: String? = null,
        val coordinates: List<Double> = emptyList(),
    )

    private suspend fun fetchVolcanoes(): List<FireEvent> {
        val data = DirectHttp.getJson<EonetResponse>(EONET_VOLCANOES_URL)
        return data.events.mapNotNull { e ->
            val geo = e.geometry.lastOrNull() ?: return@mapNotNull null
            if (geo.coordinates.size < 2) return@mapNotNull null
            FireEvent(
                lat = geo.coordinates[1],
                lng = geo.coordinates[0],
                brightness = 500.0,
                confidence = "high",
                date = geo.date?.substringBefore("T").orEmpty(),
                time = "",
                frp = 100.0,
                title = "[VOLCANO] ${e.title.orEmpty()}",
                type = "volcano",
            )
        }
    }
}
