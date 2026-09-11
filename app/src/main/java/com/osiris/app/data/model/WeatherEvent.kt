package com.osiris.app.data.model

import kotlinx.serialization.Serializable

/** Mirrors the JSON returned by GET /api/weather (NASA EONET + NWS-backed) on the Osiris backend. */
@Serializable
data class WeatherResponse(
    val events: List<WeatherEvent> = emptyList(),
    val total: Int = 0,
    val timestamp: String? = null,
)

@Serializable
data class WeatherEvent(
    val id: String? = null,
    val title: String? = null,
    val category: String? = null,
    val type: String? = null,
    val icon: String? = null,
    val severity: String? = null, // low | medium | high
    val lat: Double,
    val lng: Double,
    val date: String? = null,
    val expires: String? = null,
)
