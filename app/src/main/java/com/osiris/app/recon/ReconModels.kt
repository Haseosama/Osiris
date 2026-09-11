package com.osiris.app.recon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Typed response shapes for the RECON tools worth a dedicated view (see ReconResult).
// Scanner, SSL certs and space weather still fall back to a generic indented JSON tree
// (JsonTreeView in ReconScreen.kt) rather than a DTO — the scanner in particular proxies to an
// external microservice whose response shape varies per scan `type` and isn't in this repo.

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

@Serializable
data class WhoisResult(
    val domain: String? = null,
    val rdap: WhoisRdap? = null,
    val registration: String? = null,
    val expiration: String? = null,
    @SerialName("last_changed") val lastChanged: String? = null,
    val http: WhoisHttp? = null,
    @SerialName("security_score") val securityScore: SecurityScore? = null,
    @SerialName("sanctions_match") val sanctionsMatch: SanctionsMatchBlock? = null,
    val error: String? = null,
)

@Serializable
data class WhoisRdap(
    val handle: String? = null,
    val name: String? = null,
    val status: List<String> = emptyList(),
    val nameservers: List<String> = emptyList(),
    val entities: List<WhoisEntity> = emptyList(),
)

@Serializable
data class WhoisEntity(
    val handle: String? = null,
    val roles: List<String> = emptyList(),
    val name: String? = null,
    val org: String? = null,
)

@Serializable
data class WhoisHttp(
    val status: Int? = null,
    val headers: Map<String, String> = emptyMap(),
    val redirected: Boolean = false,
    @SerialName("final_url") val finalUrl: String? = null,
)

@Serializable
data class SecurityScore(val score: Int = 0, val max: Int = 7, val grade: String? = null)

@Serializable
data class CryptoWalletResult(
    val address: String? = null,
    val chain: String? = null,
    @SerialName("chain_label") val chainLabel: String? = null,
    val symbol: String? = null,
    val balance: WalletBalance? = null,
    val activity: WalletActivity? = null,
    val flow: WalletFlow? = null,
    val counterparties: List<Counterparty> = emptyList(),
    val transactions: List<TxSummary> = emptyList(),
    val sanctions: WalletSanctions? = null,
    val risk: WalletRisk? = null,
    val labels: List<String> = emptyList(),
    val error: String? = null,
)

@Serializable
data class WalletBalance(val native: Double = 0.0, val usd: Double? = null)

@Serializable
data class WalletActivity(
    @SerialName("tx_count") val txCount: Int = 0,
    @SerialName("first_seen") val firstSeen: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("age_days") val ageDays: Int? = null,
    @SerialName("history_complete") val historyComplete: Boolean = false,
)

@Serializable
data class WalletFlow(
    @SerialName("total_in") val totalIn: Double = 0.0,
    @SerialName("total_out") val totalOut: Double = 0.0,
    val net: Double = 0.0,
)

@Serializable
data class Counterparty(
    val address: String,
    val direction: String,
    val txs: Int = 0,
    val value: Double = 0.0,
)

@Serializable
data class TxSummary(
    val hash: String,
    val time: String? = null,
    val direction: String,
    val value: Double = 0.0,
    val counterparty: String? = null,
)

@Serializable
data class WalletSanctions(
    val screened: Boolean = false,
    val hit: Boolean = false,
    val entries: List<SanctionEntry> = emptyList(),
)

@Serializable
data class WalletRisk(
    val score: Int = 0,
    val level: String? = null,
    val factors: List<RiskFactor> = emptyList(),
)

@Serializable
data class RiskFactor(
    val code: String? = null,
    val label: String? = null,
    val severity: String? = null,
    val weight: Int = 0,
    val detail: String? = null,
)

/** What [ReconScreen] renders — a typed view for the tools above, an indented JSON tree for
 * everything else (still structured, just not locked into a DTO). */
sealed interface ReconResult {
    data class Cve(val data: CveResult) : ReconResult
    data class Dns(val data: DnsResult) : ReconResult
    data class IpIntel(val data: IpIntelResult) : ReconResult
    data class Sanctions(val data: SanctionsResult) : ReconResult
    data class Whois(val data: WhoisResult) : ReconResult
    data class CryptoWallet(val data: CryptoWalletResult) : ReconResult
    data class Raw(val json: String) : ReconResult
}
