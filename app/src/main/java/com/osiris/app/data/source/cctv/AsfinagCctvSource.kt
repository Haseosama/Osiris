package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.source.DirectHttp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Austria highway webcams (ASFINAG), called directly from the phone — mirrors
 * `osiris-backend/src/app/api/cctv/asfinag.ts`. The `Authorization` value below isn't a personal
 * credential — it's the static Basic-auth token ASFINAG's own public map widget ships to every
 * visitor's browser (already public in the backend's own source, which is MIT-licensed), not a
 * secret issued to Osiris. */
object AsfinagCctvSource {
    private const val URL = "https://odo.asfinag.at/odo/rest/sec/resource/001/json/webcams?language=atDE"
    private val HEADERS = mapOf(
        "Accept" to "application/json",
        "Referer" to "https://www.asfinag.at/",
        "Authorization" to "Basic bWFwX3dpZGdldDp0ZWdkaXc=",
        "Origin" to "https://www.asfinag.at",
    )

    suspend fun fetch(): List<CctvCamera> = runCatching {
        val webcams = DirectHttp.getJson<List<AsfinagWebcam>>(URL, headers = HEADERS)
        webcams.mapNotNull { cam ->
            val wcsId = cam.wcsId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (wcsId.startsWith("Utinform")) return@mapNotNull null // Hungarian road authority — feeds unavailable
            val lat = cam.lat ?: return@mapNotNull null
            val lng = cam.lng ?: return@mapNotNull null
            val feedUrl = cam.imageUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CctvCamera(
                id = "asfinag-$wcsId",
                lat = lat,
                lng = lng,
                name = cam.position?.takeIf { it.isNotBlank() } ?: cam.direction?.takeIf { it.isNotBlank() } ?: "ASFINAG Webcam",
                city = "Austria",
                country = "Austria",
                feedUrl = feedUrl,
                source = "ASFINAG",
            )
        }
    }.getOrDefault(emptyList())

    @Serializable
    private data class AsfinagWebcam(
        @SerialName("wcs_id") val wcsId: String? = null,
        @SerialName("wgs84_lat") val lat: Double? = null,
        @SerialName("wgs84_lon") val lng: Double? = null,
        @SerialName("position_txt") val position: String? = null,
        @SerialName("direction_txt") val direction: String? = null,
        @SerialName("url_campic") val imageUrl: String? = null,
    )
}
