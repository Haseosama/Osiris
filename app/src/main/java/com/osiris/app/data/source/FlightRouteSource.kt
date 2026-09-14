package com.osiris.app.data.source

import com.osiris.app.data.model.Flight
import com.osiris.app.data.model.FlightAirport
import com.osiris.app.data.model.FlightRoute
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import java.time.Instant
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A flight's scheduled origin/destination/ETA, called directly from the phone — mirrors
 * `osiris-backend/src/app/api/flight-route/route.ts`. Three keyless sources run in parallel
 * (each bounded to [FETCH_TIMEOUT_MS]), the first successful one in adsbdb→hexdb→airplanes.live
 * order wins — not a true "whichever answers first" race (that needs cancellable network calls,
 * more plumbing than this on-demand, one-shot lookup is worth), but bounded to the same total
 * wall-clock cost since all three run concurrently.
 */
object FlightRouteSource {

    private const val FETCH_TIMEOUT_MS = 4000L

    suspend fun fetch(flight: Flight): FlightRoute? {
        val callsign = flight.callsign?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
        val icao24 = flight.icao24?.trim()?.lowercase().orEmpty()
        val currentLat = flight.lat
        val currentLng = flight.lng
        val speedKt = flight.speedKnots

        val route = coroutineScope {
            val adsbdb = async { withTimeoutOrNull(FETCH_TIMEOUT_MS) { runCatching { fromAdsbdb(callsign) }.getOrNull() } }
            val hexdb = async { withTimeoutOrNull(FETCH_TIMEOUT_MS) { runCatching { fromHexdb(callsign) }.getOrNull() } }
            val airplanesLive = async { withTimeoutOrNull(FETCH_TIMEOUT_MS) { runCatching { fromAirplanesLive(icao24, callsign) }.getOrNull() } }
            adsbdb.await() ?: hexdb.await() ?: airplanesLive.await()
        } ?: return FlightRoute(found = false)

        val (origin, destination) = route

        // Plausibility check, same thresholds as the backend: reject implausibly short routes,
        // and routes where the plane is nowhere near either endpoint (likely a bad route match).
        val routeDist = haversineKm(origin.lat, origin.lng, destination.lat, destination.lng)
        if (routeDist < 30) return FlightRoute(found = false)
        if (currentLat != 0.0 && currentLng != 0.0) {
            val distToOrigin = haversineKm(currentLat, currentLng, origin.lat, origin.lng)
            val distToDest = haversineKm(currentLat, currentLng, destination.lat, destination.lng)
            val maxReasonable = routeDist * 1.5
            if (distToOrigin > maxReasonable && distToDest > maxReasonable) return FlightRoute(found = false)
        }

        val arc = greatCirclePoints(origin.lat, origin.lng, destination.lat, destination.lng)
        val totalDistanceKm = round(routeDist).toInt()
        val times = if (currentLat != 0.0 && currentLng != 0.0) {
            estimateTimes(origin.lat, origin.lng, destination.lat, destination.lng, currentLat, currentLng, speedKt)
        } else {
            Triple(null, null, 0.0)
        }

        return FlightRoute(
            found = true,
            origin = origin,
            destination = destination,
            arc = arc,
            totalDistanceKm = totalDistanceKm,
            departureTime = times.first,
            arrivalTime = times.second,
            progress = times.third,
        )
    }

    // ── Math ──────────────────────────────────────────────────────────

    private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = (lat2 - lat1) * PI / 180
        val dLng = (lng2 - lng1) * PI / 180
        val a = sin(dLat / 2).let { it * it } +
            cos(lat1 * PI / 180) * cos(lat2 * PI / 180) * sin(dLng / 2).let { it * it }
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun greatCirclePoints(lat1: Double, lng1: Double, lat2: Double, lng2: Double, n: Int = 72): List<List<Double>> {
        fun toR(d: Double) = d * PI / 180
        fun toD(r: Double) = r * 180 / PI
        val phi1 = toR(lat1); val lambda1 = toR(lng1); val phi2 = toR(lat2); val lambda2 = toR(lng2)
        val d = 2 * asin(sqrt(sin((phi2 - phi1) / 2).let { it * it } + cos(phi1) * cos(phi2) * sin((lambda2 - lambda1) / 2).let { it * it }))
        if (d < 1e-10) return listOf(listOf(lng1, lat1), listOf(lng2, lat2))
        val pts = mutableListOf<List<Double>>()
        for (i in 0..n) {
            val f = i.toDouble() / n
            val a = sin((1 - f) * d) / sin(d)
            val b = sin(f * d) / sin(d)
            val x = a * cos(phi1) * cos(lambda1) + b * cos(phi2) * cos(lambda2)
            val y = a * cos(phi1) * sin(lambda1) + b * cos(phi2) * sin(lambda2)
            val z = a * sin(phi1) + b * sin(phi2)
            pts += listOf(toD(atan2(y, x)), toD(atan2(z, hypot(x, y))))
        }
        return pts
    }

    private fun estimateTimes(
        oLat: Double, oLng: Double, dLat: Double, dLng: Double,
        cLat: Double, cLng: Double, speedKt: Double?,
    ): Triple<String?, String?, Double> {
        val total = haversineKm(oLat, oLng, dLat, dLng)
        val done = haversineKm(oLat, oLng, cLat, cLng)
        val left = haversineKm(cLat, cLng, dLat, dLng)
        val progress = if (total > 0) min(1.0, max(0.0, done / total)) else 0.0
        val kmh = if (speedKt != null && speedKt > 50) speedKt * 1.852 else 800.0
        val now = System.currentTimeMillis()
        val departure = Instant.ofEpochMilli(now - ((done / kmh) * 3_600_000).toLong())
        val arrival = Instant.ofEpochMilli(now + ((left / kmh) * 3_600_000).toLong())
        return Triple(departure.toString(), arrival.toString(), progress)
    }

    // ── Airport resolution ───────────────────────────────────────────

    private data class RawAirport(
        val iata: String?, val icao: String?, val name: String?,
        val city: String?, val country: String?, val lat: Double?, val lng: Double?,
    )

    private fun resolveAirports(
        oCode: String?, dCode: String?,
        oData: RawAirport? = null, dData: RawAirport? = null,
    ): Pair<FlightAirport, FlightAirport>? {
        if (oCode.isNullOrBlank() || dCode.isNullOrBlank() || oCode == dCode) return null

        val origin = Airports.lookupByCode(oCode) ?: oData?.toFlightAirport(oCode)
        val dest = Airports.lookupByCode(dCode) ?: dData?.toFlightAirport(dCode)
        if (origin == null || dest == null) return null
        if (!origin.lat.isFinite() || !origin.lng.isFinite() || !dest.lat.isFinite() || !dest.lng.isFinite()) return null
        if (origin.lat == dest.lat && origin.lng == dest.lng) return null
        return origin to dest
    }

    private fun RawAirport.toFlightAirport(fallbackIcao: String): FlightAirport? {
        val la = lat ?: return null
        val ln = lng ?: return null
        return FlightAirport(iata.orEmpty(), icao ?: fallbackIcao, name ?: fallbackIcao, city.orEmpty(), country.orEmpty(), la, ln)
    }

    // ── Source 1: adsbdb ──────────────────────────────────────────────

    @Serializable
    private data class AdsbdbRouteResponse(val response: AdsbdbRouteWrapper? = null)
    @Serializable
    private data class AdsbdbRouteWrapper(val flightroute: AdsbdbFlightRoute? = null)
    @Serializable
    private data class AdsbdbFlightRoute(val origin: AdsbdbAirport? = null, val destination: AdsbdbAirport? = null)
    @Serializable
    private data class AdsbdbAirport(
        val icao_code: String? = null, val iata_code: String? = null,
        val name: String? = null, val municipality: String? = null,
        val country_iso_name: String? = null, val latitude: String? = null, val longitude: String? = null,
    ) {
        fun toRaw() = RawAirport(iata_code, icao_code, name, municipality, country_iso_name, latitude?.toDoubleOrNull(), longitude?.toDoubleOrNull())
    }

    private suspend fun fromAdsbdb(callsign: String): Pair<FlightAirport, FlightAirport>? {
        val data = DirectHttp.getJson<AdsbdbRouteResponse>("https://api.adsbdb.com/v0/callsign/${URLEncoder.encode(callsign, "UTF-8")}")
        val route = data.response?.flightroute ?: return null
        val origin = route.origin ?: return null
        val dest = route.destination ?: return null
        return resolveAirports(
            origin.icao_code ?: origin.iata_code,
            dest.icao_code ?: dest.iata_code,
            origin.toRaw(), dest.toRaw(),
        )
    }

    // ── Source 2: hexdb ───────────────────────────────────────────────

    @Serializable
    private data class HexdbRouteResponse(val route: String? = null, val origin: String? = null, val destination: String? = null)

    private suspend fun fromHexdb(callsign: String): Pair<FlightAirport, FlightAirport>? {
        val data = DirectHttp.getJson<HexdbRouteResponse>("https://hexdb.io/api/v1/route/callsign/${URLEncoder.encode(callsign, "UTF-8")}")
        val (oCode, dCode) = when {
            data.route?.contains("-") == true -> {
                val parts = data.route.split("-")
                parts.first().trim() to parts.last().trim()
            }
            data.origin != null && data.destination != null -> data.origin to data.destination
            else -> return null
        }
        return resolveAirports(oCode, dCode)
    }

    // ── Source 3: airplanes.live ──────────────────────────────────────

    @Serializable
    private data class AirplanesLiveResponse(val ac: List<AirplanesLiveAircraft> = emptyList())

    @Serializable
    private data class AirplanesLiveAircraft(
        val oDB: AirplanesLiveAirportDb? = null,
        val dDB: AirplanesLiveAirportDb? = null,
        val from: String? = null,
        val to: String? = null,
        val route: String? = null,
    )

    @Serializable
    private data class AirplanesLiveAirportDb(
        val icao: String? = null, val iata: String? = null, val name: String? = null,
        val city: String? = null, val country: String? = null, val lat: Double? = null, val lon: Double? = null,
    ) {
        fun toRaw() = RawAirport(iata, icao, name, city, country, lat, lon)
    }

    private suspend fun fromAirplanesLive(icao24: String, callsign: String): Pair<FlightAirport, FlightAirport>? {
        if (icao24.isBlank() && callsign.isBlank()) return null
        val url = if (icao24.isNotBlank()) {
            "https://api.airplanes.live/v2/hex/${URLEncoder.encode(icao24, "UTF-8")}"
        } else {
            "https://api.airplanes.live/v2/callsign/${URLEncoder.encode(callsign, "UTF-8")}"
        }
        val data = DirectHttp.getJson<AirplanesLiveResponse>(url)
        val ac = data.ac.firstOrNull() ?: return null

        return when {
            ac.oDB?.icao != null && ac.dDB?.icao != null ->
                resolveAirports(ac.oDB.icao, ac.dDB.icao, ac.oDB.toRaw(), ac.dDB.toRaw())
            ac.from != null && ac.to != null -> resolveAirports(ac.from, ac.to)
            ac.route?.contains("-") == true -> {
                val parts = ac.route.split("-")
                resolveAirports(parts.first().trim(), parts.last().trim())
            }
            else -> null
        }
    }
}
