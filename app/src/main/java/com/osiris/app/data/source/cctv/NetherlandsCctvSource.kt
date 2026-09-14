package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.source.DirectHttp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Netherlands motorway cameras (Rijkswaterstaat, 26 HD cameras), called directly from the
 * phone — mirrors `osiris-backend/src/app/api/cctv/netherlands.ts`. Keyless. `static_url`'s CDN
 * (`stream.inmoves.nl`) 401s without a same-origin `Referer`, which
 * [com.osiris.app.map.CctvViewerDialog]'s image loader now sends automatically for every
 * snapshot — no per-source proxy needed on-device the way the backend used one. */
object NetherlandsCctvSource {
    private const val MIN_LAT = 50.7
    private const val MAX_LAT = 53.7
    private const val MIN_LNG = 3.3
    private const val MAX_LNG = 7.3

    suspend fun fetch(): List<CctvCamera> = runCatching {
        val cameras = DirectHttp.getJson<List<RwsCamera>>(
            "https://api.rwsverkeersinfo.nl/api/cameras",
            headers = mapOf("Accept" to "application/json"),
        )
        val seen = mutableSetOf<String>()
        cameras.mapNotNull { cam ->
            val id = cam.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val lat = cam.latitude?.toDoubleOrNull() ?: return@mapNotNull null
            val lng = cam.longitude?.toDoubleOrNull() ?: return@mapNotNull null
            if (lat < MIN_LAT || lat > MAX_LAT || lng < MIN_LNG || lng > MAX_LNG) return@mapNotNull null
            val feedUrl = cam.staticUrl?.takeIf { it.isNotBlank() }
            val externalUrl = cam.streamUrl?.takeIf { it.isNotBlank() }
            if (feedUrl == null && externalUrl == null) return@mapNotNull null
            if (!seen.add(id)) return@mapNotNull null

            val near = cam.near?.trim().orEmpty()
            val road = cam.road?.trim().orEmpty()
            val name = listOf(road, near).filter { it.isNotEmpty() }.joinToString(" — ")
                .ifEmpty { cam.locationDescription?.trim()?.takeIf { it.isNotEmpty() } ?: "RWS Camera $id" }

            CctvCamera(
                id = "nl-rws-$id",
                lat = lat,
                lng = lng,
                name = name,
                city = near.ifEmpty { "Netherlands" },
                country = "Netherlands",
                feedUrl = feedUrl,
                externalUrl = externalUrl,
                source = "Rijkswaterstaat",
            )
        }
    }.getOrDefault(emptyList())

    @Serializable
    private data class RwsCamera(
        val id: String? = null,
        val latitude: String? = null,
        val longitude: String? = null,
        val road: String? = null,
        val near: String? = null,
        @SerialName("location_description") val locationDescription: String? = null,
        @SerialName("stream_url") val streamUrl: String? = null,
        @SerialName("static_url") val staticUrl: String? = null,
    )
}
