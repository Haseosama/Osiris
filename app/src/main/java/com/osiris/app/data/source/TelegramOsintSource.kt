package com.osiris.app.data.source

import com.osiris.app.data.model.OsintPost
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.security.MessageDigest
import java.time.Instant

/**
 * Geoparsed Telegram OSINT feed, called directly from the phone — mirrors
 * `osiris-backend/src/app/api/news/route.ts` (despite the route's own name, this is not the
 * static YouTube stream list [com.osiris.app.data.repository.LiveNewsRepository]/
 * [MapLayer.NEWS] already covers — see [com.osiris.app.data.model.OsintPost]'s own doc
 * comment). Scrapes `t.me/s/{channel}` HTML for
 * four channels, falling back to a handful of RSS feeds only if Telegram blocks this scan
 * entirely; a fragile regex-based scrape by nature (no public API for this), same trade-off the
 * backend already accepted. Every post gets a keyword-based risk score and, when a place name
 * from a small fixed table appears in the text, a map coordinate — identical heuristics to the
 * backend, not real geoparsing.
 */
object TelegramOsintSource {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val CHANNELS = listOf("OSINTtechnical", "Faytuks", "Liveuamap", "CyberKnow")

    private val FALLBACK_FEEDS = linkedMapOf(
        "BBC" to "https://feeds.bbci.co.uk/news/world/rss.xml",
        "AlJazeera" to "https://www.aljazeera.com/xml/rss/all.xml",
        "GDACS" to "https://www.gdacs.org/xml/rss.xml",
    )

    private val RISK_KEYWORDS = listOf(
        "war", "missile", "strike", "attack", "crisis", "tension", "military", "conflict",
        "defense", "clash", "nuclear", "invasion", "bomb", "drone", "weapon", "sanctions",
        "ceasefire", "escalation", "killed", "destroyed", "operation", "casualty", "frontline",
        "threat",
    )

    private val KEYWORD_COORDS = linkedMapOf(
        "ukraine" to (49.487 to 31.272), "kyiv" to (50.450 to 30.523), "russia" to (61.524 to 105.318),
        "moscow" to (55.755 to 37.617), "israel" to (31.046 to 34.851), "gaza" to (31.416 to 34.333),
        "iran" to (32.427 to 53.688), "lebanon" to (33.854 to 35.862), "syria" to (34.802 to 38.996),
        "yemen" to (15.552 to 48.516), "china" to (35.861 to 104.195), "taiwan" to (23.697 to 120.960),
        "united states" to (38.907 to -77.036), "europe" to (48.800 to 2.300),
        "middle east" to (31.500 to 34.800),
    )

    private data class RawArticle(
        val title: String,
        val description: String,
        val link: String,
        val pubDate: String,
        val source: String,
    )

    private val MESSAGE_BLOCK_REGEX = Regex(
        "<div class=\"tgme_widget_message_wrap js-widget_message_wrap\"[\\s\\S]*?</div>\\s*</div>\\s*</div>",
        RegexOption.IGNORE_CASE,
    )
    private val TEXT_REGEX = Regex(
        "<div class=\"tgme_widget_message_text[^>]*>([\\s\\S]*?)</div>",
        RegexOption.IGNORE_CASE,
    )
    private val DATE_REGEX = Regex(
        "<a class=\"tgme_widget_message_date\" href=\"(https://t\\.me/[^\"]+)\".*?<time datetime=\"([^\"]+)\"",
        RegexOption.IGNORE_CASE,
    )
    private val BR_TAG_REGEX = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val ANY_TAG_REGEX = Regex("<[^>]+>")
    private val RSS_ITEM_REGEX = Regex("<item>([\\s\\S]*?)</item>", RegexOption.IGNORE_CASE)

    suspend fun fetch(): List<OsintPost> {
        var articles = coroutineScope {
            CHANNELS.map { channel -> async { fetchChannel(channel) } }.map { it.await() }
        }.flatten()

        // FAILSAFE: if Telegram blocks this connection entirely, fall back to traditional RSS —
        // same as the backend, just from the phone's IP instead of the server's.
        if (articles.isEmpty()) {
            articles = coroutineScope {
                FALLBACK_FEEDS.map { (source, url) -> async { fetchRss(source, url) } }.map { it.await() }
            }.flatten()
        }

        val posts = articles.map { article ->
            val text = article.description.ifBlank { article.title }
            val riskScore = scoreRisk(text)
            val coords = findCoords(text)
            OsintPost(
                id = md5("${article.link}${article.pubDate}"),
                title = article.title,
                description = article.description,
                link = article.link,
                published = article.pubDate,
                source = article.source,
                riskScore = riskScore,
                coords = coords?.let { listOf(it.first, it.second) },
                coordsDefault = coords == null,
                machineAssessment = if (riskScore >= 8) {
                    "AI Analysis indicates elevated tactical priority based on OSINT stream patterns."
                } else {
                    null
                },
            )
        }

        return posts.sortedByDescending { post ->
            runCatching { Instant.parse(post.published) }.getOrDefault(Instant.EPOCH)
        }
    }

    private suspend fun fetchChannel(channel: String): List<RawArticle> = runCatching {
        val html = DirectHttp.getText("https://t.me/s/$channel", headers = mapOf("User-Agent" to USER_AGENT))
        parseTelegramHtml(html, channel).takeLast(8)
    }.getOrDefault(emptyList())

    private fun parseTelegramHtml(html: String, channel: String): List<RawArticle> {
        val items = mutableListOf<RawArticle>()
        for (blockMatch in MESSAGE_BLOCK_REGEX.findAll(html)) {
            val blockHtml = blockMatch.value
            val textMatch = TEXT_REGEX.find(blockHtml) ?: continue
            val text = textMatch.groupValues[1]
                .replace(BR_TAG_REGEX, "\n")
                .replace(ANY_TAG_REGEX, "")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .trim()
            if (text.length < 10) continue

            val dateMatch = DATE_REGEX.find(blockHtml)
            val link = dateMatch?.groupValues?.get(1) ?: "https://t.me/$channel"
            val pubDate = dateMatch?.groupValues?.get(2) ?: Instant.now().toString()
            val title = text.substringBefore("\n").take(100)

            items += RawArticle(title = title, description = text, link = link, pubDate = pubDate, source = "t.me/$channel")
        }
        return items
    }

    private suspend fun fetchRss(source: String, url: String): List<RawArticle> = runCatching {
        parseRssItems(DirectHttp.getText(url), source).take(5)
    }.getOrDefault(emptyList())

    private fun parseRssItems(xml: String, sourceName: String): List<RawArticle> {
        val items = mutableListOf<RawArticle>()
        for (match in RSS_ITEM_REGEX.findAll(xml)) {
            val itemXml = match.groupValues[1]
            val rawTitle = rssTag(itemXml, "title").replace(ANY_TAG_REGEX, "")
            val desc = rssTag(itemXml, "description").replace(ANY_TAG_REGEX, "").replace("&quot;", "\"")
            val title = if (rawTitle.length > 100) rawTitle.take(100) + "..." else rawTitle

            items += RawArticle(
                title = title,
                description = desc,
                link = rssTag(itemXml, "link"),
                pubDate = rssTag(itemXml, "pubDate").ifBlank { Instant.now().toString() },
                source = sourceName,
            )
        }
        return items
    }

    private fun rssTag(itemXml: String, tag: String): String {
        val match = Regex(
            "<$tag[^>]*><!\\[CDATA\\[([\\s\\S]*?)\\]\\]></$tag>|<$tag[^>]*>([\\s\\S]*?)</$tag>",
            RegexOption.IGNORE_CASE,
        ).find(itemXml) ?: return ""
        return match.groupValues[1].ifEmpty { match.groupValues[2] }.trim()
    }

    private fun scoreRisk(text: String): Int {
        val lower = text.lowercase()
        var score = 1
        RISK_KEYWORDS.forEach { if (lower.contains(it)) score += 2 }
        return score.coerceAtMost(10)
    }

    private fun findCoords(text: String): Pair<Double, Double>? {
        val lower = text.lowercase()
        return KEYWORD_COORDS.entries.firstOrNull { (keyword, _) -> lower.contains(keyword) }?.value
    }

    private fun md5(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
