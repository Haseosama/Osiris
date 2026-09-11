package com.osiris.app.recon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Typed response shapes for the RECON tools worth a dedicated view (see ReconResult).
// Every other tool (scanner, WHOIS, SSL certs, crypto wallet, space weather) falls back to
// pretty-printed raw JSON — their schemas are either too deeply nested or too unstable to be
// worth locking into DTOs for a first pass.

@Serializable
data class CveResult(
    val id: String? = null,
    val description: String? = null,
    val cvss: Double? = null,
    @SerialName("cvss_vector") val cvssVector: String? = null,
    val severity: String? = null,
    val cwe: String? = null,
    val affected: List<CveAffected> = emptyList(),
    val references: List<String> = emptyList(),
    val published: String? = null,
    val modified: String? = null,
    val source: String? = null,
    val error: String? = null,
)

@Serializable
data class CveAffected(
    val vendor: String? = null,
    val product: String? = null,
    val versions: List<String> = emptyList(),
)

@Serializable
data class DnsResult(
    val domain: String? = null,
    val records: Map<String, List<DnsRecord>> = emptyMap(),
    val summary: DnsSummary? = null,
    val error: String? = null,
)

@Serializable
data class DnsRecord(
    val name: String? = null,
    val type: Int? = null,
    val ttl: Int? = null,
    val data: String? = null,
)

@Serializable
data class DnsSummary(
    @SerialName("ip_addresses") val ipAddresses: List<String> = emptyList(),
    @SerialName("mail_servers") val mailServers: List<String> = emptyList(),
    val nameservers: List<String> = emptyList(),
    @SerialName("total_records") val totalRecords: Int = 0,
)

@Serializable
data class IpIntelResult(
    val ip: String? = null,
    val geo: IpGeo? = null,
    val reputation: IpReputation? = null,
    @SerialName("sanctions_match") val sanctionsMatch: SanctionsMatchBlock? = null,
    val error: String? = null,
)

@Serializable
data class IpGeo(
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val region: String? = null,
    val city: String? = null,
    val isp: String? = null,
    val org: String? = null,
    @SerialName("as_number") val asNumber: String? = null,
    @SerialName("as_name") val asName: String? = null,
)

@Serializable
data class IpReputation(
    @SerialName("is_proxy") val isProxy: Boolean = false,
    @SerialName("is_hosting") val isHosting: Boolean = false,
    @SerialName("is_mobile") val isMobile: Boolean = false,
    @SerialName("risk_level") val riskLevel: String? = null,
)

@Serializable
data class SanctionsMatchBlock(
    val source: String? = null,
    val hits: List<SanctionsHit> = emptyList(),
)

@Serializable
data class SanctionsHit(
    @SerialName("matched_value") val matchedValue: String? = null,
    val entries: List<SanctionEntry> = emptyList(),
)

@Serializable
data class SanctionEntry(
    val id: String,
    val schema: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val programs: List<String> = emptyList(),
    val sanctions: String? = null,
)

@Serializable
data class SanctionsResult(
    val query: String? = null,
    val schema: String? = null,
    val total: Int = 0,
    val matches: List<SanctionEntry> = emptyList(),
    val source: String? = null,
    val error: String? = null,
)

/** What [ReconScreen] renders — a typed view for the tools above, raw JSON for everything else. */
sealed interface ReconResult {
    data class Cve(val data: CveResult) : ReconResult
    data class Dns(val data: DnsResult) : ReconResult
    data class IpIntel(val data: IpIntelResult) : ReconResult
    data class Sanctions(val data: SanctionsResult) : ReconResult
    data class Raw(val json: String) : ReconResult
}
