package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.source.DirectHttp
import kotlinx.serialization.Serializable

/** Finland weathercam network (Digitraffic/Fintraffic, ~470 stations), called directly from the
 * phone — mirrors `osiris-backend/src/app/api/cctv/finland.ts`. Keyless. */
object FinlandCctvSource {

    suspend fun fetch(): List<CctvCamera> = runCatching {
        val response = DirectHttp.getJson<StationsResponse>(
            "https://tie.digitraffic.fi/api/weathercam/v1/stations",
            headers = mapOf("Digitraffic-User" to "Osiris-Android/1.0"),
        )
        response.features.mapNotNull { feature ->
            val lat = feature.geometry?.coordinates?.getOrNull(1) ?: return@mapNotNull null
            val lng = feature.geometry?.coordinates?.getOrNull(0) ?: return@mapNotNull null
            val props = feature.properties ?: return@mapNotNull null
            val preset = props.presets?.firstOrNull() ?: return@mapNotNull null
            val imageUrl = preset.imageUrl ?: "https://weathercam.digitraffic.fi/${preset.id}.jpg"
            CctvCamera(
                id = "fin-${feature.id ?: props.id ?: return@mapNotNull null}",
                lat = lat,
                lng = lng,
                name = props.name ?: props.names?.fi ?: "Finland Weathercam",
                city = props.municipality ?: "Finland",
                country = "Finland",
                feedUrl = imageUrl,
                source = "Fintraffic",
            )
        }
    }.getOrDefault(emptyList())

    @Serializable
    private data class StationsResponse(val features: List<Feature> = emptyList())

    @Serializable
    private data class Feature(val id: String? = null, val geometry: Geometry? = null, val properties: StationProperties? = null)

    @Serializable
    private data class Geometry(val coordinates: List<Double> = emptyList())

    @Serializable
    private data class StationProperties(
        val id: String? = null,
        val name: String? = null,
        val names: Names? = null,
        val municipality: String? = null,
        val presets: List<Preset>? = null,
    )

    @Serializable
    private data class Names(val fi: String? = null)

    @Serializable
    private data class Preset(val id: String? = null, val imageUrl: String? = null)
}
