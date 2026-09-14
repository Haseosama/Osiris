package com.osiris.app.data.source

import com.osiris.app.data.model.Earthquake
import kotlinx.serialization.Serializable

/**
 * USGS earthquake feed, called directly from the phone — mirrors what
 * `osiris-backend/src/app/api/earthquakes/route.ts` used to do server-side. Keyless, no CORS
 * concern off a browser, so there is nothing a backend proxy was adding here.
 */
object UsgsEarthquakeSource {

    private const val URL = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_day.geojson"

    @Serializable
    private data class GeoJsonResponse(val features: List<Feature> = emptyList())

    @Serializable
    private data class Feature(
        val id: String? = null,
        val geometry: Geometry? = null,
        val properties: Properties? = null,
    )

    @Serializable
    private data class Geometry(val coordinates: List<Double> = emptyList())

    @Serializable
    private data class Properties(
        val mag: Double? = null,
        val place: String? = null,
        val time: Long? = null,
        val url: String? = null,
        val tsunami: Int? = null,
        val type: String? = null,
    )

    suspend fun fetch(): List<Earthquake> {
        val data = DirectHttp.getJson<GeoJsonResponse>(URL)
        return data.features.mapNotNull { f ->
            val coords = f.geometry?.coordinates ?: return@mapNotNull null
            if (coords.size < 2) return@mapNotNull null
            val props = f.properties
            Earthquake(
                id = f.id,
                lat = coords[1],
                lng = coords[0],
                depth = coords.getOrNull(2),
                magnitude = props?.mag,
                place = props?.place,
                time = props?.time,
                url = props?.url,
                tsunami = props?.tsunami,
                type = props?.type,
            )
        }
    }
}
