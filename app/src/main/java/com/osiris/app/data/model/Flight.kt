package com.osiris.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors the JSON returned by GET /api/flights on the Osiris backend. */
@Serializable
data class FlightsResponse(
    @SerialName("commercial_flights") val commercialFlights: List<Flight> = emptyList(),
    @SerialName("private_flights") val privateFlights: List<Flight> = emptyList(),
    @SerialName("private_jets") val privateJets: List<Flight> = emptyList(),
    @SerialName("military_flights") val militaryFlights: List<Flight> = emptyList(),
    val total: Int = 0,
    val source: String? = null,
)

@Serializable
data class Flight(
    val callsign: String? = null,
    val lat: Double,
    val lng: Double,
    val alt: Double? = null,
    val heading: Double? = null,
    @SerialName("speed_knots") val speedKnots: Double? = null,
    val model: String? = null,
    val icao24: String? = null,
    val registration: String? = null,
    val squawk: String? = null,
    @SerialName("airline_code") val airlineCode: String? = null,
    @SerialName("aircraft_category") val aircraftCategory: String? = null,
    val grounded: Boolean = false,
    @SerialName("vertical_rate_fpm") val verticalRateFpm: Double? = null,
)

@Serializable
enum class FlightCategory { COMMERCIAL, PRIVATE, JET, MILITARY }

@Serializable
data class FlightMarker(val flight: Flight, val category: FlightCategory)

fun FlightsResponse.toMarkers(): List<FlightMarker> =
    commercialFlights.map { FlightMarker(it, FlightCategory.COMMERCIAL) } +
        privateFlights.map { FlightMarker(it, FlightCategory.PRIVATE) } +
        privateJets.map { FlightMarker(it, FlightCategory.JET) } +
        militaryFlights.map { FlightMarker(it, FlightCategory.MILITARY) }

/** Mirrors GET /api/flight-route?callsign=&icao24=&lat=&lng=&speed= — fetched on demand when a
 * flight is tapped (like [SatelliteOrbit]), not bundled into the main poll: it races three
 * external route-lookup sources per callsign, too slow/heavy to do for every flight on screen.
 * [arc] is a 72-point great-circle path as `[lng, lat]` pairs, GeoJSON order already. */
@Serializable
data class FlightRoute(
    val found: Boolean = false,
    val origin: FlightAirport? = null,
    val destination: FlightAirport? = null,
    val arc: List<List<Double>> = emptyList(),
    val totalDistanceKm: Int? = null,
    val departureTime: String? = null,
    val arrivalTime: String? = null,
    val progress: Double? = null,
)

@Serializable
data class FlightAirport(
    val iata: String? = null,
    val icao: String? = null,
    val name: String? = null,
    val city: String? = null,
    val country: String? = null,
    val lat: Double,
    val lng: Double,
)

/** Mirrors GET /api/aircraft?icao24= — fetched on demand alongside [FlightRoute], also not
 * bundled into the main poll. Unlike [FlightRoute]'s synthetic great-circle [FlightRoute.arc],
 * [track] is the aircraft's *actual flown path* read off adsb.lol's trace history for this
 * specific airframe — real turns, holds and diversions included, not just start/end points.
 * Falls back to [FlightRoute.arc] when a trace isn't available (military/unlisted aircraft
 * mostly): see [com.osiris.app.map.MapViewModel.selectFlight]. */
@Serializable
data class AircraftDetail(
    val icao24: String? = null,
    val registration: String? = null,
    val typeCode: String? = null,
    val model: String? = null,
    val operator: String? = null,
    val track: List<List<Double>> = emptyList(),
    val altitudes: List<Double?> = emptyList(),
    val points: Int = 0,
    val departure: FlightAirport? = null,
    val arrival: FlightAirport? = null,
    val source: String? = null,
)
