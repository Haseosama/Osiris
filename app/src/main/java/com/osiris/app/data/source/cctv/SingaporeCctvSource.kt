package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.source.DirectHttp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Singapore live traffic cameras (LTA), called directly from the phone — mirrors the `asia`
 * region's Singapore block in `osiris-backend/src/app/api/cctv/route.ts`
 * (`fetchAsiaCameras`). Keyless. */
object SingaporeCctvSource {
    suspend fun fetch(): List<CctvCamera> = runCatching {
        val response = DirectHttp.getJson<TrafficImagesResponse>("https://api.data.gov.sg/v1/transport/traffic-images")
        val cameras = response.items.firstOrNull()?.cameras.orEmpty()
        cameras.mapNotNull { cam ->
            val lat = cam.location?.latitude ?: return@mapNotNull null
            val lng = cam.location?.longitude ?: return@mapNotNull null
            val image = cam.image?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CctvCamera(
                id = "sin-${cam.cameraId}",
                lat = lat,
                lng = lng,
                name = "Camera ${cam.cameraId}",
                city = "Singapore",
                country = "Singapore",
                feedUrl = image,
                source = "LTA Singapore",
            )
        }
    }.getOrDefault(emptyList())

    @Serializable
    private data class TrafficImagesResponse(val items: List<Item> = emptyList())

    @Serializable
    private data class Item(val cameras: List<Camera> = emptyList())

    @Serializable
    private data class Camera(
        @SerialName("camera_id") val cameraId: String? = null,
        val location: Location? = null,
        val image: String? = null,
    )

    @Serializable
    private data class Location(val latitude: Double? = null, val longitude: Double? = null)
}
