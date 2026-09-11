package com.osiris.app.data.model

import kotlinx.serialization.Serializable

/** Mirrors the JSON returned by GET /api/conflicts (GDELT + static zones) on the Osiris backend. */
@Serializable
data class ConflictsResponse(
    val zones: List<ConflictZone> = emptyList(),
    val totalZones: Int = 0,
    val activeWarzones: Int = 0,
)

@Serializable
data class ConflictZone(
    val id: String,
    val label: String,
    val severity: String, // war | high | elevated | moderate
    val lat: Double,
    val lng: Double,
    val description: String? = null,
    val sourceUrl: String? = null,
    val region: String? = null,
    val eventCount: Int = 0,
    val lastUpdated: String? = null,
)
