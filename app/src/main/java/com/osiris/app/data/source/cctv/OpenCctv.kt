package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.remote.NetworkModule
import com.osiris.app.data.source.DirectHttp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray

/**
 * Shared loader for the OpenCCTV directory (https://opencctv.org, ~145,000 cameras worldwide) —
 * mirrors `osiris-backend/src/app/api/cctv/opencctv.ts`. Fills the parts of Asia no
 * traffic-authority feed covers: South Korea, Indonesia, Vietnam, the Philippines. Two calls:
 * a 7.3 MB marker index (id/lat/lng as three parallel arrays, no filtering support at all — the
 * whole reason this thins locally) and a batched POST for full records (the server caps a batch
 * at 50 rows regardless of how many ids are sent). Used by [EastAsiaCctvSource]/
 * [SeAsiaCctvSource]/[WestAsiaCctvSource], one per sub-region so a viewport over Jakarta doesn't
 * also pay for Japan.
 */
object OpenCctv {
    private const val MARKERS_URL = "https://opencctv.org/api/cameras/markers"
    private const val BATCH_URL = "https://opencctv.org/api/cameras/batch"
    private const val BATCH_SIZE = 50
    private const val BATCH_CONCURRENCY = 8
    private const val INDEX_TTL_MS = 60 * 60_000L

    data class Bounds(val minLat: Double, val maxLat: Double, val minLng: Double, val maxLng: Double)

    @Serializable
    private data class MarkerIndex(val ids: List<String>? = null, val lats: List<Double>? = null, val lngs: List<Double>? = null)

    @Serializable
    private data class Record(
        val id: String? = null,
        val name: String? = null,
        val city: String? = null,
        val country: String? = null,
        val lat: Double? = null,
        val lng: Double? = null,
        val feed_url: String? = null,
        val feed_type: String? = null,
        val source: String? = null,
        val active: Int? = null,
        val cache_buster_breaks_url: Boolean? = null,
    )

    private var cachedIndex: Triple<Long, List<String>, Pair<List<Double>, List<Double>>>? = null

    private suspend fun loadIndex(): Triple<List<String>, List<Double>, List<Double>> {
        cachedIndex?.let { (at, ids, latLng) ->
            if (System.currentTimeMillis() - at < INDEX_TTL_MS) return Triple(ids, latLng.first, latLng.second)
        }
        val index = DirectHttp.getJson<MarkerIndex>(
            MARKERS_URL,
            headers = mapOf("Accept" to "application/json", "Referer" to "https://opencctv.org/"),
        )
        val ids = index.ids.orEmpty()
        val lats = index.lats.orEmpty()
        val lngs = index.lngs.orEmpty()
        cachedIndex = Triple(System.currentTimeMillis(), ids, lats to lngs)
        return Triple(ids, lats, lngs)
    }

    /** Thins [items] down to [cap] by walking at a fixed stride — the index is ordered by id,
     * which groups cameras by operator and so by place, so taking the first N would return one
     * city and call it a region. */
    private fun <T> sample(items: List<T>, cap: Int): List<T> {
        if (items.size <= cap) return items
        val stride = items.size.toDouble() / cap
        val out = mutableListOf<T>()
        var i = 0.0
        while (out.size < cap && i.toInt() < items.size) {
            out += items[i.toInt()]
            i += stride
        }
        return out
    }

    private fun streamKind(feedType: String?): String? = when (feedType?.lowercase()) {
        "m3u8", "hls" -> "hls"
        "image" -> "jpg"
        // mjpeg/iframe: no player equivalent here (see FranceCctvSource's doc comment on
        // iframe), so these fall through to null and get dropped, same as an unknown type.
        else -> null
    }

    private fun mapRecord(rec: Record): CctvCamera? {
        val id = rec.id ?: return null
        if (rec.active == 0) return null
        val url = rec.feed_url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val kind = streamKind(rec.feed_type) ?: return null
        val lat = rec.lat ?: return null
        val lng = rec.lng ?: return null
        // A snapshot tile re-requests with a cache-busting query string on every refresh; where
        // the source recorded that breaks the URL, it would turn into a broken box the moment
        // it refreshed, so it never gets one — simplest fix here is to just skip it.
        if (kind == "jpg" && rec.cache_buster_breaks_url == true) return null

        return CctvCamera(
            id = "occ-$id",
            lat = lat,
            lng = lng,
            name = rec.name?.trim()?.takeIf { it.isNotEmpty() } ?: rec.city?.trim()?.takeIf { it.isNotEmpty() } ?: "Camera",
            city = rec.city?.trim().orEmpty(),
            country = rec.country?.trim().orEmpty(),
            feedUrl = if (kind == "jpg") url else null,
            streamUrl = if (kind != "jpg") url else null,
            source = rec.source?.trim()?.takeIf { it.isNotEmpty() }?.let { "OpenCCTV / $it" } ?: "OpenCCTV",
        )
    }

    private suspend fun fetchBatch(ids: List<String>): List<Record> = runCatching {
        val payload = NetworkModule.json.encodeToString(
            JsonElement.serializer(),
            buildJsonObject { putJsonArray("ids") { ids.forEach { add(JsonPrimitive(it)) } } },
        )
        val text = DirectHttp.postJson(
            BATCH_URL,
            payload,
            headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json", "Referer" to "https://opencctv.org/"),
        )
        NetworkModule.json.decodeFromString(ListSerializer(Record.serializer()), text)
    }.getOrDefault(emptyList())

    suspend fun loadRegion(bounds: Bounds, cap: Int): List<CctvCamera> = runCatching {
        val (ids, lats, lngs) = loadIndex()
        val inRegion = mutableListOf<String>()
        for (i in ids.indices) {
            val lat = lats.getOrNull(i) ?: continue
            val lng = lngs.getOrNull(i) ?: continue
            if (lat > bounds.minLat && lat < bounds.maxLat && lng > bounds.minLng && lng < bounds.maxLng) {
                inRegion += ids[i]
            }
        }

        val wanted = sample(inRegion, cap)
        val chunks = wanted.chunked(BATCH_SIZE)

        coroutineScope {
            chunks.chunked(BATCH_CONCURRENCY).flatMap { batchGroup ->
                batchGroup.map { chunk -> async { fetchBatch(chunk) } }.map { it.await() }
            }
        }.flatten().mapNotNull(::mapRecord).distinctBy { it.id }
    }.getOrDefault(emptyList())
}
