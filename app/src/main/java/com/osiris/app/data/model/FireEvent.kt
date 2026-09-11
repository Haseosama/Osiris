package com.osiris.app.data.model

import kotlinx.serialization.Serializable

/** Mirrors the JSON returned by GET /api/fires (NASA FIRMS-backed) on the Osiris backend. */
@Serializable
data class FiresResponse(
    val fires: List<FireEvent> = emptyList(),
    val total: Int = 0,
    val source: String? = null,
    val timestamp: String? = null,
)

@Serializable
data class FireEvent(
    val lat: Double,
    val lng: Double,
    val brightness: Double? = null,
    val confidence: String? = null,
    val date: String? = null,
    val time: String? = null,
    val frp: Double? = null,
    val title: String? = null,
    val type: String? = null,
)
