package com.osiris.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors the JSON returned by GET /api/traffic — TomTom incident details for a fixed set of
 * French metro/highway hubs (see the backend route for why it's hub-scoped rather than
 * nationwide: TomTom rejects any bbox over 10,000km², and mainland France is ~550,000km²). */
@Serializable
data class TrafficResponse(
    val incidents: List<TrafficIncident> = emptyList(),
    val total: Int = 0,
    val source: String? = null,
)

@Serializable
data class TrafficIncident(
    val id: String,
    val lat: Double,
    val lng: Double,
    /** French label already resolved server-side from TomTom's numeric category — "route
     * fermée", "travaux", "bouchon", "accident"... */
    val category: String,
    @SerialName("icon_category") val iconCategory: Int? = null,
    /** 0=inconnu, 1=mineur, 2=modéré, 3=majeur, 4=indéfini (généralement une fermeture). */
    val magnitude: Int? = null,
    val description: String? = null,
    val from: String? = null,
    val to: String? = null,
    val road: String? = null,
    @SerialName("length_m") val lengthM: Double? = null,
    @SerialName("start_time") val startTime: String? = null,
    /** The affected road segment, `[lng, lat]` pairs in order — TomTom's own LineString, passed
     * through by the backend unchanged. [lat]/[lng] above are just this line's midpoint. */
    val geometry: List<List<Double>>? = null,
)
