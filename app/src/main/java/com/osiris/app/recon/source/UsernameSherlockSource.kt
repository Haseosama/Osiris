package com.osiris.app.recon.source

import com.osiris.app.data.remote.NetworkModule
import com.osiris.app.data.source.DirectHttp
import com.osiris.app.recon.SiteHit
import com.osiris.app.recon.UsernameScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Username enumeration across social platforms, called directly from the phone — a Kotlin
 * port of `osiris-backend/src/lib/sherlock.ts`, itself a reimplementation of the detection
 * logic from the Sherlock Project (MIT) driven by its site database. Keyless.
 *
 * The backend exposes `all`/`nsfw`/`limit`/`verify` query params; [ReconTool.USERNAME] has no
 * secondary param so the Android UI never sends them — this port only implements the default
 * behaviour those params default to (priority tier, no NSFW, no cap, verified). That also means
 * only the ~70 priority-tier site definitions ever need to be parsed out of the ~500-entry
 * upstream JSON, not the whole database — [loadSites] skips constructing a [SiteDef] for
 * everything else. */
object UsernameSherlockSource {

    private const val DATA_URL =
        "https://raw.githubusercontent.com/sherlock-project/sherlock/master/sherlock_project/resources/data.json"

    private val SANE_USERNAME = Regex("^[A-Za-z0-9._@-]{1,64}\$")

    private val BLOCKED_CODES = setOf(401, 403, 407, 429, 451, 503)

    private val CHALLENGE_MARKERS = listOf(
        "just a moment", "attention required", "cf-browser-verification",
        "enable javascript and cookies", "checking your browser", "access denied",
        "unusual traffic", "are you a robot", "px-captcha", "captcha-delivery",
    )

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36"

    private const val CONCURRENCY = 12
    private const val DB_TTL_MS = 6 * 60 * 60_000L

    /** High-signal platforms checked by default — mirrors the backend's `PRIORITY` tier. A full
     * ~500-site sweep is what the backend's `?all=1` is for; not offered here. */
    private val PRIORITY = listOf(
        "GitHub", "GitLab", "Reddit", "Instagram", "TikTok", "YouTube", "Twitch", "Steam",
        "Telegram", "Pinterest", "Tumblr", "Flickr", "SoundCloud", "Spotify", "Medium",
        "Patreon", "BuyMeACoffee", "Kick", "VK", "Vimeo", "Dribbble", "Behance", "DeviantART",
        "HackerNews", "HackerOne", "Keybase", "Kaggle", "Replit.com", "CodePen", "Codecademy",
        "Docker Hub", "npm", "PyPi", "RubyGems", "Bitbucket", "Launchpad", "CodersRank",
        "LeetCode", "Codeforces", "HackerEarth", "Chess.com", "Lichess", "Duolingo",
        "Last.fm", "Bandcamp", "MixCloud", "Genius", "Discogs", "Untappd", "Strava",
        "Trakt", "Letterboxd", "MyAnimeList", "Anilist", "Goodreads", "Wattpad",
        "Blogger", "WordPress", "Slideshare", "About.me", "Linktree", "Gravatar",
        "Roblox", "Fiverr", "Freelancer", "Wikipedia", "Archive.org", "Ask FM", "Snapchat",
    )

    private data class SiteDef(
        val url: String,
        val urlProbe: String?,
        val errorType: String,
        val errorMsgs: List<String>,
        val errorUrl: String?,
        val errorCodes: List<Int>,
        val regexCheck: String?,
        val requestMethod: String?,
        val requestPayload: JsonElement?,
        val headers: Map<String, String>,
    )

    private data class SiteResult(val site: String, val url: String, val status: String)

    private var dbCache: Triple<Long, Map<String, SiteDef>, Int>? = null
    private val httpClient = NetworkModule.okHttpClient.newBuilder()
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    fun isValidUsername(username: String): Boolean = SANE_USERNAME.matches(username)

    suspend fun lookup(usernameInput: String): UsernameScanResult {
        val started = System.currentTimeMillis()
        val username = usernameInput.trim()
        if (!isValidUsername(username)) {
            return UsernameScanResult(
                username = username,
                error = "Invalid username. Allowed: letters, digits, and . _ - @ (max 64).",
            )
        }

        val (db, totalAvailable) = runCatching { loadSites() }.getOrElse {
            return UsernameScanResult(username = username, error = it.message ?: "Sherlock site database unreachable")
        }
        if (db.isEmpty()) {
            return UsernameScanResult(username = username, error = "Sherlock site database empty")
        }

        val names = db.keys.toList()
        val results = mapLimit(names, CONCURRENCY) { name -> checkSite(name, db.getValue(name), username) }

        var found = results.filter { it.status == "found" }.sortedBy { it.site }
        val inconclusive = mutableListOf<SiteResult>()

        // Many sites answer 200 for a profile that doesn't exist. Re-run just the positives
        // against two random handles nobody can hold — any site that "finds" either one cannot
        // be trusted for this scan.
        if (found.isNotEmpty()) {
            val controls = listOf(controlUsername(), controlUsername())
            val probes = controls.flatMap { control -> found.map { it.site to control } }
            val controlResults = mapLimit(probes, minOf(CONCURRENCY, probes.size)) { (site, control) ->
                checkSite(site, db.getValue(site), control)
            }
            val unreliable = controlResults.filter { it.status == "found" }.map { it.site }.toSet()
            if (unreliable.isNotEmpty()) {
                found.filter { it.site in unreliable }.forEach { inconclusive += it.copy(status = "inconclusive") }
                found = found.filterNot { it.site in unreliable }
            }
        }

        return UsernameScanResult(
            username = username,
            checked = results.size,
            totalAvailable = totalAvailable,
            found = found.map { SiteHit(it.site, it.url, it.status) },
            inconclusive = inconclusive.map { SiteHit(it.site, it.url, it.status) },
            blocked = results.filter { it.status == "blocked" }.map { SiteHit(it.site, it.url, it.status) },
            notFoundCount = results.count { it.status == "not_found" },
            errors = results.filter { it.status == "error" }.map { SiteHit(it.site, it.url, it.status) },
            elapsedMs = (System.currentTimeMillis() - started).toInt(),
        )
    }

    private suspend fun loadSites(): Pair<Map<String, SiteDef>, Int> {
        dbCache?.let { (at, sites, total) -> if (System.currentTimeMillis() - at < DB_TTL_MS) return sites to total }

        val text = DirectHttp.getText(DATA_URL, headers = mapOf("Accept" to "application/json"))
        val root = NetworkModule.json.parseToJsonElement(text).jsonObject

        val sites = mutableMapOf<String, SiteDef>()
        for (name in PRIORITY) {
            val def = (root[name] as? JsonObject) ?: continue
            if (def["isNSFW"]?.jsonPrimitive?.booleanOrNull == true) continue
            val url = def["url"]?.jsonPrimitive?.contentOrNull ?: continue
            val errorType = def["errorType"]?.jsonPrimitive?.contentOrNull ?: continue
            sites[name] = SiteDef(
                url = url,
                urlProbe = def["urlProbe"]?.jsonPrimitive?.contentOrNull,
                errorType = errorType,
                errorMsgs = asStringList(def["errorMsg"]),
                errorUrl = def["errorUrl"]?.jsonPrimitive?.contentOrNull,
                errorCodes = asIntList(def["errorCode"]),
                regexCheck = def["regexCheck"]?.jsonPrimitive?.contentOrNull,
                requestMethod = def["request_method"]?.jsonPrimitive?.contentOrNull,
                requestPayload = def["request_payload"],
                headers = (def["headers"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.content }.orEmpty(),
            )
        }

        val totalAvailable = root.size
        dbCache = Triple(System.currentTimeMillis(), sites, totalAvailable)
        return sites to totalAvailable
    }

    private fun asStringList(el: JsonElement?): List<String> = when (el) {
        null -> emptyList()
        is JsonArray -> el.mapNotNull { it.jsonPrimitive.contentOrNull }
        else -> listOfNotNull(el.jsonPrimitive.contentOrNull)
    }

    private fun asIntList(el: JsonElement?): List<Int> = when (el) {
        null -> emptyList()
        is JsonArray -> el.mapNotNull { it.jsonPrimitive.intOrNull }
        else -> listOfNotNull(el.jsonPrimitive.intOrNull)
    }

    private fun fill(template: String, username: String): String =
        template.replace("{}", URLEncoder.encode(username, "UTF-8"))

    private fun looksLikeChallenge(body: String): Boolean {
        val head = body.take(4000).lowercase()
        return CHALLENGE_MARKERS.any { head.contains(it) }
    }

    private fun controlUsername(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..16).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    private suspend fun checkSite(name: String, def: SiteDef, username: String): SiteResult =
        withContext(Dispatchers.IO) {
            val profileUrl = fill(def.url, username)

            def.regexCheck?.let { pattern ->
                val matches = runCatching { Regex(pattern).matches(username) }.getOrDefault(true)
                if (!matches) return@withContext SiteResult(name, profileUrl, "skipped")
            }

            val requestUrl = def.urlProbe?.let { fill(it, username) } ?: profileUrl
            val method = (def.requestMethod ?: "GET").uppercase()

            try {
                executeRequest(requestUrl, method, def, username).use { res ->
                    if (res.code in BLOCKED_CODES && res.code !in def.errorCodes) {
                        return@withContext SiteResult(name, profileUrl, "blocked")
                    }
                    when (def.errorType) {
                        "status_code" -> {
                            if (res.code in def.errorCodes) {
                                return@withContext SiteResult(name, profileUrl, "not_found")
                            }
                            val found = res.code in 200..299
                            // Some urlProbe endpoints upstream are simply broken (soft-404). When
                            // the probe says "missing", give the canonical profile URL one chance
                            // to disagree — calibration re-tests any positive against a control,
                            // so a soft-404 here can't sneak through as a hit.
                            if (!found && def.urlProbe != null && requestUrl != profileUrl) {
                                val confirmed = runCatching {
                                    executeRequest(profileUrl, method, def, username).use { it.code in 200..299 }
                                }.getOrDefault(false)
                                if (confirmed) return@withContext SiteResult(name, profileUrl, "found")
                            }
                            SiteResult(name, profileUrl, if (found) "found" else "not_found")
                        }
                        "message" -> {
                            val body = res.body?.string().orEmpty()
                            if (looksLikeChallenge(body)) {
                                SiteResult(name, profileUrl, "blocked")
                            } else {
                                val missing = def.errorMsgs.any { body.contains(it) }
                                SiteResult(name, profileUrl, if (missing) "not_found" else "found")
                            }
                        }
                        else -> { // response_url: a missing profile lands on a known URL
                            val landed = res.request.url.toString()
                            val isError = def.errorUrl != null && landed.startsWith(fill(def.errorUrl, username))
                            val found = !isError && res.code in 200..299
                            SiteResult(name, profileUrl, if (found) "found" else "not_found")
                        }
                    }
                }
            } catch (e: Exception) {
                SiteResult(name, profileUrl, "error")
            }
        }

    private fun executeRequest(url: String, method: String, def: SiteDef, username: String): Response {
        val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        def.headers.forEach { (k, v) -> builder.header(k, v) }
        if (method == "POST") {
            val payloadJson = def.requestPayload?.let {
                NetworkModule.json.encodeToString(it).replace("{}", username)
            }
            if (payloadJson != null) {
                builder.header("Content-Type", "application/json")
                builder.post(payloadJson.toRequestBody("application/json".toMediaType()))
            } else {
                builder.post("".toRequestBody(null))
            }
        }
        return httpClient.newCall(builder.build()).execute()
    }

    /** Bounded-concurrency fan-out — a several-dozen-site sweep run unbounded would open too
     * many sockets at once and trip the sites' own rate limiters. */
    private suspend fun <T, R> mapLimit(items: List<T>, limit: Int, fn: suspend (T) -> R): List<R> = coroutineScope {
        val semaphore = Semaphore(limit.coerceAtLeast(1))
        items.map { item -> async { semaphore.withPermit { fn(item) } } }.awaitAll()
    }
}
