package com.osiris.app.data.model

import kotlinx.serialization.Serializable

/**
 * Mirrors the JSON returned by GET /api/satellites. Orbit propagation (SGP4/SDP4) already
 * happens server-side — this client only ever sees a plain lat/lng/alt snapshot.
 */
@Serializable
data class SatellitesResponse(
    val satellites: List<Satellite> = emptyList(),
    val total: Int = 0,
    val source: String? = null,
)

@Serializable
data class Satellite(
    val name: String,
    val lat: Double,
    val lng: Double,
    val alt: Double? = null,
    val mission: String? = null,
    val category: String? = null, // comms | navigation | earth_obs | military | science | other
    val noradId: String? = null,
)
