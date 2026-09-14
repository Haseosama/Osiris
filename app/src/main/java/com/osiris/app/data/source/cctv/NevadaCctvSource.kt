package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.source.DirectHttp
import kotlinx.serialization.Serializable

/** Nevada CCTV cameras (NDOT, ~600 statewide) — mirrors
 * `osiris-backend/src/app/api/cctv/nevada.ts`. Same IBI 511 platform as [UtahCctvSource], but
 * (unlike Utah) most rows carry their own full HLS playlist address directly, so this keeps its
 * own small record mapping reusing only [Ibi511.buildQuery]/[Ibi511.parseWkt] — same pattern as
 * [LouisianaCctvSource]. Keyless. */
object NevadaCctvSource {
    private const val BASE = "https://www.nvroads.com"
    private const val PAGE_SIZE = 100
    private const val MAX_PAGES = 20
    private val BOUNDS = Ibi511.Bounds(minLat = 34.9, maxLat = 42.1, minLng = -120.1, maxLng = -113.9)

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

    private fun mapRecord(rec: Record): CctvCamera? {
        val img = rec.images?.firstOrNull() ?: return null
        if (img.blocked == true || img.disabled == true) return null
        val (lat, lng) = Ibi511.parseWkt(rec.latLng?.geography?.wellKnownText) ?: return null
        if (lat < BOUNDS.minLat || lat > BOUNDS.maxLat || lng < BOUNDS.minLng || lng > BOUNDS.maxLng) return null

        // `location` is "N/A" on a good third of these and `roadway` repeats the picture's own
        // description, so the image caption is the best label — same order the backend uses.
        val name = listOf(img.description, rec.location, rec.roadway)
            .map { it?.trim() }
            .firstOrNull { !it.isNullOrEmpty() && it != "N/A" }

        val snapshot = img.imageUrl?.let { "$BASE$it" }
        val video = if (img.videoDisabled == true) null else img.videoUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (snapshot == null && video == null) return null

        return CctvCamera(
            id = "ndot-${rec.id}",
            lat = lat,
            lng = lng,
            name = name ?: "NDOT Camera ${rec.id}",
            city = "Nevada",
            country = "US",
            feedUrl = snapshot,
            streamUrl = video,
            source = "NDOT",
        )
    }

    private suspend fun fetchPage(start: Int): Pair<List<Record>, Int> {
        val url = "$BASE/List/GetData/Cameras?query=${Ibi511.buildQuery(start, PAGE_SIZE)}&lang=en-US"
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
