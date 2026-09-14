package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.source.DirectHttp
import kotlinx.serialization.Serializable

/** Taiwan CCTV — mirrors `osiris-backend/src/app/api/cctv/taiwan.ts`: four static YouTube
 * embeds (converted to external-link-only, same as elsewhere — see [FranceCctvSource]'s doc
 * comment) plus the Taiwan Highway Bureau's ~2,100 snapshot cameras. THB's encoders emit a
 * malformed response header whenever the request carries a `Referer`, which breaks OkHttp's
 * parser entirely — [com.osiris.app.map.CctvViewerDialog]'s image loader already knows to omit
 * it for `thb.gov.tw`, the same host list the backend's proxy used. Keyless. */
object TaiwanCctvSource {
    private val YOUTUBE = listOf(
        CctvCamera(
            id = "tw-taipei-101", lat = 25.0330, lng = 121.5654, name = "Taipei 101 Live",
            city = "Taipei", country = "Taiwan", source = "YouTube",
            externalUrl = "https://www.youtube.com/watch?v=rL5YKnxBudA",
        ),
        CctvCamera(
            id = "tw-taipei-ximending", lat = 25.0422, lng = 121.5079, name = "Ximending Walking District",
            city = "Taipei", country = "Taiwan", source = "YouTube",
            externalUrl = "https://www.youtube.com/watch?v=W3A3gCqj7bY",
        ),
        CctvCamera(
            id = "tw-kaohsiung-harbor", lat = 22.6142, lng = 120.2843, name = "Kaohsiung Harbor Live",
            city = "Kaohsiung", country = "Taiwan", source = "YouTube",
            externalUrl = "https://www.youtube.com/watch?v=PdX18mxuYRE",
        ),
        CctvCamera(
            id = "tw-keelung-harbor", lat = 25.1291, lng = 121.7423, name = "Keelung Harbor",
            city = "Keelung", country = "Taiwan", source = "YouTube",
            externalUrl = "https://www.youtube.com/watch?v=SX90gCtF3bY",
        ),
    )

    // Rough bounding boxes for major cities — same table the backend uses to caption a THB
    // camera, which otherwise carries no city field of its own.
    private val CITY_BOUNDS = listOf(
        "Taipei" to listOf(24.9, 25.2, 121.4, 121.7),
        "Taichung" to listOf(24.0, 24.3, 120.5, 120.9),
        "Kaohsiung" to listOf(22.5, 22.8, 120.1, 120.5),
        "Tainan" to listOf(22.9, 23.1, 120.1, 120.3),
        "Taoyuan" to listOf(24.7, 25.0, 121.0, 121.5),
        "New Taipei" to listOf(24.7, 25.0, 121.5, 122.0),
        "Chiayi" to listOf(23.3, 23.6, 120.3, 120.6),
        "Changhua" to listOf(23.8, 24.1, 120.6, 121.0),
        "Yilan" to listOf(24.7, 24.9, 121.6, 122.0),
        "Hualien" to listOf(23.9, 24.2, 121.4, 121.7),
    )

    private fun cityFor(lat: Double, lng: Double): String =
        CITY_BOUNDS.firstOrNull { (_, b) -> lat > b[0] && lat < b[1] && lng > b[2] && lng < b[3] }?.first ?: "Taiwan"

    private val NON_ALNUM_REGEX = Regex("[^a-zA-Z0-9]")

    private suspend fun fetchThb(): List<CctvCamera> = runCatching {
        val records = DirectHttp.getJson<List<ThbCamera>>(
            "https://thbapp.thb.gov.tw/services/cctv/thb",
            headers = mapOf("Accept" to "application/json"),
        )
        records.mapNotNull { c ->
            val html = c.html?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val lat = c.gisy?.toDoubleOrNull() ?: return@mapNotNull null
            val lng = c.gisx?.toDoubleOrNull() ?: return@mapNotNull null
            val slug = (c.id ?: c.stakenumber ?: return@mapNotNull null).replace(NON_ALNUM_REGEX, "-").lowercase()

            CctvCamera(
                id = "tw-thb-$slug",
                lat = lat,
                lng = lng,
                name = c.stakenumber?.takeIf { it.isNotBlank() } ?: c.id?.takeIf { it.isNotBlank() } ?: "THB Camera",
                city = cityFor(lat, lng),
                country = "Taiwan",
                feedUrl = "$html/snapshot",
                source = "THB Highway Bureau",
            )
        }
    }.getOrDefault(emptyList())

    suspend fun fetch(): List<CctvCamera> = YOUTUBE + fetchThb()

    @Serializable
    private data class ThbCamera(
        val id: String? = null,
        val stakenumber: String? = null,
        val gisx: String? = null,
        val gisy: String? = null,
        val html: String? = null,
    )
}
