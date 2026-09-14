package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.source.DirectHttp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Iceland road cameras (Vegagerðin, ~488 nationwide), called directly from the phone — mirrors
 * `osiris-backend/src/app/api/cctv/iceland.ts`. Keyless. */
object IcelandCctvSource {

    suspend fun fetch(): List<CctvCamera> = runCatching {
        val cameras = DirectHttp.getJson<List<VegagerdinCamera>>("https://gagnaveita.vegagerdin.is/api/vefmyndavelar2014_1")
        cameras.mapIndexedNotNull { index, cam ->
            val lat = cam.breidd ?: return@mapIndexedNotNull null
            val lng = cam.lengd ?: return@mapIndexedNotNull null
            val slod = cam.slod?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            val feedUrl = if (slod.startsWith("/")) "https://www.vegagerdin.is$slod" else slod
            val stationId = cam.maelistNr ?: index
            CctvCamera(
                id = "is-$stationId-$index",
                lat = lat,
                lng = lng,
                name = listOfNotNull(cam.myndavel, cam.skyring).joinToString(" — ").ifBlank { "Vegagerðin Camera" },
                city = cam.myndavel ?: "Iceland",
                country = "Iceland",
                feedUrl = feedUrl,
                source = "Vegagerðin",
            )
        }
    }.getOrDefault(emptyList())

    @Serializable
    private data class VegagerdinCamera(
        @SerialName("Maelist_nr") val maelistNr: Int? = null,
        @SerialName("Myndavel") val myndavel: String? = null,
        @SerialName("Skyring") val skyring: String? = null,
        @SerialName("Slod") val slod: String? = null,
        @SerialName("Breidd") val breidd: Double? = null,
        @SerialName("Lengd") val lengd: Double? = null,
    )
}
