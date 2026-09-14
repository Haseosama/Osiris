package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.source.DirectHttp
import kotlinx.serialization.Serializable

/** Louisiana CCTV cameras (LADOTD / 511la.org, ~336 statewide) — mirrors
 * `osiris-backend/src/app/api/cctv/louisiana.ts`. Same IBI 511 platform as [Ibi511]'s other
 * states, reusing its [Ibi511.buildQuery]/[Ibi511.parseWkt] — but LADOTD's own record shape and
 * name-selection order (location, then roadway, then the image's own description) differ
 * slightly from the shared loader's, so this keeps its own small paging/mapping logic rather
 * than forcing it through [Ibi511.loadCameras]. Keyless. */
object LouisianaCctvSource {
    private const val BASE = "https://511la.org"
    private const val PAGE_SIZE = 100
    private const val MAX_PAGES = 10
    private val BOUNDS = Ibi511.Bounds(minLat = 28.8, maxLat = 33.1, minLng = -94.2, maxLng = -88.6)
    private val HLS_REGEX = Regex("""\.m3u8(\?|$)""", RegexOption.IGNORE_CASE)

    @Serializable
    private data class Record(
        val id: Int,
        val roadway: String? = null,
        val location: String? = null,
        val latLng: Ibi511.LatLng? = null,
        val images: List<ImageInfo>? = null,
    )

    @Serializable
    private data class ImageInfo(
        val description: String? = null,
        val imageUrl: String? = null,
        val videoUrl: String? = null,
        val blocked: Boolean? = null,
        val disabled: Boolean? = null,
        val videoDisabled: Boolean? = null,
    )

    @Serializable
    private data class PageResponse(val data: List<Record> = emptyList(), val recordsTotal: Int = 0)

    private fun hlsUrl(url: String?): String? = url?.trim()?.takeIf { HLS_REGEX.containsMatchIn(it) }

    private fun mapRecord(rec: Record): CctvCamera? {
        val img = rec.images?.firstOrNull() ?: return null
        if (img.blocked == true || img.disabled == true) return null
        val (lat, lng) = Ibi511.parseWkt(rec.latLng?.geography?.wellKnownText) ?: return null
        if (lat < BOUNDS.minLat || lat > BOUNDS.maxLat || lng < BOUNDS.minLng || lng > BOUNDS.maxLng) return null

        val name = listOf(rec.location, rec.roadway, img.description)
            .map { it?.trim() }
            .firstOrNull { !it.isNullOrEmpty() && it != "N/A" }

        val snapshot = img.imageUrl?.let { "$BASE$it" }
        val video = if (img.videoDisabled == true) null else hlsUrl(img.videoUrl)
        if (snapshot == null && video == null) return null

        return CctvCamera(
            id = "ladotd-${rec.id}",
            lat = lat,
            lng = lng,
            name = name ?: "LADOTD Camera ${rec.id}",
            city = "Louisiana",
            country = "US",
            feedUrl = snapshot,
            streamUrl = video,
            source = "LADOTD",
        )
    }

    private suspend fun fetchPage(start: Int): Pair<List<Record>, Int> {
        val url = "$BASE/List/GetData/Cameras?query=${Ibi511.buildQuery(start, PAGE_SIZE)}&lang=en"
        val response = DirectHttp.getJson<PageResponse>(
            url,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest", "Accept" to "application/json"),
        )
        return response.data to response.recordsTotal
    }

    suspend fun fetch(): List<CctvCamera> = runCatching {
        val first = fetchPage(0)
        val seen = linkedMapOf<Int, CctvCamera>()
        fun ingest(rows: List<Record>) {
            for (rec in rows) mapRecord(rec)?.let { seen[rec.id] = it }
        }
        ingest(first.first)

        val starts = generateSequence(PAGE_SIZE) { it + PAGE_SIZE }
            .takeWhile { it < first.second && it < PAGE_SIZE * MAX_PAGES }
            .toList()
        starts.forEach { s -> runCatching { fetchPage(s) }.getOrNull()?.let { ingest(it.first) } }

        seen.values.toList()
    }.getOrDefault(emptyList())
}
