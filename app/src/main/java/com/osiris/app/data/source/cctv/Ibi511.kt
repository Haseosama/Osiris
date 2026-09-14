package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.source.DirectHttp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import java.net.URLEncoder

/**
 * Shared loader for the IBI 511 traveler-information platform, run by several US state DOTs
 * behind different domains — mirrors `osiris-backend/src/app/api/cctv/ibi511.ts`. A DataTables
 * endpoint at `/List/GetData/Cameras` pages 100 rows at a time, each row's position as WKT.
 * Used by [FloridaCctvSource]/[GeorgiaCctvSource]/[NorthCarolinaCctvSource]/[ArizonaCctvSource];
 * [LouisianaCctvSource] runs the same platform but reuses only [buildQuery]/[parseWkt] since its
 * record shape and name-selection order differ slightly from the backend's shared loader.
 *
 * No on-failure cache here unlike the backend's `sourceCache` (an Android process is killed far
 * more often than a long-running server — see the satellites layer's own note on this trade-off)
 * — a short read (paging cut off by a throttled burst) just yields an empty list for that poll
 * rather than a stale-but-plausible partial one.
 */
object Ibi511 {
    private const val PAGE_SIZE = 100
    private const val MAX_PAGES = 60
    private const val CONCURRENCY = 10
    private val CODE_PREFIX = Regex("^[A-Z]{2,8}-?\\d*\\s*:\\s*")
    private val WKT_REGEX = Regex("""POINT\s*\(\s*(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)\s*\)""", RegexOption.IGNORE_CASE)
    private val HLS_REGEX = Regex("""\.m3u8(\?|$)""", RegexOption.IGNORE_CASE)

    data class Bounds(val minLat: Double, val maxLat: Double, val minLng: Double, val maxLng: Double)
    data class Source(val base: String, val idPrefix: String, val source: String, val state: String, val bounds: Bounds)

    fun buildQuery(start: Int, length: Int): String {
        val json = "{\"columns\":[{\"data\":null,\"name\":\"\"},{\"name\":\"sortOrder\",\"s\":true}," +
            "{\"name\":\"roadway\",\"s\":true},{\"data\":3,\"name\":\"\"}],\"order\":[{\"column\":1,\"dir\":\"asc\"}]," +
            "\"start\":$start,\"length\":$length,\"search\":{\"value\":\"\"}}"
        return URLEncoder.encode(json, "UTF-8")
    }

    /** Parses a `POINT (lng lat)` WKT string. Returns `lat to lng`, not WKT's own lng-first order. */
    fun parseWkt(wkt: String?): Pair<Double, Double>? {
        if (wkt.isNullOrBlank()) return null
        val m = WKT_REGEX.find(wkt) ?: return null
        val lng = m.groupValues[1].toDoubleOrNull() ?: return null
        val lat = m.groupValues[2].toDoubleOrNull() ?: return null
        return lat to lng
    }

    private fun readable(s: String) = s.contains(' ')

    private fun cameraLabel(rec: Record): String? {
        val loc = rec.location?.trim()?.replace(CODE_PREFIX, "")
        if (!loc.isNullOrEmpty() && loc != "N/A" && readable(loc)) return loc
        val road = rec.roadway?.trim()
        if (road.isNullOrEmpty() || road == "N/A") return loc?.takeIf { it != "N/A" }
        val dir = rec.direction?.trim()
        return if (!dir.isNullOrEmpty() && dir != "N/A") "$road $dir" else road
    }

    private fun cameraCity(rec: Record, state: String): String {
        val city = rec.city?.trim()
        if (!city.isNullOrEmpty() && city != "N/A") return city
        val county = rec.county?.trim()
        if (!county.isNullOrEmpty() && county != "N/A") return "$county County"
        return state
    }

    private fun hlsUrl(url: String?): String? {
        val trimmed = url?.trim()
        return trimmed?.takeIf { HLS_REGEX.containsMatchIn(it) }
    }

    private fun mapRecord(rec: Record, cfg: Source): CctvCamera? {
        val img = rec.images?.firstOrNull() ?: return null
        if (img.blocked == true || img.disabled == true) return null
        val (lat, lng) = parseWkt(rec.latLng?.geography?.wellKnownText) ?: return null
        val b = cfg.bounds
        if (lat < b.minLat || lat > b.maxLat || lng < b.minLng || lng > b.maxLng) return null

        val snapshot = img.imageUrl?.let { "${cfg.base}$it" }
        // `isVideoAuthRequired` means the playlist is gated behind a session only the platform's
        // own player can negotiate — several agencies set it on every row and answer 401 to
        // anyone else, so taking that URL would hand the player a stream that cannot open.
        val gated = img.isVideoAuthRequired == true
        val video = if (img.videoDisabled == true || gated) null else hlsUrl(img.videoUrl)
        if (snapshot == null && video == null) return null

        return CctvCamera(
            id = "${cfg.idPrefix}-${rec.id}",
            lat = lat,
            lng = lng,
            name = cameraLabel(rec) ?: "${cfg.source} Camera ${rec.id}",
            city = cameraCity(rec, cfg.state),
            country = "US",
            feedUrl = snapshot,
            streamUrl = video,
            source = cfg.source,
        )
    }

    private suspend fun fetchPage(cfg: Source, start: Int): Pair<List<Record>, Int> {
        val url = "${cfg.base}/List/GetData/Cameras?query=${buildQuery(start, PAGE_SIZE)}&lang=en"
        val response = DirectHttp.getJson<PageResponse>(
            url,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest", "Accept" to "application/json"),
        )
        return response.data to response.recordsTotal
    }

    suspend fun loadCameras(cfg: Source): List<CctvCamera> = coroutineScope {
        val first = runCatching { fetchPage(cfg, 0) }.getOrNull() ?: return@coroutineScope emptyList()
        val seen = linkedMapOf<Int, CctvCamera>()
        fun ingest(rows: List<Record>) {
            for (rec in rows) mapRecord(rec, cfg)?.let { seen[rec.id] = it }
        }
        ingest(first.first)
        val total = first.second

        val starts = generateSequence(PAGE_SIZE) { it + PAGE_SIZE }
            .takeWhile { it < total && it < PAGE_SIZE * MAX_PAGES }
            .toList()

        val failed = mutableListOf<Int>()
        starts.chunked(CONCURRENCY).forEach { batch ->
            val results = batch.map { s -> s to async { runCatching { fetchPage(cfg, s) } } }
                .map { (s, deferred) -> s to deferred.await() }
            results.forEach { (s, result) -> result.onSuccess { ingest(it.first) }.onFailure { failed += s } }
        }
        // These agencies throttle a burst rather than refuse it outright, so a page that failed
        // in a batch of ten usually succeeds on its own a moment later.
        if (failed.isNotEmpty()) {
            failed.map { s -> async { runCatching { fetchPage(cfg, s) } } }
                .map { it.await() }
                .forEach { it.getOrNull()?.let { page -> ingest(page.first) } }
        }

        val cams = seen.values.toList()
        // A short read is a failed refresh, not a smaller state — none of these feeds drops
        // rows on its own, so anything materially short of recordsTotal is pages not received.
        if (total > 0 && cams.size < total * 0.95) emptyList() else cams
    }

    @Serializable
    data class Record(
        val id: Int,
        val roadway: String? = null,
        val direction: String? = null,
        val location: String? = null,
        val city: String? = null,
        val county: String? = null,
        val latLng: LatLng? = null,
        val images: List<ImageInfo>? = null,
    )

    @Serializable data class LatLng(val geography: Geography? = null)
    @Serializable data class Geography(val wellKnownText: String? = null)

    @Serializable
    data class ImageInfo(
        val description: String? = null,
        val imageUrl: String? = null,
        val videoUrl: String? = null,
        val isVideoAuthRequired: Boolean? = null,
        val blocked: Boolean? = null,
        val disabled: Boolean? = null,
        val videoDisabled: Boolean? = null,
    )

    @Serializable
    data class PageResponse(val data: List<Record> = emptyList(), val recordsTotal: Int = 0)
}
