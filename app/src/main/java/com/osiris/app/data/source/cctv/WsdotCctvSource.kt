package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.source.DirectHttp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** WSDOT Washington State highway cameras (~500), called directly from the phone — mirrors
 * `fetchWSDOTCameras()` in `osiris-backend/src/app/api/cctv/route.ts`. Keyless. */
object WsdotCctvSource {

    suspend fun fetch(): List<CctvCamera> = runCatching {
        val cameras = DirectHttp.getJson<List<WsdotCamera>>("https://data.wsdot.wa.gov/log/public/cameras.json")
        cameras.mapNotNull { cam ->
            val lat = cam.location?.latitude ?: return@mapNotNull null
            val lng = cam.location.longitude ?: return@mapNotNull null
            val feedUrl = cam.imageUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CctvCamera(
                id = "wsdot-${cam.cameraId}",
                lat = lat,
                lng = lng,
                name = cam.title ?: "WSDOT Camera",
                city = "Washington",
                country = "US",
                feedUrl = feedUrl,
                source = "WSDOT",
            )
        }
    }.getOrDefault(emptyList())

    @Serializable
    private data class WsdotCamera(
        @SerialName("CameraID") val cameraId: Int? = null,
        @SerialName("CameraLocation") val location: WsdotLocation? = null,
        @SerialName("Title") val title: String? = null,
        @SerialName("ImageURL") val imageUrl: String? = null,
    )

    @Serializable
    private data class WsdotLocation(
        @SerialName("Latitude") val latitude: Double? = null,
        @SerialName("Longitude") val longitude: Double? = null,
    )
}
