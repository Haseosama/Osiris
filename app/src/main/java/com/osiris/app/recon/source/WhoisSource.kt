package com.osiris.app.recon.source

import com.osiris.app.data.source.DirectHttp
import com.osiris.app.recon.SanctionsHit
import com.osiris.app.recon.SanctionsMatchBlock
import com.osiris.app.recon.SecurityScore
import com.osiris.app.recon.WhoisEntity
import com.osiris.app.recon.WhoisHttp
import com.osiris.app.recon.WhoisRdap
import com.osiris.app.recon.WhoisResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder

/** WHOIS/domain intelligence via RDAP + HTTP header fingerprinting, called directly from the
 * phone — mirrors `osiris-backend/src/app/api/osint/whois/route.ts`. Cross-checks RDAP
 * registrant/org names against the OFAC SDN list via [SanctionsIndex]. Skips the backend's
 * SSRF-guard redirect handling for the HEAD probe — that exists to stop a public multi-tenant
 * server being used to reach internal network addresses on the server's behalf, a threat model
 * that doesn't apply to a client the phone's own owner is running against their own queries. */
object WhoisSource {

    private val SECURITY_HEADERS = listOf(
        "server", "x-powered-by", "x-frame-options", "strict-transport-security",
        "content-security-policy", "x-content-type-options", "x-xss-protection",
        "referrer-policy", "permissions-policy",
    )

    suspend fun lookup(domainInput: String): WhoisResult {
        val domain = domainInput.trim()
        var rdap: WhoisRdap? = null
        var registration: String? = null
        var expiration: String? = null
        var lastChanged: String? = null

        runCatching {
            val data = DirectHttp.getJson<RdapResponse>(
                "https://rdap.org/domain/${URLEncoder.encode(domain, "UTF-8")}",
                headers = mapOf("Accept" to "application/json"),
            )
            val events = data.events
            val entities = data.entities.map {
                WhoisEntity(
                    handle = it.handle,
                    roles = it.roles,
                    name = vcardValue(it.vcardArray, "fn"),
                    org = vcardValue(it.vcardArray, "org"),
                )
            }.filter { it.name != null || it.org != null }

            rdap = WhoisRdap(
                handle = data.handle,
                name = data.ldhName,
                status = data.status,
                nameservers = data.nameservers.mapNotNull { it.ldhName },
                entities = entities,
            )
            registration = events.firstOrNull { it.eventAction == "registration" }?.eventDate
            expiration = events.firstOrNull { it.eventAction == "expiration" }?.eventDate
            lastChanged = events.firstOrNull { it.eventAction == "last changed" }?.eventDate
        }

        var http: WhoisHttp? = null
        var securityScore: SecurityScore? = null
        runCatching {
            val result = DirectHttp.head("https://$domain")
            val headers = result.headers.filterKeys { it.lowercase() in SECURITY_HEADERS }
            http = WhoisHttp(status = result.code, headers = headers, redirected = result.redirected, finalUrl = result.finalUrl)

            var score = 0
            if (headers.keys.any { it.equals("strict-transport-security", true) }) score += 2
            if (headers.keys.any { it.equals("content-security-policy", true) }) score += 2
            if (headers.keys.any { it.equals("x-frame-options", true) }) score += 1
            if (headers.keys.any { it.equals("x-content-type-options", true) }) score += 1
            if (headers.keys.any { it.equals("referrer-policy", true) }) score += 1
            val grade = when { score >= 5 -> "A"; score >= 3 -> "B"; score >= 1 -> "C"; else -> "F" }
            securityScore = SecurityScore(score = score, max = 7, grade = grade)
        }

        val sanctionsMatch = runCatching {
            val candidates = linkedSetOf<String>()
            rdap?.entities.orEmpty().forEach { it.name?.let(candidates::add); it.org?.let(candidates::add) }
            val hits = candidates.mapNotNull { value ->
                val entries = SanctionsIndex.matchExact(value)
                if (entries.isNotEmpty()) SanctionsHit(matchedValue = value, entries = entries) else null
            }
            hits.takeIf { it.isNotEmpty() }?.let { SanctionsMatchBlock(source = "OFAC SDN", hits = it) }
        }.getOrNull()

        return WhoisResult(
            domain = domain,
            rdap = rdap,
            registration = registration,
            expiration = expiration,
            lastChanged = lastChanged,
            http = http,
            securityScore = securityScore,
            sanctionsMatch = sanctionsMatch,
        )
    }

    private fun vcardValue(vcardArray: List<JsonElement>?, field: String): String? {
        // RDAP jCard shape: ["vcard", [ ["fn", {}, "text", "Some Name"], ["org", {}, "text", "Some Org"], ... ]]
        val props = vcardArray?.getOrNull(1) as? JsonArray ?: return null
        for (prop in props) {
            val arr = prop as? JsonArray ?: continue
            val name = (arr.getOrNull(0) as? JsonPrimitive)?.content ?: continue
            if (name == field) return (arr.getOrNull(3) as? JsonPrimitive)?.content
        }
        return null
    }

    @Serializable
    private data class RdapResponse(
        val handle: String? = null,
        val ldhName: String? = null,
        val status: List<String> = emptyList(),
        val events: List<RdapEvent> = emptyList(),
        val nameservers: List<RdapNameserver> = emptyList(),
        val entities: List<RdapEntity> = emptyList(),
    )

    @Serializable private data class RdapEvent(val eventAction: String? = null, val eventDate: String? = null)
    @Serializable private data class RdapNameserver(val ldhName: String? = null)
    @Serializable private data class RdapEntity(
        val handle: String? = null,
        val roles: List<String> = emptyList(),
        val vcardArray: List<JsonElement>? = null,
    )
}
