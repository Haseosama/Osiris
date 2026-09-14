package com.osiris.app.recon.source

import com.osiris.app.data.source.DirectHttp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.URLEncoder

/** Certificate Transparency lookup via crt.sh, called directly from the phone — mirrors
 * `osiris-backend/src/app/api/osint/certs/route.ts`. Keyless. No typed [com.osiris.app.recon.ReconResult]
 * shape for this tool (falls back to the generic indented-JSON view), so this just reshapes the
 * same fields the backend used to and hands back pretty-printed JSON text. */
object SslCertsSource {

    private val prettyJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Serializable
    private data class CrtShEntry(
        val id: Long? = null,
        val issuer_name: String? = null,
        val common_name: String? = null,
        val name_value: String? = null,
        val not_before: String? = null,
        val not_after: String? = null,
        val serial_number: String? = null,
    )

    suspend fun lookup(domain: String): String {
        val url = "https://crt.sh/?q=%25.${URLEncoder.encode(domain, "UTF-8")}&output=json"
        val certs = runCatching {
            DirectHttp.getJson<List<CrtShEntry>>(url, headers = mapOf("User-Agent" to "Osiris-OSINT/3.0"))
        }.getOrNull()

        if (certs == null) {
            return prettyJson.encodeToString(
                JsonElement.serializer(),
                buildJsonObject { put("domain", domain); putJsonArray("certificates") {}; put("error", "crt.sh unavailable") },
            )
        }

        val seen = mutableSetOf<String>()
        val subdomains = sortedSetOf<String>()
        val unique = mutableListOf<CrtShEntry>()

        for (cert in certs.take(200)) {
            val key = "${cert.common_name}-${cert.serial_number}"
            if (!seen.add(key)) continue

            cert.name_value.orEmpty().split("\n").forEach { line ->
                val clean = line.trim().removePrefix("*.")
                if (clean.endsWith(domain)) subdomains += clean
            }
            unique += cert
        }

        val result = buildJsonObject {
            put("domain", domain)
            putJsonArray("certificates") {
                unique.take(50).forEach { cert ->
                    add(buildJsonObject {
                        put("id", cert.id)
                        put("issuer", cert.issuer_name)
                        put("common_name", cert.common_name)
                        put("name_value", cert.name_value)
                        put("not_before", cert.not_before)
                        put("not_after", cert.not_after)
                        put("serial", cert.serial_number)
                    })
                }
            }
            putJsonArray("subdomains") { subdomains.forEach { add(JsonPrimitive(it)) } }
            put("total_certs", certs.size)
            put("unique_subdomains", subdomains.size)
        }

        return prettyJson.encodeToString(JsonElement.serializer(), result)
    }
}
