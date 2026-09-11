package com.osiris.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the JSON returned by GET /api/news — despite the name, this is Osiris's geoparsed
 * Telegram OSINT feed (OSINTtechnical, Faytuks, Liveuamap, CyberKnow...), falling back to a
 * handful of RSS feeds only if Telegram blocks the backend's IP. [coords] is `[lat, lng]`
 * (matching the route's own KEYWORD_COORDS table) — the reverse of GeoJSON's `[lng, lat]`.
 */
@Serializable
data class OsintResponse(
    val news: List<OsintPost> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class OsintPost(
    val id: String,
    val title: String,
    val description: String? = null,
    val link: String? = null,
    val published: String? = null,
    val source: String? = null,
    @SerialName("risk_score") val riskScore: Int = 1,
    val coords: List<Double>? = null,
    @SerialName("coords_default") val coordsDefault: Boolean = true,
    @SerialName("machine_assessment") val machineAssessment: String? = null,
) {
    val lat: Double? get() = coords?.getOrNull(0)
    val lng: Double? get() = coords?.getOrNull(1)
}
