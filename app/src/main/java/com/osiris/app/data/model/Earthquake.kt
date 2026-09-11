package com.osiris.app.data.model

import kotlinx.serialization.Serializable

/** Mirrors the JSON returned by GET /api/earthquakes (USGS-backed) on the Osiris backend. */
@Serializable
data class EarthquakesResponse(
    val earthquakes: List<Earthquake> = emptyList(),
    val total: Int = 0,
    val timestamp: String? = null,
)

@Serializable
data class Earthquake(
    val id: String? = null,
    val lat: Double,
    val lng: Double,
    val depth: Double? = null,
    val magnitude: Double? = null,
    val place: String? = null,
    val time: Long? = null,
    val url: String? = null,
    val tsunami: Int? = null,
    val type: String? = null,
)
