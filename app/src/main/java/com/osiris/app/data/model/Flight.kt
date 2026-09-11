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
