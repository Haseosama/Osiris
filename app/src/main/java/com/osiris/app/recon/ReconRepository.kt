package com.osiris.app.recon

import com.osiris.app.data.remote.NetworkModule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.net.URLEncoder

class ReconRepository {

    private val prettyJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun query(baseUrl: String, tool: ReconTool, value: String, secondaryValue: String?): Result<String> =
        runCatching {
            val url = buildString {
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
            val response = NetworkModule.apiFor(baseUrl).raw(url)
            val body = response.body()?.string().orEmpty()
            if (body.isBlank()) {
                error("HTTP ${response.code()}")
            }
            prettyPrint(body)
        }

    private fun prettyPrint(raw: String): String = runCatching {
        val element = prettyJson.parseToJsonElement(raw)
        prettyJson.encodeToString(JsonElement.serializer(), element)
    }.getOrDefault(raw)
}
