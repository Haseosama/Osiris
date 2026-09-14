package com.osiris.app.recon.source

import com.osiris.app.data.source.DirectHttp
import com.osiris.app.recon.IpIntelResult
import com.osiris.app.recon.WhoisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLEncoder

/** "Scanner réseau" (RECON, [com.osiris.app.recon.ReconTool.PORT_SCAN]), called directly from
 * the phone. The backend's `/api/scanner` route is a thin proxy to a separate, external
 * hardened scanner microservice (`SCANNER_URL`/`SCANNER_KEY`) whose source isn't in either
 * repo, so its scan types can't be ported line-for-line — this reimplements the ones that map
 * to well-understood, standard techniques, reusing the sources already built for other RECON
 * tools where the same data applies:
 *   - `whois`              → [WhoisSource] (RDAP + HTTP fingerprint)
 *   - `ssl`/`subdomains`   → [SslCertsSource] (crt.sh already returns both certs and subdomains)
 *   - `geoloc`             → [IpIntelSource] (ip-api.com resolves a hostname server-side too)
 *   - `rdns`               → Google DNS-over-HTTPS PTR lookup
 *   - `headers`            → the same HTTP fingerprint HEAD probe [WhoisSource] uses, standalone
 *   - `quick`              → a bounded TCP connect-scan (java.net.Socket, not a raw SYN scan)
 *                            against a fixed list of well-known ports, not an arbitrary range,
 *                            so a single query can't turn the phone into a sweep tool
 * The backend's own two other types, `tech` and `vuln`, are dropped: there's no way to
 * approximate a real tech-fingerprint/vulnerability engine without the original microservice's
 * code, and a guess dressed up under that name would be misleading. [ReconTool]'s `type`
 * options only offer the seven handled here.
 *
 * No SSRF guard here unlike the backend's proxy — that existed to stop a public multi-tenant
 * server being tricked into reaching internal addresses on the server's behalf, a threat model
 * that doesn't apply to a client the phone's own owner is running against their own queries
 * (same reasoning as [WhoisSource]'s HEAD probe). */
object NetworkScannerSource {

    private val prettyJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val QUICK_PORTS = listOf(
        21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP", 53 to "DNS",
        80 to "HTTP", 110 to "POP3", 143 to "IMAP", 443 to "HTTPS", 445 to "SMB",
        587 to "SMTP submission", 993 to "IMAPS", 995 to "POP3S", 1433 to "MSSQL",
        3306 to "MySQL", 3389 to "RDP", 5432 to "PostgreSQL", 5900 to "VNC",
        6379 to "Redis", 8080 to "HTTP alt", 8443 to "HTTPS alt", 27017 to "MongoDB",
    )
    private const val CONNECT_TIMEOUT_MS = 1200
    private const val CONCURRENCY = 8

    suspend fun scan(targetInput: String, type: String): String {
        val target = targetInput.trim()
        if (target.isEmpty()) return errorJson(target, type, "Missing target")
        return runCatching {
            when (type) {
                "whois" -> prettyJson.encodeToString(WhoisResult.serializer(), WhoisSource.lookup(target))
                "ssl", "subdomains" -> SslCertsSource.lookup(target)
                "geoloc" -> prettyJson.encodeToString(IpIntelResult.serializer(), IpIntelSource.lookup(target))
                "rdns" -> rdns(target)
                "headers" -> headers(target)
                else -> quick(target)
            }
        }.getOrElse { errorJson(target, type, it.message ?: "Scan failed") }
    }

    private fun errorJson(target: String, type: String, message: String): String =
        prettyJson.encodeToString(
            JsonElement.serializer(),
            buildJsonObject { put("target", target); put("scan_type", type); put("error", message) },
        )

    private suspend fun quick(target: String): String {
        val started = System.currentTimeMillis()
        val open = coroutineScope {
            val semaphore = Semaphore(CONCURRENCY)
            QUICK_PORTS.map { (port, name) ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val isOpen = runCatching {
                            Socket().use { it.connect(InetSocketAddress(target, port), CONNECT_TIMEOUT_MS) }
                            true
                        }.getOrDefault(false)
                        if (isOpen) port to name else null
                    }
                }
            }.awaitAll().filterNotNull()
        }.sortedBy { it.first }

        return prettyJson.encodeToString(
            JsonElement.serializer(),
            buildJsonObject {
                put("target", target)
                put("scan_type", "quick")
                put(
                    "note",
                    "Scan de connexion TCP sur une liste fixe de ports courants, exécuté depuis ce " +
                        "téléphone — pas un scan SYN, pas de balayage de plage arbitraire.",
                )
                put("ports_checked", QUICK_PORTS.size)
                put("elapsed_ms", (System.currentTimeMillis() - started).toInt())
                putJsonArray("open_ports") {
                    open.forEach { (port, name) -> addJsonObject { put("port", port); put("service", name) } }
                }
            },
        )
    }

    private suspend fun headers(target: String): String {
        val primary = if (target.startsWith("http://") || target.startsWith("https://")) target else "https://$target"
        val result = runCatching { DirectHttp.head(primary) }
            .getOrElse { DirectHttp.head(primary.replaceFirst("https://", "http://")) }

        return prettyJson.encodeToString(
            JsonElement.serializer(),
            buildJsonObject {
                put("target", target)
                put("scan_type", "headers")
                put("url", result.finalUrl)
                put("status", result.code)
                put("redirected", result.redirected)
                putJsonObject("headers") { result.headers.forEach { (k, v) -> put(k, v) } }
            },
        )
    }

    private suspend fun rdns(target: String): String {
        val ip = if (isIpv4(target)) target else resolveA(target)
            ?: return errorJson(target, "rdns", "Could not resolve target to an IPv4 address")
        val reversed = ip.split(".").reversed().joinToString(".") + ".in-addr.arpa"
        val url = "https://dns.google/resolve?name=${URLEncoder.encode(reversed, "UTF-8")}&type=PTR"
        val hostnames = runCatching {
            DirectHttp.getJson<GoogleDnsResponse>(url, headers = mapOf("Accept" to "application/json"))
        }.getOrNull()?.Answer.orEmpty().mapNotNull { it.data }

        return prettyJson.encodeToString(
            JsonElement.serializer(),
            buildJsonObject {
                put("target", target)
                put("scan_type", "rdns")
                put("ip", ip)
                putJsonArray("hostnames") { hostnames.forEach { add(JsonPrimitive(it)) } }
            },
        )
    }

    private suspend fun resolveA(domain: String): String? {
        val url = "https://dns.google/resolve?name=${URLEncoder.encode(domain, "UTF-8")}&type=A"
        return runCatching {
            DirectHttp.getJson<GoogleDnsResponse>(url, headers = mapOf("Accept" to "application/json"))
        }.getOrNull()?.Answer?.firstOrNull()?.data
    }

    private fun isIpv4(s: String): Boolean = Regex("^\\d{1,3}(\\.\\d{1,3}){3}\$").matches(s)

    @Serializable
    private data class GoogleDnsResponse(val Answer: List<GoogleDnsAnswer> = emptyList())

    @Serializable
    private data class GoogleDnsAnswer(val data: String? = null)
}
