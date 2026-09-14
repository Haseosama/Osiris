package com.osiris.app.data.repository

import com.osiris.app.data.model.AircraftDetail
import com.osiris.app.data.model.Flight
import com.osiris.app.data.model.FlightMarker
import com.osiris.app.data.model.FlightRoute
import com.osiris.app.data.model.toMarkers
import com.osiris.app.data.remote.NetworkModule
import kotlinx.serialization.json.Json
import java.net.URLEncoder

class FlightsRepository {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(baseUrl: String): List<FlightMarker> =
        NetworkModule.apiFor(baseUrl).flights().toMarkers()

    /** One flight's scheduled origin/destination/ETA/progress, à la FlightRadar24 — see
     * [FlightRoute]. Returns null on any failure or when the backend simply couldn't resolve a
     * route (`found: false`), which is common for GA/private traffic with no filed route. */
    suspend fun fetchRoute(baseUrl: String, flight: Flight): FlightRoute? = runCatching {
        val callsign = flight.callsign?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val url = buildString {
            append("api/flight-route?callsign=").append(URLEncoder.encode(callsign, "UTF-8"))
            flight.icao24?.takeIf { it.isNotBlank() }?.let { append("&icao24=").append(URLEncoder.encode(it, "UTF-8")) }
            append("&lat=").append(flight.lat)
            append("&lng=").append(flight.lng)
            flight.speedKnots?.let { append("&speed=").append(it) }
        }
        val response = NetworkModule.apiFor(baseUrl).raw(url)
        val body = response.body()?.string() ?: return null
        json.decodeFromString<FlightRoute>(body).takeIf { it.found }
    }.getOrNull()

    /** This airframe's identity (registration/type/operator) and actual flown track for the
     * current leg — see [AircraftDetail]. Returns null on any failure, including a 404 for an
     * icao24 adsb.lol has no trace history for (common for aircraft first seen minutes ago). */
    suspend fun fetchAircraftDetail(baseUrl: String, icao24: String): AircraftDetail? = runCatching {
        val hex = icao24.trim().lowercase().takeIf { it.isNotBlank() } ?: return null
        val response = NetworkModule.apiFor(baseUrl).raw("api/aircraft?icao24=$hex")
        if (!response.isSuccessful) return null
        val body = response.body()?.string() ?: return null
        json.decodeFromString<AircraftDetail>(body)
    }.getOrNull()
}
