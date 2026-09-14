package com.osiris.app.data.repository

import com.osiris.app.data.model.Satellite
import com.osiris.app.data.model.SatelliteOrbit
import com.osiris.app.data.remote.NetworkModule
import kotlinx.serialization.json.Json

class SatellitesRepository {
    private val json = Json { ignoreUnknownKeys = true }

    /** The full catalogue (~18-19k, mostly Starlink + tracked debris) — no cap here any more.
     * [MapViewModel] filters by category client-side based on what the user picked in Réglages,
     * which is what actually keeps the animated/rendered set to something a phone can handle,
     * rather than a fixed backend-side number the user can't adjust. */
    suspend fun fetch(baseUrl: String): List<Satellite> =
        NetworkModule.apiFor(baseUrl).satellites().satellites

    /** One satellite's orbital period, fetched on tap rather than bundled into every poll — see
     * [SatelliteOrbit]. [epochMs] anchors the propagation to the moment the marker's position on
     * screen actually represents (the backend needs this or it silently draws/measures a slightly
     * wrong orbit — see the orbit route's own doc comment on why "now" alone isn't right there). */
    suspend fun fetchOrbitPeriod(baseUrl: String, noradId: String, epochMs: Long): Double? = runCatching {
        val response = NetworkModule.apiFor(baseUrl).raw("api/satellites/orbit?id=$noradId&t=$epochMs")
        val body = response.body()?.string() ?: return null
        json.decodeFromString<SatelliteOrbit>(body).periodMinutes
    }.getOrNull()
}
