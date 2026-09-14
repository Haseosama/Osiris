package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.source.DirectHttp
import kotlinx.serialization.Serializable

/** Oregon road cameras (ODOT TripCheck, ~1,100 statewide), called directly from the phone —
 * mirrors `osiris-backend/src/app/api/cctv/oregon.ts`. Keyless — but the endpoint 406s on a
 * JSON-specific Accept header, it only serves `* / *`, hence the explicit override below. */
object OregonCctvSource {
    private const val INVENTORY = "https://www.tripcheck.com/Scripts/map/data/cctvinventory.js"
    private const val IMAGE_BASE = "https://tripcheck.com/RoadCams/cams"
    private const val MIN_LAT = 41.9
    private const val MAX_LAT = 46.3
    private const val MIN_LNG = -124.6
    private const val MAX_LNG = -116.4

    suspend fun fetch(): List<CctvCamera> = runCatching {
        val response = DirectHttp.getJson<TripCheckResponse>(INVENTORY, headers = mapOf("Accept" to "*/*"))
        val seen = mutableSetOf<String>()
        response.features.mapNotNull { feature ->
            val a = feature.attributes ?: return@mapNotNull null
            val cameraId = a.cameraId ?: return@mapNotNull null
            val filename = a.filename?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val lat = a.latitude ?: return@mapNotNull null
            val lng = a.longitude ?: return@mapNotNull null
            if (lat < MIN_LAT || lat > MAX_LAT || lng < MIN_LNG || lng > MAX_LNG) return@mapNotNull null
            val id = "odot-$cameraId"
            if (!seen.add(id)) return@mapNotNull null

            CctvCamera(
                id = id,
                lat = lat,
                lng = lng,
                name = (a.title?.trim()?.takeIf { it.isNotEmpty() } ?: a.route?.trim()?.takeIf { it.isNotEmpty() } ?: "ODOT Camera $cameraId"),
                city = a.route?.trim()?.takeIf { it.isNotEmpty() } ?: "Oregon",
                country = "US",
                feedUrl = "$IMAGE_BASE/$filename",
                source = "ODOT TripCheck",
            )
        }
    }.getOrDefault(emptyList())

    @Serializable
    private data class TripCheckResponse(val features: List<TripCheckFeature> = emptyList())

    @Serializable
    private data class TripCheckFeature(val attributes: TripCheckAttributes? = null)

    @Serializable
    private data class TripCheckAttributes(
        val cameraId: Int? = null,
        val filename: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val route: String? = null,
        val title: String? = null,
    )
}
