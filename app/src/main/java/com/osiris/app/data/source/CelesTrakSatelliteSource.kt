package com.osiris.app.data.source

import com.osiris.app.data.model.Satellite
import com.osiris.app.data.model.SatelliteNextPass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import uk.me.g4dpz.satellite.GroundStationPosition
import uk.me.g4dpz.satellite.PassPredictor
import uk.me.g4dpz.satellite.SatelliteFactory
import uk.me.g4dpz.satellite.TLE
import java.util.Date

/**
 * Satellite tracking via CelesTrak TLEs (SatNOGS as fallback), propagated on-device with
 * predict4java's SGP4/SDP4 — called directly from the phone, mirrors
 * `osiris-backend/src/app/api/satellites/route.ts` + `src/lib/orbit.ts`. Same reasoning for the
 * ~40-group fan-out as the backend: CelesTrak 403s a single "give me everything" request, so
 * many smaller group requests in parallel stand in for one big one, each easy for CelesTrak to
 * serve. No disk cache here unlike the backend (which persists across server restarts on a
 * machine that stays up for days) — an Android process is killed far more often by the OS
 * regardless, so the in-memory-only cache below just refills from CelesTrak on a cold app start
 * instead of surviving it; a straightforward simplification, not a corner cut to fix later.
 */
object CelesTrakSatelliteSource {

    private data class TleEntry(val name: String, val line1: String, val line2: String)

    private const val CT = "https://celestrak.org/NORAD/elements/gp.php?GROUP="
    private const val FMT = "&FORMAT=tle"
    private val CELESTRAK_GROUPS = listOf(
        "${CT}active$FMT",
        "https://celestrak.org/NORAD/elements/supplemental/sup-gp.php?FILE=starlink&FORMAT=tle",
        "${CT}gps-ops$FMT", "${CT}glonass-operational$FMT", "${CT}galileo$FMT", "${CT}beidou$FMT",
        "${CT}oneweb$FMT", "${CT}iridium-NEXT$FMT", "${CT}globalstar$FMT", "${CT}orbcomm$FMT",
        "${CT}intelsat$FMT", "${CT}ses$FMT", "${CT}other-comm$FMT", "${CT}x-comm$FMT",
        "${CT}stations$FMT", "${CT}education$FMT", "${CT}engineering$FMT", "${CT}science$FMT",
        "${CT}weather$FMT", "${CT}resource$FMT", "${CT}sarsat$FMT", "${CT}planet$FMT",
        "${CT}goes$FMT", "${CT}argos$FMT", "${CT}dmc$FMT", "${CT}spire$FMT",
        "${CT}military$FMT", "${CT}radar$FMT", "${CT}geodetic$FMT", "${CT}tdrss$FMT",
        "${CT}geo$FMT",
        "${CT}cubesat$FMT", "${CT}tle-new$FMT", "${CT}amateur$FMT",
        "${CT}last-30-days$FMT",
        "${CT}visual$FMT",
        "${CT}supplemental$FMT",
        "${CT}fengyun-1c-debris$FMT", "${CT}cosmos-2251-debris$FMT",
        "${CT}iridium-33-debris$FMT", "${CT}cosmos-1408-debris$FMT",
        "${CT}nnss$FMT", "${CT}musson$FMT",
    )
    private const val SATNOGS_API = "https://db.satnogs.org/api/tle/?format=json"

    private const val REFRESH_MIN_COUNT = 5000
    private const val REFRESH_STALE_MS = 3_600_000L

    // Standard gravitational parameter of Earth (km³/s²) and mean radius (km, matching the value
    // already used elsewhere for the orbital-speed estimate in InfoDialogContent) — both needed
    // to turn a TLE's mean motion into apogee/perigee altitude via Kepler's third law.
    private const val EARTH_MU_KM3_S2 = 398_600.4418
    private const val EARTH_RADIUS_KM = 6_371.0

    private val fetchMutex = Mutex()
    @Volatile private var cachedTles: List<TleEntry> = emptyList()
    @Volatile private var cacheTime = 0L

    private val ISS_FALLBACK = TleEntry(
        "ISS (FALLBACK)",
        "1 25544U 98067A   24146.40251785  .00015505  00000-0  27885-3 0  9997",
        "2 25544  51.6402 189.7042 0004381 334.8091 106.8778 15.50091157455243",
    )

    // ── Mission classification (verbatim keyword table from the backend) ──

    private val MISSION_CLASSIFY = listOf(
        "USA" to "Military Recon", "NROL" to "NRO Classified", "LACROSSE" to "SAR Imaging",
        "MENTOR" to "SIGINT", "ORION" to "SIGINT", "TRUMPET" to "SIGINT",
        "GPS" to "Navigation", "NAVSTAR" to "Navigation", "GLONASS" to "Navigation",
        "GALILEO" to "Navigation", "BEIDOU" to "Navigation",
        "SBIRS" to "Early Warning", "DSP" to "Early Warning",
        "STARLINK" to "Commercial Comms", "ONEWEB" to "Commercial Comms",
        "PLANET" to "Earth Imaging", "WORLDVIEW" to "Commercial Imaging",
        "ISS" to "Space Station", "TIANGONG" to "Space Station",
        "COSMOS" to "Russian Military", "YAOGAN" to "Chinese Recon",
        "FENGYUN" to "Weather", "GOES" to "Weather", "NOAA" to "Weather", "METEOSAT" to "Weather",
        "LANDSAT" to "Earth Observation", "SENTINEL" to "Earth Observation",
        "TERRA" to "Earth Science", "AQUA" to "Earth Science",
        "HUBBLE" to "Space Telescope", "JAMES WEBB" to "Space Telescope",
    )

    private fun classifyMission(name: String): String {
        val upper = name.uppercase()
        return MISSION_CLASSIFY.firstOrNull { (keyword, _) -> upper.contains(keyword) }?.second ?: "Unknown"
    }

    private fun categoryFor(name: String, mission: String): String {
        val upper = name.uppercase()
        return when {
            upper.contains(" DEB") || upper.contains("DEBRIS") || upper.contains(" R/B") -> "other"
            mission == "Commercial Comms" || mission == "Commercial Imaging" -> "comms"
            mission == "Navigation" -> "navigation"
            mission == "Weather" || mission == "Earth Observation" || mission == "Earth Science" -> "earth_obs"
            mission in setOf("Military Recon", "NRO Classified", "SIGINT", "Early Warning", "Russian Military", "Chinese Recon", "SAR Imaging") -> "military"
            mission == "Space Station" || mission == "Space Telescope" -> "science"
            else -> "other"
        }
    }

    // ── Public API ──────────────────────────────────────────────────

    suspend fun fetch(): Pair<List<Satellite>, String> = withContext(Dispatchers.Default) {
        val source = ensureTleCatalogFresh()
        val tles = cachedTles.ifEmpty { listOf(ISS_FALLBACK) }
        val effectiveSource = if (cachedTles.isEmpty()) "emergency-fallback" else source

        val satellites = tles.mapNotNull { tle -> propagate(tle) }
        satellites to effectiveSource
    }

    /** Re-propagates just the given NORAD ids off the in-memory TLE cache — no network, pure
     * SGP4/SDP4 math, so [com.osiris.app.map.MapViewModel] can call this several times a second
     * for the currently-displayed satellites to get their true live position instead of a
     * dead-reckoned approximation between polls. Cheap because it's bounded to the (typically a
     * few hundred) satellites actually on screen, not the ~19k-entry full catalogue [fetch]
     * classifies. */
    suspend fun propagateLive(noradIds: Set<String>): List<Satellite> = withContext(Dispatchers.Default) {
        if (noradIds.isEmpty()) return@withContext emptyList()
        cachedTles.filter { noradOf(it.line1) in noradIds }.mapNotNull { propagate(it) }
    }

    /** Pure math on the TLE's mean motion — no propagation needed, so this doesn't touch
     * predict4java at all. Mirrors `orbitalPeriodMinutes` in the backend's orbit.ts. */
    fun fetchOrbitPeriod(noradId: String): Double? {
        val tle = cachedTles.firstOrNull { noradOf(it.line1) == noradId } ?: return null
        val meanMotion = tle.line2.drop(52).take(11).trim().toDoubleOrNull() ?: return null
        if (meanMotion <= 0) return null
        return 1440.0 / meanMotion
    }

    /** Next time [noradId] rises above the horizon for an observer at [observerLat]/[observerLng]
     * — the backend never had this at all (it only ever propagated a snapshot position, never ran
     * a pass search), so this is fully on-device via predict4java's PassPredictor against the
     * same cached TLE the map position comes from. Null if the TLE isn't cached, or if
     * PassPredictor itself determines there's no visible pass at all — a GEO satellite parked
     * over the other side of the globe, or another case its own willBeSeen() check flags (see
     * PassPredictor.validateData()'s SatNotFoundException). Wrapped in a timeout: a pass search
     * steps forward in 30-60s increments and, per predict4java's own docs, can take a while for a
     * satellite with a long period — better to give up and show "no pass found" than hang the
     * dialog indefinitely on a pathological TLE. */
    suspend fun nextPass(noradId: String, observerLat: Double, observerLng: Double): SatelliteNextPass? =
        withContext(Dispatchers.Default) {
            val entry = cachedTles.firstOrNull { noradOf(it.line1) == noradId } ?: return@withContext null
            withTimeoutOrNull(15_000L) {
                runCatching {
                    val tle = TLE(arrayOf(entry.name, entry.line1, entry.line2))
                    val qth = GroundStationPosition(observerLat, observerLng, 0.0)
                    val pass = PassPredictor(tle, qth).nextSatPass(Date())
                    SatelliteNextPass(
                        startTimeMs = pass.startTime.time,
                        endTimeMs = pass.endTime.time,
                        maxElevationDeg = pass.maxEl,
                        aosAzimuthDeg = pass.aosAzimuth,
                        losAzimuthDeg = pass.losAzimuth,
                    )
                }.getOrNull()
            }
        }

    private fun noradOf(line1: String): String = line1.drop(2).take(5).trim()

    private data class OrbitalElements(
        val inclinationDeg: Double,
        val eccentricity: Double,
        val apogeeAltKm: Double,
        val perigeeAltKm: Double,
        val launchYear: Int?,
    )

    /** Pure string/math on the two TLE lines, no predict4java involved — column positions per the
     * standard TLE format (verified against the same file's own mean-motion extraction in
     * [fetchOrbitPeriod], which uses the identical column range). Apogee/perigee come from the
     * semi-major axis implied by mean motion (Kepler's third law: a = cbrt(μ / n²)) combined with
     * eccentricity, not from any live propagation — a satellite's *current* altitude ([alt] in
     * [Satellite]) varies continuously between these two over one orbit. */
    private fun parseOrbitalElements(line1: String, line2: String): OrbitalElements? = runCatching {
        val inclinationDeg = line2.substring(8, 16).trim().toDouble()
        val eccentricity = ("0." + line2.substring(26, 33).trim()).toDouble()
        val meanMotion = line2.substring(52, 63).trim().toDouble() // revolutions/day

        val intlDesig = line1.substring(9, 17).trim()
        val launchYear = intlDesig.take(2).toIntOrNull()?.let { if (it < 57) 2000 + it else 1900 + it }

        val meanMotionRadPerSec = meanMotion * 2.0 * Math.PI / 86_400.0
        val semiMajorAxisKm = Math.cbrt(EARTH_MU_KM3_S2 / (meanMotionRadPerSec * meanMotionRadPerSec))
        val apogeeAltKm = semiMajorAxisKm * (1 + eccentricity) - EARTH_RADIUS_KM
        val perigeeAltKm = semiMajorAxisKm * (1 - eccentricity) - EARTH_RADIUS_KM

        OrbitalElements(inclinationDeg, eccentricity, apogeeAltKm, perigeeAltKm, launchYear)
    }.getOrNull()

    private fun propagate(tle: TleEntry): Satellite? {
        val position = runCatching {
            val t = TLE(arrayOf(tle.name, tle.line1, tle.line2))
            val sat = SatelliteFactory.createSatellite(t)
            sat.calculateSatelliteVectors(Date())
            sat.calculateSatelliteGroundTrack()
        }.getOrNull() ?: return null

        val latDeg = Math.toDegrees(position.latitude)
        val lngDeg = wrapLongitude(Math.toDegrees(position.longitude))
        val altKm = position.altitude

        if (!latDeg.isFinite() || !lngDeg.isFinite() || !altKm.isFinite()) return null
        // Below this it has re-entered; above it is not an Earth orbit worth drawing — same
        // sanity range the backend applies.
        if (altKm < 80 || altKm > 60000) return null

        val mission = classifyMission(tle.name)
        val elements = parseOrbitalElements(tle.line1, tle.line2)
        return Satellite(
            name = tle.name,
            lat = Math.round(latDeg * 10000) / 10000.0,
            lng = Math.round(lngDeg * 10000) / 10000.0,
            alt = Math.round(altKm).toDouble(),
            mission = mission,
            category = categoryFor(tle.name, mission),
            noradId = noradOf(tle.line1),
            inclinationDeg = elements?.inclinationDeg?.let { Math.round(it * 100) / 100.0 },
            eccentricity = elements?.eccentricity,
            apogeeAltKm = elements?.apogeeAltKm?.let { Math.round(it).toDouble() },
            perigeeAltKm = elements?.perigeeAltKm?.let { Math.round(it).toDouble() },
            launchYear = elements?.launchYear,
        )
    }

    private fun wrapLongitude(deg: Double): Double = (((deg + 180) % 360 + 360) % 360) - 180

    // ── TLE catalogue refresh ──────────────────────────────────────

    private suspend fun ensureTleCatalogFresh(): String {
        val now = System.currentTimeMillis()
        if (cachedTles.size >= REFRESH_MIN_COUNT && now - cacheTime < REFRESH_STALE_MS) return "memory-cache"

        return fetchMutex.withLock {
            val now2 = System.currentTimeMillis()
            if (cachedTles.size >= REFRESH_MIN_COUNT && now2 - cacheTime < REFRESH_STALE_MS) return@withLock "memory-cache"

            val merged = fetchFromCelesTrak()
            if (merged.size > 500) {
                cachedTles = merged
                cacheTime = System.currentTimeMillis()
                return@withLock "celestrak (${merged.size} TLEs)"
            }

            val satnogs = runCatching { fetchFromSatNogs() }.getOrDefault(emptyList())
            if (satnogs.isNotEmpty()) {
                cachedTles = satnogs
                cacheTime = System.currentTimeMillis()
                return@withLock "satnogs-api"
            }

            if (cachedTles.isEmpty()) "emergency-fallback" else "memory-cache"
        }
    }

    private suspend fun fetchFromCelesTrak(): List<TleEntry> = coroutineScope {
        val results = CELESTRAK_GROUPS.map { url -> async { runCatching { fetchCelesTrakGroup(url) }.getOrDefault(emptyList()) } }
            .map { it.await() }

        val seen = mutableSetOf<String>()
        val merged = mutableListOf<TleEntry>()
        for (group in results) {
            for (sat in group) {
                val noradId = noradOf(sat.line1)
                if (seen.add(noradId)) merged += sat
            }
        }
        // Backfill with cached satellites that failed to fetch this time (rate limits etc).
        for (sat in cachedTles) {
            val noradId = noradOf(sat.line1)
            if (seen.add(noradId)) merged += sat
        }
        merged
    }

    private suspend fun fetchCelesTrakGroup(url: String): List<TleEntry> {
        val text = DirectHttp.getText(url, headers = mapOf("User-Agent" to "OSIRIS-Android/1.0"))
        if (text.contains("has not updated since") || text.length < 100) return emptyList()
        return parseTleText(text)
    }

    private fun parseTleText(text: String): List<TleEntry> {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val sats = mutableListOf<TleEntry>()
        var i = 0
        while (i < lines.size - 1) {
            when {
                !lines[i].startsWith("1") && lines.getOrNull(i + 1)?.startsWith("1") == true && lines.getOrNull(i + 2)?.startsWith("2") == true -> {
                    sats += TleEntry(lines[i].removePrefix("0").trim(), lines[i + 1], lines[i + 2])
                    i += 3
                }
                lines[i].startsWith("1") && lines.getOrNull(i + 1)?.startsWith("2") == true -> {
                    val noradId = lines[i].drop(2).take(5).trim()
                    sats += TleEntry("SAT-$noradId", lines[i], lines[i + 1])
                    i += 2
                }
                else -> i++
            }
        }
        return sats
    }

    @Serializable
    private data class SatNogsEntry(val tle0: String? = null, val tle1: String? = null, val tle2: String? = null)

    private suspend fun fetchFromSatNogs(): List<TleEntry> {
        val entries = DirectHttp.getJson<List<SatNogsEntry>>(SATNOGS_API, headers = mapOf("Accept" to "application/json"))
        val seenNames = mutableSetOf<String>()
        val out = mutableListOf<TleEntry>()
        for (item in entries) {
            val name = item.tle0?.trim()?.removePrefix("0")?.trim().orEmpty()
            val line1 = item.tle1?.trim()
            val line2 = item.tle2?.trim()
            if (name.isNotEmpty() && !line1.isNullOrEmpty() && !line2.isNullOrEmpty() && seenNames.add(name)) {
                out += TleEntry(name, line1, line2)
            }
        }
        return out
    }
}
