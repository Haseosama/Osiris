package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.source.DirectHttp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable

/** Utah CCTV cameras (UDOT Traffic, ~2,000 statewide) — mirrors
 * `osiris-backend/src/app/api/cctv/utah.ts`. Same IBI 511 platform as [Ibi511]'s states, but
 * UDOT's own frame URL is just `{base}/map/Cctv/{id}` (no per-camera field for it, unlike
 * Florida/Georgia/etc.) and there's no HLS here at all — every camera is a plain snapshot — so
 * this keeps its own small paging loop reusing only [Ibi511.buildQuery]/[Ibi511.parseWkt],
 * same pattern as [LouisianaCctvSource]/[NevadaCctvSource]. Keyless. */
object UtahCctvSource {
    private const val BASE = "https://prod-ut.ibi511.com"
    private const val PAGE_SIZE = 100
    private const val MAX_PAGES = 40
    private val BOUNDS = Ibi511.Bounds(minLat = 36.9, maxLat = 42.1, minLng = -114.2, maxLng = -108.9)

    @Serializable
    private data class Record(
        val id: Int,
        val location: String? = null,
        val roadway: String? = null,
        val latLng: Ibi511.LatLng? = null,
        val images: List<ImageInfo>? = null,
    )

    @Serializable
    private data class ImageInfo(val blocked: Boolean? = null, val disabled: Boolean? = null)

    @Serializable
    private data class PageResponse(val data: List<Record> = emptyList(), val recordsTotal: Int = 0)

    private fun mapRecord(rec: Record): CctvCamera? {
        val img = rec.images?.firstOrNull() ?: return null
        if (img.blocked == true || img.disabled == true) return null
        val (lat, lng) = Ibi511.parseWkt(rec.latLng?.geography?.wellKnownText) ?: return null
        if (lat < BOUNDS.minLat || lat > BOUNDS.maxLat || lng < BOUNDS.minLng || lng > BOUNDS.maxLng) return null

        return CctvCamera(
            id = "udot-${rec.id}",
            lat = lat,
            lng = lng,
            name = rec.location?.trim()?.takeIf { it.isNotEmpty() }
                ?: rec.roadway?.trim()?.takeIf { it.isNotEmpty() }
                ?: "UDOT Traffic Camera",
            city = "Utah",
            country = "US",
            feedUrl = "$BASE/map/Cctv/${rec.id}",
            source = "UDOT",
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
        coroutineScope {
            val first = fetchPage(0)
            val seen = linkedMapOf<Int, CctvCamera>()
            fun ingest(rows: List<Record>) {
                for (rec in rows) mapRecord(rec)?.let { seen[rec.id] = it }
            }
            ingest(first.first)

            val starts = generateSequence(PAGE_SIZE) { it + PAGE_SIZE }
                .takeWhile { it < first.second && it < PAGE_SIZE * MAX_PAGES }
                .toList()
            starts.map { s -> async { runCatching { fetchPage(s) } } }
                .map { it.await() }
                .forEach { it.getOrNull()?.let { page -> ingest(page.first) } }

            seen.values.toList()
        }
    }.getOrDefault(emptyList())
}
