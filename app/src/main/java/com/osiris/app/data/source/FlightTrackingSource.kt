package com.osiris.app.data.source

import com.osiris.app.BuildConfig
import com.osiris.app.data.model.Flight
import com.osiris.app.data.model.FlightsResponse
import com.osiris.app.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import kotlin.math.roundToInt

/**
 * Live flight tracking via OpenSky (authenticated when keys are set, else anonymous) with
 * adsb.fi as the military feed and last-resort regional fallback, called directly from the
 * phone — mirrors `osiris-backend/src/app/api/flights/route.ts`. The backend's whole point
 * (a process-lifetime cache/snapshot/cooldown/token) exists because OpenSky's budget is a
 * *daily* quota, not a per-request one — the same stateful singleton shape is what makes that
 * budget last on a single device too, so it's kept as-is rather than simplified away.
 */
object FlightTrackingSource {

    private const val ADSBFI_BASE = "https://opendata.adsb.fi/api/v2"
    private const val ADSB_MAX_DIST = 250
    private const val ADSBFI_GAP_MS = 1100L
    private const val CACHE_TTL_MS = 90_000L
    private const val OPENSKY_COOLDOWN_MS = 15 * 60_000L

    private val json = Json { ignoreUnknownKeys = true }
    private val fetchMutex = Mutex()

    @Volatile private var cachedData: FlightsResponse? = null
    @Volatile private var lastFetchTime = 0L

    @Volatile private var osToken: String? = null
    @Volatile private var osTokenExpiry = 0L
    @Volatile private var osSnapshot: List<RawAircraft> = emptyList()
    @Volatile private var osSnapshotTime = 0L
    @Volatile private var openSkyCooldownUntil = 0L

    private fun hasOpenSkyCreds() = BuildConfig.OPENSKY_CLIENT_ID.isNotBlank() && BuildConfig.OPENSKY_CLIENT_SECRET.isNotBlank()
    private fun openSkyInterval() = if (hasOpenSkyCreds()) 90_000L else 900_000L

    suspend fun fetch(): FlightsResponse {
        val now = System.currentTimeMillis()
        cachedData?.let { if (now - lastFetchTime < CACHE_TTL_MS) return it }

        return fetchMutex.withLock {
            val now2 = System.currentTimeMillis()
            cachedData?.let { if (now2 - lastFetchTime < CACHE_TTL_MS) return@withLock it }

            val data = runCatching { buildSnapshot() }.getOrElse { cachedData ?: return@withLock FlightsResponse() }
            cachedData = data
            lastFetchTime = System.currentTimeMillis()
            data
        }
    }

    // ── Raw aircraft, normalized shape shared by adsb.fi and OpenSky ──

    private data class RawAircraft(
        val hex: String?, val flight: String?, val t: String?, val dbFlags: Int?,
        val lat: Double?, val lon: Double?, val altBaroFt: Double?,
        val gs: Double?, val track: Double?, val categoryOs: Int?, val r: String?,
        val squawk: String?, val baroRate: Double?, val geomRate: Double?, val nacP: Int?,
    )

    @Serializable
    private data class AdsbFiResponse(val ac: List<AdsbFiAircraft> = emptyList())

    @Serializable
    private data class AdsbFiAircraft(
        val hex: String? = null,
        val flight: String? = null,
        val t: String? = null,
        val dbFlags: Int? = null,
        val lat: Double? = null,
        val lon: Double? = null,
        val alt_baro: JsonElement? = null,
        val gs: Double? = null,
        val track: Double? = null,
        val r: String? = null,
        val squawk: String? = null,
        val baro_rate: Double? = null,
        val geom_rate: Double? = null,
        val nac_p: Int? = null,
    ) {
        fun toRaw(): RawAircraft {
            val altNum = (alt_baro as? JsonPrimitive)?.doubleOrNull
            return RawAircraft(
                hex = hex, flight = flight, t = t, dbFlags = dbFlags, lat = lat, lon = lon,
                altBaroFt = altNum,
                gs = gs, track = track, categoryOs = null, r = r, squawk = squawk,
                baroRate = baro_rate, geomRate = geom_rate, nacP = nac_p,
            )
        }
    }

    @Serializable
    private data class OpenSkyResponse(val states: List<JsonArray> = emptyList())

    private fun openSkyStateToRaw(s: JsonArray): RawAircraft? {
        fun num(i: Int): Double? = s.getOrNull(i)?.let { (it as? JsonPrimitive)?.doubleOrNull }
        fun str(i: Int): String? = s.getOrNull(i)?.let { (it as? JsonPrimitive)?.content }
        val altBaroM = num(7)
        return RawAircraft(
            hex = str(0),
            flight = str(1)?.trim(),
            t = null,
            dbFlags = null,
            lon = num(5),
            lat = num(6),
            altBaroFt = altBaroM?.let { it * 3.28084 },
            gs = num(9)?.let { it * 1.94384 },
            track = num(10),
            categoryOs = num(17)?.toInt(),
            r = null,
            squawk = str(14),
            baroRate = num(11)?.let { it * 196.85 },
            geomRate = null,
            nacP = null,
        )
    }

    // ── Classification (verbatim port of the backend's type/heuristic tables) ──

    private val HELI_TYPES = setOf(
        "R22", "R44", "R66", "B06", "B06T", "B204", "B205", "B206", "B212", "B222", "B230",
        "B407", "B412", "B427", "B429", "B430", "B505", "B525",
        "AS32", "AS35", "AS50", "AS55", "AS65",
        "EC20", "EC25", "EC30", "EC35", "EC45", "EC55", "EC75",
        "H125", "H130", "H135", "H145", "H155", "H160", "H175", "H215", "H225",
        "S55", "S58", "S61", "S64", "S70", "S76", "S92",
        "A109", "A119", "A139", "A169", "A189", "AW09",
        "MD52", "MD60", "MDHI", "MD90", "NOTR",
        "B47G", "HUEY", "GAMA", "CABR", "EXE",
    )

    private val PRIVATE_JET_TYPES = setOf(
        "G150", "G200", "G280", "GLEX", "G500", "G550", "G600", "G650", "G700",
        "GLF2", "GLF3", "GLF4", "GLF5", "GLF6", "GL5T", "GL7T", "GV", "GIV",
        "CL30", "CL35", "CL60", "BD70", "BD10",
        "C25A", "C25B", "C25C", "C500", "C510", "C525", "C550", "C560", "C56X", "C680", "C700", "C750",
        "E35L", "E50P", "E55P", "E545", "E550",
        "FA50", "FA7X", "FA8X", "F900", "F2TH",
        "LJ35", "LJ40", "LJ45", "LJ60", "LJ70", "LJ75",
        "PC12", "PC24", "TBM7", "TBM8", "TBM9",
        "PRM1", "SF50", "EA50", "VLJ",
    )

    private val MILITARY_INDICATORS = setOf(
        "C17", "C5M", "C130", "C30J", "KC10", "KC46", "KC35", "E3CF", "E3TF", "E8A",
        "B1B", "B2", "B52", "F16", "F15", "F18", "F22", "F35", "A10", "F117",
        "RC135", "E6B", "P8A", "P3", "MQ9", "RQ4", "U2", "EP3", "RC12",
        "V22", "CH47", "UH60", "AH64", "AH1Z", "MV22",
        "EUFI", "RFAL", "TORD", "TYP", "GR4",
    )

    private val AIRLINER_TYPES = setOf(
        "A319", "A320", "A321", "A332", "A333", "A339", "A343", "A359", "A388",
        "B737", "B738", "B739", "B38M", "B39M", "B752", "B753", "B763", "B764",
        "B772", "B77L", "B77W", "B788", "B789", "B78X",
        "E170", "E175", "E190", "E195", "CRJ7", "CRJ9", "AT43", "AT72", "DH8D",
    )

    private val BIZJET_OPERATORS = setOf("EJA", "EJM", "NJE", "LXJ", "FJO", "VJT", "XOJ", "JTL", "WUP", "GAJ", "DPJ", "CLY", "TWY")

    private val AIRLINE_CODE_RE = Regex("^([A-Z]{3})\\d")
    private val CALLSIGN_RE = Regex("^[A-Z0-9]{3,8}$")
    private val MIL_CALLSIGN_RE = Regex("^(RCH|KING|DUKE|EVAC|JAKE|REACH|CONVOY)\\d", RegexOption.IGNORE_CASE)
    private const val JET_CRUISE_ALT_M = 8500.0
    private const val JET_CRUISE_KTS = 300.0

    private fun classifyFlight(f: RawAircraft): Pair<Flight, String>? {
        val modelUpper = f.t.orEmpty().uppercase()
        val flightStr = f.flight.orEmpty().trim().uppercase()
        val dbFlags = f.dbFlags ?: 0

        if (modelUpper == "TWR") return null
        val lat = f.lat ?: return null
        val lon = f.lon ?: return null

        val callsign = flightStr.ifEmpty { f.hex ?: "UNKNOWN" }
        val altMeters = f.altBaroFt?.let { it * 0.3048 } ?: 0.0
        val speedKnots = f.gs?.let { (it * 10).roundToInt() / 10.0 }
        val heading = f.track ?: 0.0
        val isHeli = HELI_TYPES.contains(modelUpper) || f.categoryOs == 8
        val isGrounded = f.altBaroFt != null && f.altBaroFt < 100

        val isOsMilitary = f.categoryOs == 14
        val isOsHighPerf = f.categoryOs == 7
        val isOsLight = f.categoryOs == 2
        val isOsHeavy = f.categoryOs == 4 || f.categoryOs == 5 || f.categoryOs == 6

        val airlineMatch = AIRLINE_CODE_RE.find(callsign)
        val airlineCode = airlineMatch?.groupValues?.get(1).orEmpty()

        val isGaCallsign = airlineCode.isEmpty() && CALLSIGN_RE.matches(flightStr)
        val cruisesLikeAJet = altMeters > JET_CRUISE_ALT_M && (speedKnots ?: 0.0) > JET_CRUISE_KTS

        val category = when {
            isOsMilitary || (dbFlags and 1) != 0 || MILITARY_INDICATORS.contains(modelUpper) || MIL_CALLSIGN_RE.containsMatchIn(f.flight.orEmpty()) -> "military"
            AIRLINER_TYPES.contains(modelUpper) || isOsHeavy -> "commercial"
            BIZJET_OPERATORS.contains(airlineCode) || PRIVATE_JET_TYPES.contains(modelUpper) || isOsHighPerf || (isGaCallsign && cruisesLikeAJet) -> "jet"
            isGaCallsign || isOsLight -> "private"
            else -> "commercial"
        }

        val verticalRateFpm = (f.baroRate ?: f.geomRate)?.roundToInt()

        val flight = Flight(
            callsign = callsign,
            lat = (lat * 100000).roundToInt() / 100000.0,
            lng = (lon * 100000).roundToInt() / 100000.0,
            alt = altMeters.roundToInt().toDouble(),
            heading = heading.roundToInt().toDouble(),
            speedKnots = speedKnots,
            model = f.t ?: "Unknown",
            icao24 = f.hex.orEmpty(),
            registration = f.r ?: "N/A",
            squawk = f.squawk.orEmpty(),
            airlineCode = airlineCode,
            aircraftCategory = if (isHeli) "heli" else "plane",
            grounded = isGrounded,
            verticalRateFpm = verticalRateFpm?.toDouble(),
        )
        return flight to category
    }

    // ── adsb.fi ──────────────────────────────────────────────────────

    private suspend fun fetchAdsbFiMil(): List<RawAircraft> =
        runCatching { DirectHttp.getJson<AdsbFiResponse>("$ADSBFI_BASE/mil").ac.map { it.toRaw() } }.getOrDefault(emptyList())

    private suspend fun fetchAdsbFiRegion(lat: Double, lon: Double): List<RawAircraft> =
        runCatching {
            DirectHttp.getJson<AdsbFiResponse>("$ADSBFI_BASE/lat/$lat/lon/$lon/dist/$ADSB_MAX_DIST").ac.map { it.toRaw() }
        }.getOrDefault(emptyList())

    private val REGIONS = listOf(
        39.8 to -98.5, 41.0 to -74.0, 33.0 to -84.0, 42.0 to -88.0, 30.0 to -97.0,
        47.0 to -122.0, 34.0 to -118.0, 45.0 to -73.0, 49.0 to -97.0,
        50.0 to 15.0, 51.5 to -1.0, 47.0 to 2.0, 40.0 to -4.0, 42.0 to 13.0,
        60.0 to 15.0, 52.0 to 22.0, 39.0 to 35.0,
        25.0 to 45.0, 22.0 to 78.0,
        35.0 to 105.0, 35.0 to 136.0, 37.0 to 127.0, 13.0 to 100.0, 1.0 to 104.0,
        -25.0 to 133.0, -33.0 to 151.0,
        0.0 to 20.0, -26.0 to 28.0,
        -15.0 to -60.0, -23.0 to -46.0,
    )

    // ── OpenSky ──────────────────────────────────────────────────────

    @Serializable
    private data class OpenSkyTokenResponse(val access_token: String? = null, val expires_in: Int? = null)

    private suspend fun getOpenSkyToken(): String? {
        val id = BuildConfig.OPENSKY_CLIENT_ID
        val secret = BuildConfig.OPENSKY_CLIENT_SECRET
        if (id.isBlank() || secret.isBlank()) return null
        osToken?.let { if (System.currentTimeMillis() < osTokenExpiry) return it }

        return runCatching {
            val body = "grant_type=client_credentials&client_id=${URLEncoder.encode(id, "UTF-8")}" +
                "&client_secret=${URLEncoder.encode(secret, "UTF-8")}"
            val text = postForm("https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token", body)
            val data = json.decodeFromString(OpenSkyTokenResponse.serializer(), text)
            val token = data.access_token ?: return null
            osToken = token
            osTokenExpiry = System.currentTimeMillis() + ((data.expires_in ?: 1800) - 60) * 1000L
            token
        }.getOrNull()
    }

    private suspend fun postForm(url: String, body: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()
        NetworkModule.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string() ?: error("Empty response body")
        }
    }

    // ── Build ────────────────────────────────────────────────────────

    private suspend fun buildSnapshot(): FlightsResponse = coroutineScope {
        val allRaw = mutableListOf<RawAircraft>()
        val seenHex = mutableSetOf<String>()

        fun ingest(raw: List<RawAircraft>) {
            for (ac in raw) {
                val hex = ac.hex.orEmpty().lowercase().trim()
                if (hex.isNotEmpty() && seenHex.add(hex)) allRaw += ac
            }
        }

        val now = System.currentTimeMillis()
        val skipOpenSky = now < openSkyCooldownUntil || now - osSnapshotTime < openSkyInterval()

        // Run the global military feed and OpenSky simultaneously — same reasoning as the
        // backend: keeps wall-clock time to max(mil, opensky) instead of sum.
        val milDeferred = async { fetchAdsbFiMil() }
        val osDeferred = if (!skipOpenSky) async { runCatching { refreshOpenSky() } } else null

        ingest(milDeferred.await())
        osDeferred?.await()

        ingest(osSnapshot)
        val openSkyWorked = osSnapshot.isNotEmpty()

        val source: String
        if (!openSkyWorked) {
            source = "regional"
            for ((lat, lon) in REGIONS) {
                ingest(fetchAdsbFiRegion(lat, lon))
                delay(ADSBFI_GAP_MS)
            }
        } else {
            source = if (hasOpenSkyCreds()) "opensky-auth" else "opensky-anon"
        }

        val commercial = mutableListOf<Flight>()
        val privateFl = mutableListOf<Flight>()
        val jets = mutableListOf<Flight>()
        val military = mutableListOf<Flight>()

        for (raw in allRaw) {
            val (flight, category) = classifyFlight(raw) ?: continue
            when (category) {
                "military" -> military += flight
                "jet" -> jets += flight
                "private" -> privateFl += flight
                else -> commercial += flight
            }
        }

        FlightsResponse(
            commercialFlights = commercial,
            privateFlights = privateFl,
            privateJets = jets,
            militaryFlights = military,
            total = allRaw.size,
            source = source,
        )
    }

    private suspend fun refreshOpenSky() {
        val token = getOpenSkyToken()
        val headers = token?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
        val url = "https://opensky-network.org/api/states/all?extended=1"

        val text = runCatching { DirectHttp.getText(url, headers) }.getOrElse { e ->
            if (e.message?.contains("429") == true) openSkyCooldownUntil = System.currentTimeMillis() + OPENSKY_COOLDOWN_MS
            return
        }
        val data = runCatching { json.decodeFromString(OpenSkyResponse.serializer(), text) }.getOrNull() ?: return
        if (data.states.size > 100) {
            osSnapshot = data.states.mapNotNull { openSkyStateToRaw(it) }
            osSnapshotTime = System.currentTimeMillis()
        }
    }
}
