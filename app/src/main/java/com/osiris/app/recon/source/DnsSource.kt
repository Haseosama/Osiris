package com.osiris.app.recon.source

import com.osiris.app.data.source.DirectHttp
import com.osiris.app.recon.DnsRecord
import com.osiris.app.recon.DnsResult
import com.osiris.app.recon.DnsSummary
import kotlinx.serialization.Serializable
import java.net.URLEncoder

/** Google DNS-over-HTTPS lookup, called directly from the phone — mirrors
 * `osiris-backend/src/app/api/osint/dns/route.ts`. Keyless. */
object DnsSource {

    private val TYPES = listOf("A", "AAAA", "MX", "NS", "TXT", "CNAME", "SOA")

    @Serializable
    private data class GoogleDnsResponse(val Status: Int = -1, val Answer: List<GoogleDnsAnswer> = emptyList())

    @Serializable
    private data class GoogleDnsAnswer(val name: String? = null, val type: Int? = null, val TTL: Int? = null, val data: String? = null)

    suspend fun lookup(domain: String): DnsResult {
        val records = linkedMapOf<String, List<DnsRecord>>()

        for (type in TYPES) {
            val url = "https://dns.google/resolve?name=${URLEncoder.encode(domain, "UTF-8")}&type=$type"
            val answers = runCatching { DirectHttp.getJson<GoogleDnsResponse>(url, headers = mapOf("Accept" to "application/json")) }
                .getOrNull()?.Answer.orEmpty()
            records[type] = answers.map { DnsRecord(name = it.name, type = it.type, ttl = it.TTL, data = it.data) }
        }

        val aRecords = records["A"].orEmpty()
        val mxRecords = records["MX"].orEmpty()
        val nsRecords = records["NS"].orEmpty()

        return DnsResult(
            domain = domain,
            records = records,
            summary = DnsSummary(
                ipAddresses = aRecords.mapNotNull { it.data },
                mailServers = mxRecords.mapNotNull { it.data },
                nameservers = nsRecords.mapNotNull { it.data },
                totalRecords = records.values.sumOf { it.size },
            ),
        )
    }
}
