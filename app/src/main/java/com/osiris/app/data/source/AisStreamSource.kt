package com.osiris.app.data.source

import android.util.Log
import com.osiris.app.BuildConfig
import com.osiris.app.data.model.Chokepoint
import com.osiris.app.data.model.MaritimeResponse
import com.osiris.app.data.model.Port
import com.osiris.app.data.model.Ship
import com.osiris.app.data.remote.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Live AIS vessel tracking via aisstream.io, connected directly from the phone — mirrors
 * `osiris-backend/src/app/api/maritime/route.ts`. The backend kept one long-lived WebSocket
 * open for its whole process lifetime, continuously accumulating ships into an in-memory cache
 * that every HTTP GET just read a snapshot of; this does the exact same thing, just with the
 * app itself as the only "client" instead of serving many over HTTP, so there's no need for the
 * backend's extra response-snapshot cache (that existed purely to survive many concurrent
 * pollers, not relevant with one device polling itself).
 */
object AisStreamSource {

    // No visibility at all into this connection previously (every failure/parse error was
    // swallowed silently) — added while chasing a "no ships ever show up" report that left zero
    // trace in logcat, so the next repro actually says why.
    private const val TAG = "AisStreamSource"

    private const val WS_URL = "wss://stream.aisstream.io/v0/stream"
    private const val STALE_MS = 10 * 60 * 1000L
    private const val MAX_SHIPS = 20_000
    private const val RECONNECT_DELAY_MS = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    // [NetworkModule.okHttpClient]'s 30s readTimeout is meant for bounded HTTP calls — applied to
    // a long-lived WebSocket it kills the connection during any lull longer than that (a known
    // OkHttp gotcha: the read timeout is enforced per idle gap, not per request), which reads as
    // "ships stop updating"/"no ships" until scheduleReconnect() catches it 5s later. A dedicated
    // client with no read timeout and a ping every 20s (well under that 30s window, keeping the
    // connection demonstrably alive even if aisstream.io itself goes quiet) avoids the churn.
    private val webSocketClient = NetworkModule.okHttpClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private data class MutableShip(
        val mmsi: Long,
        var lat: Double? = null,
        var lng: Double? = null,
        var speed: Double? = null,
        var heading: Double? = null,
        var name: String? = null,
        var destination: String? = null,
        var type: String? = null,
        var navStatus: Int? = null,
        var callSign: String? = null,
        var imo: Long? = null,
        var lengthM: Double? = null,
        var widthM: Double? = null,
        var draughtM: Double? = null,
        var etaMonth: Int? = null,
        var etaDay: Int? = null,
        var etaHour: Int? = null,
        var etaMinute: Int? = null,
        var timestamp: Long = System.currentTimeMillis(),
    )

    // LinkedHashMap for insertion order (oldest-first eviction, same as the backend's Map),
    // synchronized since the WebSocket listener writes from an OkHttp thread while fetch() reads
    // from a coroutine caller.
    private val shipsCache = Collections.synchronizedMap(LinkedHashMap<Long, MutableShip>())

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var connecting = false

    @Volatile private var messagesReceived = 0L

    fun ensureConnected() {
        if (connecting || webSocket != null) return
        val apiKey = BuildConfig.AIS_API_KEY
        if (apiKey.isBlank()) {
            Log.w(TAG, "AIS_API_KEY is blank (local.properties not picked up by this build?) — never connecting")
            return
        }
        connecting = true
        Log.d(TAG, "connecting to $WS_URL")

        val request = Request.Builder().url(WS_URL).build()
        webSocket = webSocketClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                connecting = false
                val sub = subscriptionMessage(apiKey)
                val sent = ws.send(sub)
                Log.d(TAG, "connected (HTTP ${response.code}), sending subscription (${sub.length} chars, enqueued=$sent): $sub")
            }

            override fun onMessage(ws: WebSocket, text: String) = onPayload(text)

            // aisstream.io sends every stream message as a BINARY frame, not text — confirmed by
            // direct testing: this was the actual bug behind "no ships ever show up". OkHttp only
            // ever called onMessage(String) here before, which this server-side choice never
            // triggers, so every real message was silently dropped by the (until now unoverridden)
            // no-op default WebSocketListener.onMessage(ByteString) — the connection looked
            // healthy (HTTP 101, subscription sent) but nothing ever reached handleMessage().
            override fun onMessage(ws: WebSocket, bytes: okio.ByteString) = onPayload(bytes.utf8())

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "closing: $code $reason")
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "closed: $code $reason (after $messagesReceived messages) — reconnecting in ${RECONNECT_DELAY_MS}ms")
                webSocket = null
                connecting = false
                scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "connection failed (HTTP ${response?.code}, after $messagesReceived messages) — reconnecting in ${RECONNECT_DELAY_MS}ms", t)
                webSocket = null
                connecting = false
                scheduleReconnect()
            }
        })
    }

    private fun onPayload(text: String) {
        messagesReceived++
        if (messagesReceived <= 3 || messagesReceived % 500 == 0L) {
            Log.d(TAG, "message #$messagesReceived (${text.take(300)})")
        }
        handleMessage(text)
    }

    private fun scheduleReconnect() {
        scope.launch {
            delay(RECONNECT_DELAY_MS)
            ensureConnected()
        }
    }

    private fun subscriptionMessage(apiKey: String): String {
        // Same target bounding boxes the backend used — high-value SCM chokepoints/hubs, plus a
        // global fallback aisstream heavily samples on the free tier.
        val boxes = listOf(
            listOf(listOf(34.8, 139.5), listOf(35.7, 140.2)), // Tokyo Bay
            listOf(listOf(25.0, 54.0), listOf(27.5, 57.5)), // Hormuz
            listOf(listOf(27.0, 32.0), listOf(32.0, 33.5)), // Suez Canal
            listOf(listOf(12.0, 42.5), listOf(14.0, 44.0)), // Bab el-Mandeb
            listOf(listOf(8.0, -80.5), listOf(10.0, -79.0)), // Panama Canal
            listOf(listOf(1.0, 103.0), listOf(3.0, 104.5)), // Malacca / Singapore
            listOf(listOf(22.0, 118.0), listOf(26.0, 121.0)), // Taiwan Strait
            listOf(listOf(50.0, 0.0), listOf(53.0, 5.0)), // Rotterdam / English Channel
            listOf(listOf(33.0, -119.0), listOf(34.5, -117.0)), // US West Coast (LA/LB)
            listOf(listOf(-90.0, -180.0), listOf(90.0, 180.0)), // Global fallback
        )
        val sub = Subscription(apiKey, boxes, listOf("PositionReport", "ShipStaticData"))
        return json.encodeToString(Subscription.serializer(), sub)
    }

    @Serializable
    private data class Subscription(
        val APIKey: String,
        val BoundingBoxes: List<List<List<Double>>>,
        val FilterMessageTypes: List<String>,
    )

    @Serializable
    private data class AisMessage(
        val MessageType: String? = null,
        val MetaData: AisMetaData? = null,
        val Message: AisMessageBody? = null,
    )

    @Serializable private data class AisMetaData(val MMSI: Long? = null, val ShipName: String? = null)
    @Serializable
    private data class AisMessageBody(
        val PositionReport: AisPositionReport? = null,
        val ShipStaticData: AisShipStaticData? = null,
    )

    @Serializable
    private data class AisPositionReport(
        val Latitude: Double? = null,
        val Longitude: Double? = null,
        val Sog: Double? = null,
        val TrueHeading: Double? = null,
        val Cog: Double? = null,
        val NavigationalStatus: Int? = null,
    )

    @Serializable
    private data class AisShipStaticData(
        val Name: String? = null,
        val Destination: String? = null,
        val Type: Int? = null,
        val CallSign: String? = null,
        val ImoNumber: Long? = null,
        val Dimension: AisDimension? = null,
        val MaximumStaticDraught: Double? = null,
        val Eta: AisEta? = null,
    )

    /** Bow/stern/port/starboard distances from the AIS antenna, in metres — A+B is overall
     * length, C+D is overall beam (width). */
    @Serializable
    private data class AisDimension(val A: Int? = null, val B: Int? = null, val C: Int? = null, val D: Int? = null)

    /** No year field in raw AIS — vessels only ever report month/day/hour/minute, implicitly
     * "the next time this date/time comes around". Month=0 or Day=0 both mean "not available". */
    @Serializable
    private data class AisEta(val Month: Int? = null, val Day: Int? = null, val Hour: Int? = null, val Minute: Int? = null)

    private fun osirisShipType(typeCode: Int?): String = when {
        typeCode == null -> "cargo"
        typeCode in 80..89 -> "tanker"
        typeCode in 70..79 -> "cargo"
        typeCode == 35 -> "military"
        else -> "cargo"
    }

    /** ITU-R M.1371 Table 45 — the standard AIS navigational status codes, verbatim (0-15 are
     * all defined; anything else would be a malformed message). Explains a lot of "why isn't this
     * ship moving": 1/5/6 are anchored/moored/aground, not a tracking bug. */
    fun navStatusLabel(status: Int?): String? = when (status) {
        0 -> "En route (moteur)"
        1 -> "À l'ancre"
        2 -> "Sous contrôle"
        3 -> "Manœuvrabilité restreinte"
        4 -> "Gêné par son tirant d'eau"
        5 -> "Amarré"
        6 -> "Échoué"
        7 -> "En pêche"
        8 -> "En route (voile)"
        9 -> "Engin à grande vitesse"
        10 -> "Engin à effet de surface"
        11 -> "En remorque (arrière)"
        12 -> "En poussage/remorque (côté)"
        14 -> "Alerte AIS-SART/MOB/EPIRB"
        else -> null
    }

    /** No year in raw AIS ETA — this just formats month/day/hour/minute as given. Per spec,
     * Month=0 or Day=0 both mean "not available" (and Hour=24/Minute=60 are the same for those
     * fields specifically, though aisstream.io tends to omit the field entirely instead). */
    fun formatEta(month: Int?, day: Int?, hour: Int?, minute: Int?): String? {
        if (month == null || day == null || month == 0 || day == 0) return null
        val h = hour?.takeIf { it in 0..23 } ?: return "%02d/%02d".format(day, month)
        val m = minute?.takeIf { it in 0..59 } ?: 0
        return "%02d/%02d %02d:%02d".format(day, month, h, m)
    }

    /** First 3 digits of the MMSI are the ITU Maritime Identification Digits — the vessel's flag
     * state, assigned per country regardless of where it actually sails. Not exhaustive (the real
     * MID table has ~200 entries across every UN member) — covers the major flag registries and
     * the biggest maritime nations, which is the large majority of what actually shows up on a
     * live AIS feed; an unlisted MID just shows no flag rather than a wrong one. */
    fun mmsiFlagCountry(mmsi: Long?): String? {
        val mid = mmsi?.toString()?.takeIf { it.length == 9 }?.take(3)?.toIntOrNull() ?: return null
        return MID_TABLE[mid]
    }

    private val MID_TABLE: Map<Int, String> = buildMap {
        // Open/flag-of-convenience registries — a large share of world tonnage.
        for (m in 351..357) put(m, "Panama")
        for (m in 636..637) put(m, "Liberia")
        put(538, "Îles Marshall")
        put(477, "Hong Kong")
        for (m in 563..566) put(m, "Singapour")
        put(215, "Malte")
        put(229, "Malte")
        for (m in 248..249) put(m, "Malte")
        for (m in 308..311) put(m, "Bahamas")
        put(319, "Îles Caïmans")
        put(667, "Saint-Vincent-et-les-Grenadines")
        put(548, "Îles Cook")
        // Major shipbuilding/trading/naval nations.
        for (m in 412..414) put(m, "Chine")
        for (m in 431..432) put(m, "Japon")
        for (m in 440..441) put(m, "Corée du Sud")
        put(416, "Taïwan")
        put(232, "Royaume-Uni")
        put(233, "Royaume-Uni")
        put(235, "Royaume-Uni")
        for (m in 366..369) put(m, "États-Unis")
        put(303, "États-Unis")
        for (m in 244..245) put(m, "Pays-Bas")
        put(211, "Allemagne")
        put(218, "Allemagne")
        for (m in 226..228) put(m, "France")
        put(247, "Italie")
        put(212, "Chypre")
        put(209, "Chypre")
        put(250, "Irlande")
        for (m in 219..220) put(m, "Danemark")
        for (m in 257..259) put(m, "Norvège")
        for (m in 265..266) put(m, "Suède")
        put(230, "Finlande")
        put(273, "Russie")
        put(533, "Malaisie")
        put(574, "Vietnam")
        put(577, "Philippines")
        put(419, "Inde")
        put(525, "Indonésie")
        put(503, "Australie")
        put(512, "Nouvelle-Zélande")
        put(725, "Chili")
        put(710, "Brésil")
    }

    private fun handleMessage(text: String) {
        val parsed = runCatching { json.decodeFromString(AisMessage.serializer(), text) }
            .onFailure { Log.e(TAG, "failed to parse message: ${text.take(300)}", it) }
            .getOrNull() ?: return
        val mmsi = parsed.MetaData?.MMSI ?: return

        val existing = shipsCache.getOrPut(mmsi) { MutableShip(mmsi = mmsi) }
        parsed.MetaData.ShipName?.trim()?.takeIf { it.isNotEmpty() }?.let { existing.name = it }

        when (parsed.MessageType) {
            "PositionReport" -> parsed.Message?.PositionReport?.let { report ->
                existing.lat = report.Latitude
                existing.lng = report.Longitude
                existing.speed = report.Sog
                // Per ITU-R M.1371, 511 is TrueHeading's own "not available" sentinel (most small
                // craft have no gyrocompass and never report a real one) and 360 is Cog's — a
                // non-null field doesn't mean a valid bearing. Falling through to a genuinely
                // usable value keeps DeadReckoning.project() from sailing a real, moving ship off
                // in whatever direction 511°/360° happens to reduce to mod 360.
                existing.heading = report.TrueHeading?.takeIf { it in 0.0..359.0 }
                    ?: report.Cog?.takeIf { it in 0.0..359.9 }
                existing.navStatus = report.NavigationalStatus
                existing.timestamp = System.currentTimeMillis()
            }
            "ShipStaticData" -> parsed.Message?.ShipStaticData?.let { data ->
                data.Name?.trim()?.takeIf { it.isNotEmpty() }?.let { existing.name = it }
                data.Destination?.trim()?.takeIf { it.isNotEmpty() }?.let { existing.destination = it }
                existing.type = osirisShipType(data.Type)
                // "@" is AIS's own space-padding character for unset text fields — a callsign of
                // all-"@" means "not available", not a literal callsign.
                data.CallSign?.trim()?.trim('@')?.takeIf { it.isNotEmpty() }?.let { existing.callSign = it }
                data.ImoNumber?.takeIf { it > 0 }?.let { existing.imo = it }
                data.Dimension?.let { dim ->
                    if (dim.A != null && dim.B != null) existing.lengthM = (dim.A + dim.B).toDouble()
                    if (dim.C != null && dim.D != null) existing.widthM = (dim.C + dim.D).toDouble()
                }
                data.MaximumStaticDraught?.takeIf { it > 0 }?.let { existing.draughtM = it }
                data.Eta?.let { eta ->
                    existing.etaMonth = eta.Month
                    existing.etaDay = eta.Day
                    existing.etaHour = eta.Hour
                    existing.etaMinute = eta.Minute
                }
            }
            else -> {}
        }

        if (existing.lat == null || existing.lng == null) return
        synchronized(shipsCache) {
            if (shipsCache.size > MAX_SHIPS) {
                val firstKey = shipsCache.keys.firstOrNull()
                if (firstKey != null) shipsCache.remove(firstKey)
            }
        }
    }

    // ── Static reference data ──────────────────────────────────────────

    private data class PortDef(
        val name: String, val country: String, val lat: Double, val lng: Double,
        val type: String, val volume: String, val rank: Int? = null, val fleet: String? = null,
    )

    private data class ChokepointDef(val name: String, val lat: Double, val lng: Double, val traffic: String, val risk: String)

    private val PORTS = listOf(
        PortDef("Shanghai", "CN", 31.23, 121.47, "container", "47.3M TEU", rank = 1),
        PortDef("Singapore", "SG", 1.26, 103.84, "container", "37.2M TEU", rank = 2),
        PortDef("Ningbo-Zhoushan", "CN", 29.87, 121.55, "container", "33.3M TEU", rank = 3),
        PortDef("Shenzhen", "CN", 22.54, 114.05, "container", "30.0M TEU", rank = 4),
        PortDef("Guangzhou", "CN", 23.08, 113.32, "container", "24.2M TEU", rank = 5),
        PortDef("Busan", "KR", 35.10, 129.04, "container", "22.7M TEU", rank = 6),
        PortDef("Qingdao", "CN", 36.07, 120.38, "container", "22.0M TEU", rank = 7),
        PortDef("Rotterdam", "NL", 51.90, 4.50, "container", "14.5M TEU", rank = 8),
        PortDef("Tokyo", "JP", 35.61, 139.79, "container", "4.5M TEU"),
        PortDef("Yokohama", "JP", 35.45, 139.66, "container", "2.9M TEU"),
        PortDef("Kobe", "JP", 34.67, 135.21, "container", "2.8M TEU"),
        PortDef("Nagoya", "JP", 35.08, 136.87, "container", "2.6M TEU"),
        PortDef("Osaka", "JP", 34.63, 135.41, "container", "2.1M TEU"),
        PortDef("Hakata (Fukuoka)", "JP", 33.60, 130.40, "container", "0.9M TEU"),
        PortDef("Kitakyushu", "JP", 33.91, 130.93, "container", "0.5M TEU"),
        PortDef("Shimizu", "JP", 35.00, 138.50, "container", "0.5M TEU"),
        PortDef("Tomakomai", "JP", 42.63, 141.63, "container", "0.4M TEU"),
        PortDef("Niigata", "JP", 37.95, 139.06, "container", "0.2M TEU"),
        PortDef("Sendai", "JP", 38.27, 141.02, "container", "0.2M TEU"),
        PortDef("Mizushima", "JP", 34.50, 133.72, "energy", "Industrial"),
        PortDef("Yokkaichi", "JP", 34.95, 136.65, "energy", "Industrial"),
        PortDef("Dubai (Jebel Ali)", "AE", 25.01, 55.06, "container", "14.0M TEU", rank = 9),
        PortDef("Port Klang", "MY", 2.99, 101.39, "container", "13.2M TEU", rank = 10),
        PortDef("Antwerp", "BE", 51.30, 4.40, "container", "12.0M TEU", rank = 11),
        PortDef("Xiamen", "CN", 24.48, 118.09, "container", "11.4M TEU", rank = 12),
        PortDef("Hamburg", "DE", 53.55, 9.97, "container", "8.7M TEU", rank = 14),
        PortDef("Los Angeles", "US", 33.74, -118.27, "container", "9.9M TEU", rank = 13),
        PortDef("Long Beach", "US", 33.75, -118.19, "container", "8.0M TEU", rank = 15),
        PortDef("Tanjung Pelepas", "MY", 1.36, 103.55, "container", "9.8M TEU", rank = 16),
        PortDef("Savannah", "US", 32.08, -81.09, "container", "5.6M TEU", rank = 20),
        PortDef("Felixstowe", "GB", 51.96, 1.35, "container", "3.8M TEU", rank = 25),
        PortDef("Santos", "BR", -23.95, -46.31, "container", "4.2M TEU", rank = 22),
        PortDef("Colombo", "LK", 6.94, 79.84, "container", "7.2M TEU", rank = 17),
        PortDef("Ras Tanura", "SA", 26.64, 50.16, "energy", "6.5M bpd"),
        PortDef("Fujairah", "AE", 25.14, 56.35, "energy", "3.5M bpd"),
        PortDef("Novorossiysk", "RU", 44.72, 37.77, "energy", "2.8M bpd"),
        PortDef("Houston Ship Channel", "US", 29.73, -95.27, "energy", "2.5M bpd"),
        PortDef("Kharg Island", "IR", 29.24, 50.33, "energy", "2.0M bpd"),
        PortDef("Primorsk", "RU", 60.35, 28.70, "energy", "1.6M bpd"),
        PortDef("Norfolk Naval Station", "US", 36.95, -76.33, "naval", "", fleet = "US Atlantic Fleet"),
        PortDef("San Diego Naval Base", "US", 32.69, -117.15, "naval", "", fleet = "US Pacific Fleet"),
        PortDef("Pearl Harbor", "US", 21.35, -157.97, "naval", "", fleet = "US Pacific Fleet"),
        PortDef("Yokosuka", "JP", 35.28, 139.67, "naval", "", fleet = "US 7th Fleet"),
        PortDef("Severomorsk", "RU", 69.07, 33.42, "naval", "", fleet = "Russian Northern Fleet"),
        PortDef("Tartus", "SY", 34.89, 35.89, "naval", "", fleet = "Russian Mediterranean"),
        PortDef("Zhanjiang", "CN", 21.20, 110.39, "naval", "", fleet = "PLA Navy South Sea Fleet"),
        PortDef("Qingdao Naval", "CN", 36.09, 120.43, "naval", "", fleet = "PLA Navy North Sea Fleet"),
        PortDef("Portsmouth", "GB", 50.80, -1.11, "naval", "", fleet = "Royal Navy"),
        PortDef("Toulon", "FR", 43.12, 5.93, "naval", "", fleet = "French Navy Mediterranean"),
        PortDef("Changi Naval Base", "SG", 1.33, 104.01, "naval", "", fleet = "Republic of Singapore Navy"),
        PortDef("Visakhapatnam", "IN", 17.69, 83.30, "naval", "", fleet = "Indian Navy Eastern Command"),
        PortDef("Mumbai Naval", "IN", 18.93, 72.84, "naval", "", fleet = "Indian Navy Western Command"),
    )

    private val CHOKEPOINTS = listOf(
        ChokepointDef("Strait of Hormuz", 26.57, 56.25, "21M bpd oil", "HIGH"),
        ChokepointDef("Strait of Malacca", 2.50, 101.50, "16M bpd oil", "MODERATE"),
        ChokepointDef("Suez Canal", 30.43, 32.34, "12% world trade", "ELEVATED"),
        ChokepointDef("Bab el-Mandeb", 12.58, 43.33, "6.2M bpd oil", "CRITICAL"),
        ChokepointDef("Panama Canal", 9.08, -79.68, "5% world trade", "LOW"),
        ChokepointDef("Turkish Straits", 41.12, 29.07, "3M bpd oil", "MODERATE"),
        ChokepointDef("Danish Straits", 55.70, 12.60, "3.2M bpd oil", "LOW"),
        ChokepointDef("Cape of Good Hope", -34.36, 18.47, "Alt route Suez", "LOW"),
        ChokepointDef("Taiwan Strait", 24.00, 119.00, "88% large ships", "ELEVATED"),
        ChokepointDef("Lombok Strait", -8.47, 115.72, "Alt Malacca", "LOW"),
    )

    private fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dx = (lng1 - lng2) * cos((lat1 + lat2) / 2 * Math.PI / 180)
        val dy = lat1 - lat2
        return sqrt(dx * dx + dy * dy) * 111.32
    }

    /** Runs on [Dispatchers.Default] — scanning every port/chokepoint against every cached ship
     * is up to ~1.4M distance calculations at the 20k-ship cap, fine off the main thread but not
     * worth risking a jank on it. */
    suspend fun fetch(): MaritimeResponse = withContext(Dispatchers.Default) {
        ensureConnected()

        val now = System.currentTimeMillis()
        val ships = synchronized(shipsCache) {
            shipsCache.entries.removeAll { now - it.value.timestamp > STALE_MS }
            shipsCache.values.toList()
        }
        Log.d(TAG, "fetch(): ${ships.size} ships in cache (connected=${webSocket != null}, $messagesReceived messages received total)")

        val dynamicPorts = PORTS.map { port ->
            var nearby = 0
            var waiting = 0
            for (ship in ships) {
                val lat = ship.lat ?: continue
                val lng = ship.lng ?: continue
                if (distanceKm(port.lat, port.lng, lat, lng) < 50) {
                    nearby++
                    if ((ship.speed ?: 0.0) < 0.5 && ship.type != "military") waiting++
                }
            }
            val congestionRatio = if (nearby > 0) waiting.toDouble() / nearby else 0.0
            val (status, dwell) = when {
                congestionRatio > 0.6 || waiting > 30 -> "SEVERE" to "7+ Days"
                congestionRatio > 0.4 || waiting > 15 -> "CONGESTED" to "3-5 Days"
                else -> "NORMAL" to "1-2 Days"
            }
            Port(
                name = port.name, country = port.country, lat = port.lat, lng = port.lng, type = port.type,
                volume = if (port.volume.isNotEmpty()) "${port.volume} | LIVE: $nearby (WAITING: $waiting)" else null,
                congestion = status, rank = port.rank, fleet = port.fleet, dwellTime = dwell,
            )
        }

        val dynamicChokepoints = CHOKEPOINTS.map { choke ->
            var nearby = 0
            for (ship in ships) {
                val lat = ship.lat ?: continue
                val lng = ship.lng ?: continue
                if (distanceKm(choke.lat, choke.lng, lat, lng) < 100) nearby++
            }
            val risk = when {
                nearby > 50 -> "CRITICAL"
                nearby > 20 && choke.risk != "CRITICAL" -> "HIGH"
                nearby > 5 && choke.risk == "LOW" -> "ELEVATED"
                else -> choke.risk
            }
            Chokepoint(name = choke.name, lat = choke.lat, lng = choke.lng, traffic = "${choke.traffic} | LIVE SHIPS: $nearby", risk = risk)
        }

        val shipDtos = ships.mapNotNull { s ->
            val lat = s.lat ?: return@mapNotNull null
            val lng = s.lng ?: return@mapNotNull null
            Ship(
                id = s.mmsi, mmsi = s.mmsi, lat = lat, lng = lng, speed = s.speed, heading = s.heading,
                name = s.name, destination = s.destination, type = s.type,
                navStatus = s.navStatus, callSign = s.callSign, imo = s.imo,
                lengthM = s.lengthM, widthM = s.widthM, draughtM = s.draughtM,
                etaText = formatEta(s.etaMonth, s.etaDay, s.etaHour, s.etaMinute),
            )
        }

        MaritimeResponse(
            ports = dynamicPorts,
            chokepoints = dynamicChokepoints,
            ships = shipDtos,
            totalPorts = dynamicPorts.size,
            totalChokepoints = dynamicChokepoints.size,
            totalShips = shipDtos.size,
        )
    }
}
