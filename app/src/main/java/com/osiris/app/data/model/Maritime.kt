package com.osiris.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors the JSON returned by GET /api/maritime (static ports/chokepoints + live AIS ships). */
@Serializable
data class MaritimeResponse(
    val ports: List<Port> = emptyList(),
    val chokepoints: List<Chokepoint> = emptyList(),
    val ships: List<Ship> = emptyList(),
    @SerialName("total_ports") val totalPorts: Int = 0,
    @SerialName("total_chokepoints") val totalChokepoints: Int = 0,
    @SerialName("total_ships") val totalShips: Int = 0,
)

@Serializable
data class Port(
    val name: String,
    val country: String? = null,
    val lat: Double,
    val lng: Double,
    val type: String, // container | energy | naval
    val volume: String? = null,
    val congestion: String? = null,
    /** Global container-traffic rank — container ports only, and only the busier ones. */
    val rank: Int? = null,
    /** Naval bases only — which fleet is based there, e.g. "US Pacific Fleet". */
    val fleet: String? = null,
    /** Estimated ship dwell time, derived server-side from nearby AIS traffic — container ports
     * with enough live ship data only, e.g. "1-2 Days". */
    @SerialName("dwell_time") val dwellTime: String? = null,
)

@Serializable
data class Chokepoint(
    val name: String,
    val lat: Double,
    val lng: Double,
    val traffic: String? = null,
    val risk: String, // LOW | MODERATE | ELEVATED | HIGH | CRITICAL
)

/** Only populated when the backend has AIS_API_KEY configured — otherwise always empty. Beyond
 * the core position fields, [navStatus]/[callSign]/[imo]/[lengthM]/[widthM]/[draughtM]/[etaText]
 * all come from the same AIS stream ([com.osiris.app.data.source.AisStreamSource]) — the raw
 * PositionReport/ShipStaticData messages already carry them, they just weren't captured before. */
@Serializable
data class Ship(
    val id: Long? = null,
    val mmsi: Long? = null,
    val lat: Double,
    val lng: Double,
    val speed: Double? = null,
    val heading: Double? = null,
    val name: String? = null,
    val destination: String? = null,
    val type: String? = null, // cargo | tanker | military
    /** ITU-R M.1371 navigational status code (0-15) — see [com.osiris.app.data.source.AisStreamSource.navStatusLabel].
     * Explains a lot of "why isn't this ship moving": 1/5/6 are anchored/moored/aground. */
    val navStatus: Int? = null,
    val callSign: String? = null,
    val imo: Long? = null,
    val lengthM: Double? = null,
    val widthM: Double? = null,
    val draughtM: Double? = null,
    /** Pre-formatted since raw AIS ETA has no year field (just month/day/hour/minute) — see
     * [com.osiris.app.data.source.AisStreamSource.formatEta]. */
    val etaText: String? = null,
)
