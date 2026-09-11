package com.osiris.app.recon

import com.osiris.app.data.remote.NetworkModule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.net.URLEncoder

class ReconRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val prettyJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun query(baseUrl: String, tool: ReconTool, value: String, secondaryValue: String?): Result<ReconResult> =
        runCatching {
            val url = buildUrl(tool, value, secondaryValue)
            val response = NetworkModule.apiFor(baseUrl).raw(url)
            val body = response.body()?.string().orEmpty()
            if (body.isBlank()) {
                error("HTTP ${response.code()}")
            }
            parse(tool, body)
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

    /** Tries the typed shape for tools that have one; anything else (or a decode mismatch,
     * e.g. an unexpected `{ error: ... }` body) falls back to pretty-printed raw JSON. */
    private fun parse(tool: ReconTool, body: String): ReconResult = when (tool) {
        ReconTool.CVE -> decodeOrRaw(body) { ReconResult.Cve(json.decodeFromString(body)) }
        ReconTool.DNS -> decodeOrRaw(body) { ReconResult.Dns(json.decodeFromString(body)) }
        ReconTool.IP_INTEL -> decodeOrRaw(body) { ReconResult.IpIntel(json.decodeFromString(body)) }
        ReconTool.SANCTIONS -> decodeOrRaw(body) { ReconResult.Sanctions(json.decodeFromString(body)) }
        else -> ReconResult.Raw(prettyPrint(body))
    }

    private inline fun decodeOrRaw(body: String, decode: () -> ReconResult): ReconResult =
        runCatching(decode).getOrElse { ReconResult.Raw(prettyPrint(body)) }

    private fun prettyPrint(raw: String): String = runCatching {
        val element = prettyJson.parseToJsonElement(raw)
        prettyJson.encodeToString(JsonElement.serializer(), element)
    }.getOrDefault(raw)
}
