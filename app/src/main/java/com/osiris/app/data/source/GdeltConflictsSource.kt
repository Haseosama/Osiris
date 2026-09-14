package com.osiris.app.data.source

import com.osiris.app.data.model.ConflictZone
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Known conflict zones enriched with live event counts from world-news RSS, called directly
 * from the phone — mirrors `osiris-backend/src/app/api/conflicts/route.ts`. Despite the
 * backend's own doc-comment claiming GDELT, the actual code only ever scrapes 3 RSS feeds and
 * keyword-matches headlines against this same fixed zone list — no GDELT API involved, keyless
 * either way. On any fetch failure, falls back to the static zone list with zero live counts,
 * same graceful-degradation the backend used.
 */
object GdeltConflictsSource {

    private data class ZoneDef(
        val id: String, val label: String, val severity: String,
        val lat: Double, val lng: Double, val region: String,
        val description: String, val sourceUrl: String,
        val queries: List<String>,
        val minLat: Double, val maxLat: Double, val minLng: Double, val maxLng: Double,
    )

    private val KNOWN_CONFLICTS = listOf(
        ZoneDef("ukraine", "UKRAINE WAR", "war", 48.5, 31.2, "ukraine",
            "Ongoing Russian invasion of Ukraine — active frontlines across eastern and southern regions.",
            "https://liveuamap.com/", listOf("ukraine war", "ukraine attack", "ukraine frontline"), 44.0, 53.0, 22.0, 40.0),
        ZoneDef("gaza", "GAZA CONFLICT", "war", 31.35, 34.35, "gaza",
            "Active military operations and humanitarian crisis in Gaza Strip.",
            "https://israelpalestine.liveuamap.com/", listOf("gaza attack", "gaza airstrike", "israel hamas"), 31.0, 32.0, 34.0, 34.8),
        ZoneDef("lebanon", "LEBANON BORDER", "high", 33.377, 35.483, "lebanon",
            "Active cross-border military operations in southern Lebanon.",
            "https://lebanon.liveuamap.com/", listOf("lebanon airstrike", "hezbollah attack", "lebanon military"), 33.0, 34.5, 35.0, 36.5),
        ZoneDef("sudan", "SUDAN CIVIL WAR", "war", 15.0, 30.0, "sudan",
            "Armed conflict between SAF and RSF factions across Sudan.",
            "https://sudan.liveuamap.com/", listOf("sudan war", "sudan conflict", "RSF SAF"), 10.0, 22.0, 22.0, 38.0),
        ZoneDef("myanmar", "MYANMAR CONFLICT", "war", 19.5, 96.5, "myanmar",
            "Internal conflict — military junta vs opposition forces.",
            "https://myanmar.liveuamap.com/", listOf("myanmar conflict", "myanmar military", "myanmar junta"), 10.0, 28.0, 92.0, 101.0),
        ZoneDef("yemen", "YEMEN WAR", "war", 15.5, 48.0, "yemen",
            "Houthi militant operations, Red Sea maritime threats, and coalition strikes.",
            "https://yemen.liveuamap.com/", listOf("yemen houthi", "red sea attack", "yemen strike"), 12.0, 20.0, 42.0, 55.0),
        ZoneDef("syria", "SYRIA", "high", 35.0, 38.5, "syria",
            "Ongoing civil conflict and localized insurgencies.",
            "https://syria.liveuamap.com/", listOf("syria attack", "syria military", "syria conflict"), 32.0, 37.0, 35.0, 42.0),
        ZoneDef("drc", "DRC EASTERN CONFLICT", "war", -1.0, 28.5, "drc",
            "M23 rebel offensive and regional instability in eastern Congo.",
            "https://drc.liveuamap.com/", listOf("congo conflict", "M23 DRC", "congo attack"), -5.0, 5.0, 25.0, 32.0),
        ZoneDef("red-sea", "RED SEA THREAT", "high", 16.0, 40.0, "red-sea",
            "Houthi anti-ship missile and drone attacks on maritime traffic.",
            "https://yemen.liveuamap.com/", listOf("red sea ship attack", "houthi missile ship"), 12.0, 22.0, 36.0, 44.0),
        ZoneDef("taiwan-strait", "TAIWAN STRAIT", "elevated", 24.0, 119.5, "taiwan",
            "Elevated military drills and regional tension.",
            "https://china.liveuamap.com/", listOf("taiwan strait military", "china taiwan"), 22.0, 26.0, 117.0, 122.0),
        ZoneDef("korean-dmz", "KOREAN DMZ", "elevated", 38.3, 127.0, "korea",
            "Ongoing cross-border tension and military posturing.",
            "https://liveuamap.com/", listOf("north korea military", "korean dmz"), 37.0, 39.5, 124.0, 130.0),
        ZoneDef("sahel", "SAHEL INSTABILITY", "high", 14.0, 5.0, "sahel",
            "Insurgencies and military coups across Mali, Burkina Faso, Niger.",
            "https://africa.liveuamap.com/", listOf("sahel insurgency", "mali burkina niger conflict"), 10.0, 20.0, -5.0, 15.0),
        ZoneDef("somalia", "SOMALIA", "high", 5.0, 46.0, "somalia",
            "Al-Shabaab insurgency and counter-terrorism operations.",
            "https://africa.liveuamap.com/", listOf("somalia al-shabaab", "somalia attack"), -2.0, 12.0, 40.0, 52.0),
        ZoneDef("iraq", "IRAQ INSTABILITY", "elevated", 33.3, 44.4, "iraq",
            "Ongoing militia activity and counter-terrorism operations.",
            "https://iraq.liveuamap.com/", listOf("iraq militia", "iraq attack", "iraq isis"), 29.0, 37.5, 38.0, 49.0),
        ZoneDef("ethiopia", "ETHIOPIA", "elevated", 9.0, 38.7, "ethiopia",
            "Ethnic tensions and regional conflicts across multiple regions.",
            "https://africa.liveuamap.com/", listOf("ethiopia conflict", "tigray amhara"), 3.0, 15.0, 33.0, 48.0),
    )

    private val RSS_FEEDS = listOf(
        "http://feeds.bbci.co.uk/news/world/rss.xml",
        "https://www.aljazeera.com/xml/rss/all.xml",
        "https://rss.nytimes.com/services/xml/rss/nyt/World.xml",
    )

    private val RSS_HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.5",
    )

    private data class NewsItem(val title: String, val link: String, val desc: String)

    suspend fun fetch(): List<ConflictZone> {
        val eventsByRegion = runCatching { countLiveEventsByRegion() }.getOrDefault(emptyMap())

        return KNOWN_CONFLICTS.map { zone ->
            ConflictZone(
                id = zone.id,
                label = zone.label,
                severity = zone.severity,
                lat = zone.lat,
                lng = zone.lng,
                description = zone.description,
                sourceUrl = zone.sourceUrl,
                region = zone.region,
                eventCount = eventsByRegion[zone.id] ?: 0,
                lastUpdated = java.time.Instant.now().toString(),
            )
        }
    }

    private suspend fun countLiveEventsByRegion(): Map<String, Int> = coroutineScope {
        val feedDeferreds = RSS_FEEDS.map { url -> async { runCatching { fetchFeedItems(url) }.getOrDefault(emptyList()) } }
        val items = feedDeferreds.flatMap { it.await() }

        val seenTitles = mutableSetOf<String>()
        val counts = mutableMapOf<String, Int>()

        for (item in items) {
            val title = item.title.take(150)
            if (!seenTitles.add(title)) continue // dedupe by title across feeds

            val searchText = "${item.title} ${item.desc}".lowercase()
            val zone = KNOWN_CONFLICTS.firstOrNull { z ->
                z.queries.any { q ->
                    val terms = q.lowercase().split(" ")
                    terms.all { term -> searchText.contains(term) } || searchText.contains(z.region)
                }
            } ?: continue

            counts[zone.id] = (counts[zone.id] ?: 0) + 1
        }
        counts
    }

    private suspend fun fetchFeedItems(url: String): List<NewsItem> {
        val xml = DirectHttp.getText(url, headers = RSS_HEADERS)
        val rawItems = xml.split(Regex("<item>", RegexOption.IGNORE_CASE)).drop(1)
        return rawItems.mapNotNull { raw ->
            val item = raw.split(Regex("</item>", RegexOption.IGNORE_CASE)).first()
            val title = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE).find(item)?.groupValues?.get(1)
                ?: Regex("<title><!\\[CDATA\\[(.*?)]]></title>", RegexOption.IGNORE_CASE).find(item)?.groupValues?.get(1)
                ?: return@mapNotNull null
            val link = Regex("<link>(.*?)</link>", RegexOption.IGNORE_CASE).find(item)?.groupValues?.get(1).orEmpty()
            val desc = Regex("<description>(.*?)</description>", RegexOption.IGNORE_CASE).find(item)?.groupValues?.get(1)
                ?: Regex("<description><!\\[CDATA\\[(.*?)]]></description>", RegexOption.IGNORE_CASE).find(item)?.groupValues?.get(1)
                ?: ""
            NewsItem(
                title = title.replace(Regex("<[^>]*>"), "").trim(),
                link = link,
                desc = desc.replace(Regex("<[^>]*>"), "").trim(),
            )
        }
    }
}
