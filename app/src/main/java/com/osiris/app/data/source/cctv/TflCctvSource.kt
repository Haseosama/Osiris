package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.source.DirectHttp
import kotlinx.serialization.Serializable

/** Transport for London JamCams (~900), called directly from the phone — mirrors
 * `fetchTfLCameras()` in `osiris-backend/src/app/api/cctv/route.ts`. Keyless. */
object TflCctvSource {

    suspend fun fetch(): List<CctvCamera> = runCatching {
        val places = DirectHttp.getJson<List<TflPlace>>("https://api.tfl.gov.uk/Place/Type/JamCam")
        places.mapNotNull { place ->
            val lat = place.lat ?: return@mapNotNull null
            val lng = place.lon ?: return@mapNotNull null
            val imageUrl = place.additionalProperties.firstOrNull { it.key == "imageUrl" }?.value
            val camId = place.id?.removePrefix("JamCams_").orEmpty()
            CctvCamera(
                id = "tfl-${place.id}",
                lat = lat,
                lng = lng,
                name = place.commonName ?: "London JamCam",
                city = "London",
                country = "UK",
                feedUrl = imageUrl ?: "https://s3-eu-west-1.amazonaws.com/jamcams.tfl.gov.uk/$camId.jpg",
                source = "TfL",
            )
        }
    }.getOrDefault(emptyList())

    @Serializable
    private data class TflPlace(
        val id: String? = null,
        val commonName: String? = null,
        val lat: Double? = null,
        val lon: Double? = null,
        val additionalProperties: List<TflProperty> = emptyList(),
    )

    @Serializable
    private data class TflProperty(val key: String? = null, val value: String? = null)
}
