package com.osiris.app.data.repository

import com.osiris.app.data.model.AircraftDetail
import com.osiris.app.data.model.Flight
import com.osiris.app.data.model.FlightMarker
import com.osiris.app.data.model.FlightRoute
import com.osiris.app.data.model.toMarkers
import com.osiris.app.data.remote.NetworkModule
import com.osiris.app.data.source.AdsbAircraftSource
import com.osiris.app.data.source.FlightRouteSource

class FlightsRepository {

    suspend fun fetch(baseUrl: String): List<FlightMarker> =
        NetworkModule.apiFor(baseUrl).flights().toMarkers()

    /** One flight's scheduled origin/destination/ETA/progress, à la FlightRadar24 — see
     * [FlightRoute]. Called directly against adsbdb/hexdb/airplanes.live (see
     * [FlightRouteSource]) — no backend involved. Returns null on any failure or when none of
     * the three sources could resolve a route, which is common for GA/private traffic with no
     * filed route; `baseUrl` is unused, kept only so call sites don't need to change. */
    suspend fun fetchRoute(baseUrl: String, flight: Flight): FlightRoute? =
        runCatching { FlightRouteSource.fetch(flight) }.getOrNull()?.takeIf { it.found }

    /** This airframe's identity (registration/type/operator) and actual flown track for the
     * current leg — see [AircraftDetail]. Called directly against adsb.lol/adsbdb (see
     * [AdsbAircraftSource]) — no backend involved. Returns null on any failure, including no
     * trace history for an icao24 (common for aircraft first seen minutes ago); `baseUrl` is
     * unused, kept only so call sites don't need to change. */
    suspend fun fetchAircraftDetail(baseUrl: String, icao24: String): AircraftDetail? =
        runCatching { AdsbAircraftSource.fetch(icao24) }.getOrNull()
}
