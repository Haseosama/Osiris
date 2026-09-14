package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.source.DirectHttp
import kotlinx.serialization.Serializable

/** Australia live traffic cameras (Live Traffic NSW), called directly from the phone — mirrors
 * `osiris-backend/src/app/api/cctv/australia.ts`. Keyless. */
object AustraliaCctvSource {

    suspend fun fetch(): List<CctvCamera> = runCatching {
        val events = DirectHttp.getJson<List<LiveTrafficEvent>>("https://www.livetraffic.com/datajson/all-feeds-web.json")
        events.filter { it.eventType == "liveCams" }.mapNotNull { event ->
            val lat = event.geometry?.coordinates?.getOrNull(1) ?: return@mapNotNull null
            val lng = event.geometry?.coordinates?.getOrNull(0) ?: return@mapNotNull null
            val feedUrl = event.properties?.href?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CctvCamera(
                id = event.path ?: return@mapNotNull null,
                lat = lat,
                lng = lng,
                name = event.properties.title ?: "Australia Camera",
                city = event.properties.region ?: "Australia",
                country = "Australia",
                feedUrl = feedUrl,
                source = "Live Traffic",
            )
        }
    }.getOrDefault(emptyList())

    @Serializable
    private data class LiveTrafficEvent(
        val path: String? = null,
        val eventType: String? = null,
        val geometry: Geometry? = null,
        val properties: EventProperties? = null,
    )

    @Serializable
    private data class Geometry(val coordinates: List<Double> = emptyList())

    @Serializable
    private data class EventProperties(val title: String? = null, val region: String? = null, val href: String? = null)
}
