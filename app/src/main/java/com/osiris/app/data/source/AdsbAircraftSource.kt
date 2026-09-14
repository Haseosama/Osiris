package com.osiris.app.data.source

import com.osiris.app.data.model.AircraftDetail
import com.osiris.app.data.model.FlightAirport
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * An airframe's identity and actually-flown track, called directly from the phone — mirrors
 * `osiris-backend/src/app/api/aircraft/route.ts`. adsb.lol publishes the readsb trace files
 * (real flown path, registration, type, model); adsbdb fills in the registered operator, which
 * the traces don't carry. Both are keyless.
 */
object AdsbAircraftSource {

    private const val TRACE_BASE = "https://adsb.lol/data/traces"
    private const val ADSBDB = "https://api.adsbdb.com/v0/aircraft"
    private const val DEPARTURE_CEILING_FT = 10_000.0

    /** readsb shards traces by the last two characters of the hex address. */
    private fun shardFor(icao24: String): String = icao24.trim().lowercase().takeLast(2)

    @Serializable
    private data class TraceFile(
        val icao: String? = null,
        val r: String? = null,
        val t: String? = null,
        val desc: String? = null,
        val trace: List<JsonArray> = emptyList(),
    )

    @Serializable
    private data class AdsbdbResponse(val response: AdsbdbAircraftWrapper? = null)

    @Serializable
    private data class AdsbdbAircraftWrapper(val aircraft: AdsbdbAircraft? = null)

    @Serializable
    private data class AdsbdbAircraft(
        val manufacturer: String? = null,
        val type: String? = null,
        val registered_owner: String? = null,
        val registration: String? = null,
        val icao_type: String? = null,
    )

    private fun JsonArray.numAt(i: Int): Double? =
        getOrNull(i)?.let { if (it is JsonNull) null else (it as? JsonPrimitive)?.doubleOrNull }

    private fun JsonArray.isGround(): Boolean {
        val el = getOrNull(3) as? JsonPrimitive ?: return false
        return el.isString && el.content == "ground"
    }

    /**
     * Keep only the leg the aircraft is currently flying. A full trace covers 24h, which for an
     * airliner is several separate flights strung end to end — walk back from the newest point,
     * skip time parked, take the airborne run, stop at the previous stint on the ground.
     */
    private fun currentLeg(rows: List<JsonArray>, minGroundRun: Int = 4): List<JsonArray> {
        if (rows.isEmpty()) return rows
        var i = rows.size - 1
        while (i >= 0 && rows[i].isGround()) i--
        if (i < 0) return rows.takeLast(2)
        val end = i

        var run = 0
        var runNewest = -1
        var start = 0
        while (i >= 0) {
            if (rows[i].isGround()) {
                if (run == 0) runNewest = i
                run++
                if (run >= minGroundRun) {
                    start = runNewest + 1
                    break
                }
            } else {
                run = 0
            }
            i--
        }
        val leg = rows.subList(start, end + 1)
        return if (leg.size > 1) leg else rows
    }

    private fun <T> downsample(points: List<T>, max: Int): List<T> {
        if (points.size <= max) return points
        val step = (points.size - 1).toDouble() / (max - 1)
        return (0 until max).map { i -> points[Math.round(i * step).toInt()] }
    }

    private data class ParsedTrace(
        val icao24: String,
        val registration: String?,
        val typeCode: String?,
        val model: String?,
        val track: List<List<Double>>,
        val altitudes: List<Double?>,
        val points: Int,
        val departure: FlightAirport?,
        val arrival: FlightAirport?,
    )

    private fun parseTrace(trace: TraceFile, maxPoints: Int = 700): ParsedTrace {
        val rows = currentLeg(trace.trace)
        val track = mutableListOf<List<Double>>()
        val altitudes = mutableListOf<Double?>()
        for (row in rows) {
            val lat = row.numAt(1) ?: continue
            val lng = row.numAt(2) ?: continue
            if (!lat.isFinite() || !lng.isFinite()) continue
            track += listOf(lng, lat)
            altitudes += row.numAt(3)
        }

        val idx = downsample(track.indices.toList(), maxPoints)
        val keptTrack = idx.map { track[it] }
        val keptAltitudes = idx.map { altitudes[it] }

        val landed = trace.trace.isNotEmpty() && trace.trace.last().isGround()

        var departure: FlightAirport? = null
        var arrival: FlightAirport? = null
        if (track.size >= 2) {
            val firstAlt = altitudes.first()
            val lowStart = firstAlt == null || firstAlt <= DEPARTURE_CEILING_FT
            if (lowStart) departure = Airports.nearestAirport(track.first()[1], track.first()[0])
            if (landed) arrival = Airports.nearestAirport(track.last()[1], track.last()[0])
        }

        return ParsedTrace(
            icao24 = trace.icao.orEmpty().lowercase(),
            registration = trace.r?.trim()?.takeIf { it.isNotEmpty() },
            typeCode = trace.t?.trim()?.takeIf { it.isNotEmpty() },
            model = trace.desc?.trim()?.takeIf { it.isNotEmpty() },
            track = keptTrack,
            altitudes = keptAltitudes,
            points = keptTrack.size,
            departure = departure,
            arrival = arrival,
        )
    }

    suspend fun fetch(icao24Input: String): AircraftDetail? {
        val icao24 = icao24Input.trim().lowercase()
        if (!Regex("^[0-9a-f]{6}$").matches(icao24)) return null

        val trace = runCatching {
            DirectHttp.getJson<TraceFile>("$TRACE_BASE/${shardFor(icao24)}/trace_full_$icao24.json")
        }.getOrNull()
        val db = runCatching { DirectHttp.getJson<AdsbdbResponse>("$ADSBDB/$icao24") }.getOrNull()

        val ac = db?.response?.aircraft
        val parsed = trace?.let { parseTrace(it) }
        if (parsed == null && ac == null) return null

        val model = parsed?.model
            ?: (ac?.manufacturer?.takeIf { it.isNotBlank() } to ac?.type?.takeIf { it.isNotBlank() })
                .let { (m, t) -> if (m != null && t != null) "$m $t" else null }
            ?: ac?.type

        return AircraftDetail(
            icao24 = icao24,
            registration = parsed?.registration ?: ac?.registration,
            typeCode = parsed?.typeCode ?: ac?.icao_type,
            model = model,
            operator = ac?.registered_owner,
            track = parsed?.track ?: emptyList(),
            altitudes = parsed?.altitudes ?: emptyList(),
            points = parsed?.points ?: 0,
            departure = parsed?.departure,
            arrival = parsed?.arrival,
            source = if (parsed != null) "adsb.lol" else "adsbdb",
        )
    }
}
