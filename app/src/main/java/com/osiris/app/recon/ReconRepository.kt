package com.osiris.app.recon

import com.osiris.app.data.remote.NetworkModule
import com.osiris.app.recon.source.CveSource
import com.osiris.app.recon.source.DnsSource
import com.osiris.app.recon.source.GithubSource
import com.osiris.app.recon.source.LeaksSource
import com.osiris.app.recon.source.MacSource
import com.osiris.app.recon.source.PhoneSource
import com.osiris.app.recon.source.SslCertsSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.net.URLEncoder

class ReconRepository {

    companion object {
        /** Tools that hit their upstream directly and need no backend at all — see [query].
         * [ReconViewModel] uses this to skip the "configure the backend" gate for these. */
        val NATIVE_TOOLS: Set<ReconTool> = setOf(
            ReconTool.DNS, ReconTool.SSL_CERTS, ReconTool.CVE, ReconTool.LEAKS,
            ReconTool.GITHUB, ReconTool.MAC, ReconTool.PHONE,
        )
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val prettyJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /** Tools ported off the backend (see the "no backend" migration plan, Phase 1) hit their
     * upstream source directly; everything else still goes through the self-hosted Osiris
     * backend's `/api/osint/*` proxy, same as before. `baseUrl` only matters for that second
     * group. */
    suspend fun query(baseUrl: String, tool: ReconTool, value: String, secondaryValue: String?): Result<ReconResult> =
        runCatching {
            when (tool) {
                ReconTool.DNS -> ReconResult.Dns(DnsSource.lookup(value))
                ReconTool.SSL_CERTS -> ReconResult.Raw(SslCertsSource.lookup(value))
                ReconTool.CVE -> ReconResult.Cve(CveSource.lookup(value))
                ReconTool.LEAKS -> ReconResult.Leaks(LeaksSource.lookup(value))
                ReconTool.GITHUB -> ReconResult.Github(GithubSource.lookup(value))
                ReconTool.MAC -> ReconResult.Mac(MacSource.lookup(value))
                ReconTool.PHONE -> ReconResult.Phone(PhoneSource.lookup(value))
                else -> {
                    val url = buildUrl(tool, value, secondaryValue)
                    val response = NetworkModule.apiFor(baseUrl).raw(url)
                    val body = response.body()?.string().orEmpty()
                    if (body.isBlank()) {
                        error("HTTP ${response.code()}")
                    }
                    parse(tool, body)
                }
            }
        }

    private fun buildUrl(tool: ReconTool, value: String, secondaryValue: String?): String = buildString {
        append(tool.path)
        if (tool.paramName.isNotEmpty()) {
            append('?')
            append(tool.paramName)
            append('=')
            append(URLEncoder.encode(value, "UTF-8"))
            tool.secondaryParam?.let { secondary ->
                append('&')
                append(secondary.name)
                append('=')
                append(URLEncoder.encode(secondaryValue ?: secondary.default, "UTF-8"))
            }
        }
    }

    /** Only reached for tools still proxied through the backend (see [NATIVE_TOOLS]) — tries the
     * typed shape for the ones that have one; anything else (or a decode mismatch, e.g. an
     * unexpected `{ error: ... }` body) falls back to pretty-printed raw JSON. */
    private fun parse(tool: ReconTool, body: String): ReconResult = when (tool) {
        ReconTool.IP_INTEL -> decodeOrRaw(body) { ReconResult.IpIntel(json.decodeFromString(body)) }
        ReconTool.SANCTIONS -> decodeOrRaw(body) { ReconResult.Sanctions(json.decodeFromString(body)) }
        ReconTool.WHOIS -> decodeOrRaw(body) { ReconResult.Whois(json.decodeFromString(body)) }
        ReconTool.CRYPTO_WALLET -> decodeOrRaw(body) { ReconResult.CryptoWallet(json.decodeFromString(body)) }
        ReconTool.USERNAME -> decodeOrRaw(body) { ReconResult.Username(json.decodeFromString(body)) }
        else -> ReconResult.Raw(prettyPrint(body))
    }

    private inline fun decodeOrRaw(body: String, decode: () -> ReconResult): ReconResult =
        runCatching(decode).getOrElse { ReconResult.Raw(prettyPrint(body)) }

    private fun prettyPrint(raw: String): String = runCatching {
        val element = prettyJson.parseToJsonElement(raw)
        prettyJson.encodeToString(JsonElement.serializer(), element)
    }.getOrDefault(raw)
}
