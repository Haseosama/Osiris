package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.source.DirectHttp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Caltrans California highway cameras, called directly from the phone — mirrors
 * `fetchCaltransCameras()` in `osiris-backend/src/app/api/cctv/route.ts`. Keyless ArcGIS
 * FeatureServer query. */
object CaltransCctvSource {

    private const val URL =
        "https://caltrans-gis.dot.ca.gov/arcgis/rest/services/CHhighway/CCTV/FeatureServer/0/query" +
            "?where=1%3D1&outFields=*&f=json"

    suspend fun fetch(): List<CctvCamera> = runCatching {
        val response = DirectHttp.getJson<CaltransResponse>(URL)
        response.features.mapNotNull { feature ->
            val p = feature.attributes
            val lat = p.latitude ?: return@mapNotNull null
            val lng = p.longitude ?: return@mapNotNull null
            val feedUrl = p.currentImageUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CctvCamera(
                id = "cal-${p.objectId}",
                lat = lat,
                lng = lng,
                name = p.locationName ?: "Caltrans",
                city = p.nearbyPlace ?: p.county ?: "California",
                country = "US",
                feedUrl = feedUrl,
                source = "Caltrans",
            )
        }
    }.getOrDefault(emptyList())

    @Serializable
    private data class CaltransResponse(val features: List<CaltransFeature> = emptyList())

    @Serializable
    private data class CaltransFeature(val attributes: CaltransAttributes)

    @Serializable
    private data class CaltransAttributes(
        @SerialName("OBJECTID") val objectId: Long? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        @SerialName("currentImageURL") val currentImageUrl: String? = null,
        val locationName: String? = null,
        val nearbyPlace: String? = null,
        val county: String? = null,
    )
}
