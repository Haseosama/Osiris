package com.osiris.app.data.source

import com.osiris.app.data.model.CyberAttack
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Live "cyberattack" arcs from abuse.ch's Feodo Tracker C2 blocklist, called directly from the
 * phone — mirrors `osiris-backend/src/app/api/cyber-attacks/route.ts`. The feed only lists C2
 * hosts, not real attributed attacks — the origin side, malware→region attribution, severity and
 * jitter are all cosmetic (same lookup tables the backend used), purely to make the animated
 * arcs feel alive rather than one dot per feed entry.
 */
object FeodoTrackerSource {

    private const val URL = "https://feodotracker.abuse.ch/downloads/ipblocklist.json"
    private const val TARGET_ARCS = 15
    private const val MAX_ARCS = 20

    private val THREAT_ORIGINS: Map<String, List<Pair<Double, Double>>> = mapOf(
        "Emotet" to listOf(37.6 to 55.7, 30.5 to 50.4, 24.1 to 56.9, 21.0 to 52.2),
        "QakBot" to listOf(37.6 to 55.7, 49.1 to 55.8, 30.3 to 59.9, 27.6 to 53.9),
        "Qakbot" to listOf(37.6 to 55.7, 49.1 to 55.8, 30.3 to 59.9, 27.6 to 53.9),
        "BumbleBee" to listOf(37.6 to 55.7, 24.1 to 56.9, 14.4 to 50.1),
        "Dridex" to listOf(37.6 to 55.7, 30.5 to 50.4, 49.1 to 55.8),
        "TrickBot" to listOf(37.6 to 55.7, 30.5 to 50.4, 68.0 to 55.0),
        "IcedID" to listOf(37.6 to 55.7, 24.1 to 56.9, 30.3 to 59.9),
        "SystemBC" to listOf(37.6 to 55.7, 14.4 to 50.1, 21.0 to 52.2),
        "Pikabot" to listOf(37.6 to 55.7, 30.5 to 50.4, 24.1 to 56.9),
        "BazarLoader" to listOf(37.6 to 55.7, 49.1 to 55.8),
        "CobaltStrike" to listOf(116.4 to 39.9, 121.5 to 31.2, 37.6 to 55.7, 113.3 to 23.1),
        "PlugX" to listOf(116.4 to 39.9, 121.5 to 31.2, 113.3 to 23.1),
        "ShadowPad" to listOf(116.4 to 39.9, 104.1 to 30.6, 106.7 to 26.6),
        "Winnti" to listOf(116.4 to 39.9, 121.5 to 31.2),
        "_default" to listOf(37.6 to 55.7, 116.4 to 39.9, -73.9 to 40.7, -46.6 to -23.5, 28.0 to -26.2, 103.8 to 1.4),
    )

    private val COUNTRY_COORDS: Map<String, Pair<Double, Double>> = mapOf(
        "AF" to (65.0 to 33.0), "AL" to (20.0 to 41.0), "DZ" to (3.0 to 28.0), "AO" to (18.5 to -12.5),
        "AR" to (-64.0 to -34.0), "AM" to (45.0 to 40.0), "AU" to (134.0 to -25.0), "AT" to (14.0 to 47.5),
        "AZ" to (50.0 to 40.5), "BD" to (90.0 to 24.0), "BY" to (28.0 to 53.0), "BE" to (4.0 to 50.8),
        "BR" to (-51.0 to -10.0), "BG" to (25.5 to 42.7), "CA" to (-96.0 to 62.0), "CL" to (-71.0 to -30.0),
        "CN" to (105.0 to 35.0), "CO" to (-72.0 to 4.0), "HR" to (16.0 to 45.2), "CZ" to (15.5 to 49.8),
        "DK" to (10.0 to 56.0), "EG" to (30.0 to 27.0), "FI" to (26.0 to 64.0), "FR" to (2.0 to 46.0),
        "DE" to (10.0 to 51.0), "GR" to (22.0 to 39.0), "HK" to (114.2 to 22.3), "HU" to (19.5 to 47.0),
        "IN" to (79.0 to 22.0), "ID" to (120.0 to -5.0), "IR" to (53.0 to 32.0), "IQ" to (44.0 to 33.0),
        "IE" to (-8.0 to 53.0), "IL" to (34.8 to 31.5), "IT" to (12.5 to 42.8), "JP" to (138.0 to 36.0),
        "KZ" to (67.0 to 48.0), "KE" to (38.0 to 1.0), "KR" to (128.0 to 36.0), "LT" to (24.0 to 55.5),
        "MY" to (112.0 to 3.0), "MX" to (-102.0 to 23.5), "NL" to (5.5 to 52.5), "NZ" to (174.0 to -41.0),
        "NG" to (8.0 to 10.0), "NO" to (8.0 to 62.0), "PK" to (70.0 to 30.0), "PA" to (-80.0 to 9.0),
        "PH" to (122.0 to 12.5), "PL" to (19.5 to 52.0), "PT" to (-8.0 to 39.5), "RO" to (25.0 to 46.0),
        "RU" to (100.0 to 60.0), "SA" to (45.0 to 25.0), "SG" to (103.8 to 1.35), "ZA" to (24.0 to -29.0),
        "ES" to (-4.0 to 40.0), "SE" to (16.0 to 62.0), "CH" to (8.0 to 47.0), "TW" to (121.0 to 23.7),
        "TH" to (101.0 to 15.0), "TR" to (35.0 to 39.0), "UA" to (32.0 to 49.0), "AE" to (54.0 to 24.0),
        "GB" to (-2.0 to 54.0), "US" to (-97.0 to 38.0), "VN" to (106.0 to 16.0),
    )

    private val SEVERITY: Map<String, Int> = mapOf(
        "Emotet" to 9, "QakBot" to 8, "Qakbot" to 8, "Dridex" to 8, "TrickBot" to 7, "IcedID" to 7,
        "BumbleBee" to 7, "CobaltStrike" to 10, "SystemBC" to 6, "Pikabot" to 7, "BazarLoader" to 8,
        "PlugX" to 9, "ShadowPad" to 10, "Winnti" to 9,
    )

    private val ATTACK_VERBS = listOf(
        "C2 BEACON", "PAYLOAD DROP", "EXFILTRATION", "LATERAL MOVE", "CREDENTIAL HARVEST",
        "IMPLANT DEPLOY", "REVERSE SHELL", "DATA STAGING", "PERSISTENCE", "RECON SWEEP",
    )

    @Serializable
    private data class FeodoEntry(
        val country: String? = null,
        val malware: String? = null,
        val ip_address: String? = null,
    )

    suspend fun fetch(): List<CyberAttack> {
        val raw = runCatching {
            DirectHttp.getJson<List<FeodoEntry>>(URL, headers = mapOf("User-Agent" to "OSIRIS/1.0"))
        }.getOrNull() ?: return emptyList()

        val entries = raw.filter { it.country != null && COUNTRY_COORDS.containsKey(it.country) }
        if (entries.isEmpty()) return emptyList()

        val multiplier = maxOf(1, ceilDiv(TARGET_ARCS, entries.size))
        val attacks = mutableListOf<CyberAttack>()
        var id = 0

        for (entry in entries) {
            if (attacks.size >= MAX_ARCS) break
            val malware = entry.malware ?: "Unknown"
            val origins = THREAT_ORIGINS[malware] ?: THREAT_ORIGINS.getValue("_default")
            val dst = COUNTRY_COORDS[entry.country] ?: continue

            for (m in 0 until multiplier) {
                if (attacks.size >= MAX_ARCS) break
                val origin = origins[(id + m) % origins.size]
                val jSrcLng = (Random.nextDouble() - 0.5) * 8
                val jSrcLat = (Random.nextDouble() - 0.5) * 5
                val jDstLng = (Random.nextDouble() - 0.5) * 6
                val jDstLat = (Random.nextDouble() - 0.5) * 4

                attacks += CyberAttack(
                    id = "ca-$id",
                    srcLng = origin.first + jSrcLng,
                    srcLat = origin.second + jSrcLat,
                    dstLng = dst.first + jDstLng,
                    dstLat = dst.second + jDstLat,
                    malware = malware,
                    targetIp = entry.ip_address ?: "0.0.0.0",
                    targetCountry = entry.country,
                    severity = SEVERITY[malware] ?: 5,
                    action = ATTACK_VERBS[Random.nextInt(ATTACK_VERBS.size)],
                )
                id++
            }
        }
        return attacks
    }

    private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b
}
