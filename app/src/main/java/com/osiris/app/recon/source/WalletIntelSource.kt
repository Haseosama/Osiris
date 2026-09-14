package com.osiris.app.recon.source

import com.osiris.app.data.source.DirectHttp
import com.osiris.app.recon.Counterparty
import com.osiris.app.recon.CryptoWalletResult
import com.osiris.app.recon.RiskFactor
import com.osiris.app.recon.SanctionEntry
import com.osiris.app.recon.TxSummary
import com.osiris.app.recon.WalletActivity
import com.osiris.app.recon.WalletBalance
import com.osiris.app.recon.WalletFlow
import com.osiris.app.recon.WalletRisk
import com.osiris.app.recon.WalletSanctions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

/**
 * On-chain wallet intelligence (BTC/ETH/SOL), called directly from the phone — mirrors
 * `osiris-backend/src/lib/chainIntel.ts` + `src/app/api/osint/crypto/route.ts`. Every source is
 * keyless (mempool.space, Blockscout, public Solana RPC, CoinGecko), cross-checked against OFAC
 * via [SanctionsIndex]. The backend's response also carries `tokens`/`sources`/`partial`/
 * `dormant_days`/`sample_size`/`ambiguous_chain` — none of those are in the app's
 * [CryptoWalletResult] DTO (nothing renders them), so they're computed only where needed
 * internally (dormancy still feeds the risk score) and dropped otherwise, not fetched at all
 * for the token-balance calls that only fed the unused `tokens` list.
 */
object WalletIntelSource {

    private val RE_ETH = Regex("^0x[a-fA-F0-9]{40}$")
    private val RE_BTC_BECH32 = Regex("^bc1[a-z0-9]{25,62}$")
    private val RE_BTC_LEGACY = Regex("^[13][a-km-zA-HJ-NP-Z1-9]{25,34}$")
    private val RE_BASE58 = Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$")

    private data class ChainMeta(val label: String, val symbol: String)
    private val CHAIN_META = mapOf(
        "bitcoin" to ChainMeta("Bitcoin", "BTC"),
        "ethereum" to ChainMeta("Ethereum", "ETH"),
        "solana" to ChainMeta("Solana", "SOL"),
    )
    private val COINGECKO_IDS = mapOf("bitcoin" to "bitcoin", "ethereum" to "ethereum", "solana" to "solana")

    private fun detectChain(address: String): Pair<String?, Boolean> = when {
        RE_ETH.matches(address) -> "ethereum" to false
        RE_BTC_BECH32.matches(address) -> "bitcoin" to false
        RE_BTC_LEGACY.matches(address) -> "bitcoin" to RE_BASE58.matches(address)
        RE_BASE58.matches(address) -> "solana" to false
        else -> null to false
    }

    suspend fun lookup(addressInput: String, chainOverride: String?): CryptoWalletResult {
        val address = addressInput.trim()
        if (address.length > 128) return CryptoWalletResult(address = address, error = "Address too long")
        // The backend also reports `ambiguous` (BTC-legacy vs Solana base58 overlap) — dropped
        // here too, CryptoWalletResult has no field for it.
        val (detected, _) = detectChain(address)
        val chain = chainOverride ?: detected
        if (chain == null) return CryptoWalletResult(address = address, error = "Unrecognised address format for BTC, ETH or SOL")
        val meta = CHAIN_META.getValue(chain)

        val raw = runCatching {
            when (chain) {
                "bitcoin" -> collectBitcoin(address)
                "ethereum" -> collectEthereum(address)
                else -> collectSolana(address)
            }
        }.getOrElse { return CryptoWalletResult(address = address, chain = chain, error = it.message ?: "Wallet lookup failed") }

        val price = runCatching { fetchPrice(chain) }.getOrNull()
        val sanctionHits = runCatching { SanctionsIndex.matchExact(address) }.getOrDefault(emptyList())

        val times = raw.transactions.mapNotNull { it.time }.sorted()
        val firstSeen = times.firstOrNull()
        val lastSeen = times.lastOrNull()
        val dayMs = 86_400_000L
        val now = System.currentTimeMillis()
        val ageDays = if (raw.historyComplete && firstSeen != null) {
            ((now - parseIsoMillis(firstSeen)) / dayMs).toInt()
        } else null
        val dormantDays = lastSeen?.let { ((now - parseIsoMillis(it)) / dayMs).toInt() }

        val activity = WalletActivity(
            txCount = raw.txCount,
            firstSeen = firstSeen,
            lastSeen = lastSeen,
            ageDays = ageDays,
            historyComplete = raw.historyComplete,
        )

        val counterparties = aggregateCounterparties(raw.transactions)
        val factors = buildFactors(raw, meta.symbol, ageDays, dormantDays, counterparties, sanctionHits)
        val (score, level) = scoreRisk(factors)

        return CryptoWalletResult(
            address = address,
            chain = chain,
            chainLabel = meta.label,
            symbol = meta.symbol,
            balance = WalletBalance(native = raw.balance, usd = price?.let { raw.balance * it }),
            activity = activity,
            flow = raw.flow,
            counterparties = counterparties,
            transactions = raw.transactions.take(25),
            sanctions = WalletSanctions(screened = true, hit = sanctionHits.isNotEmpty(), entries = sanctionHits.take(5)),
            risk = WalletRisk(score = score, level = level, factors = factors),
            labels = raw.labels,
        )
    }

    private fun parseIsoMillis(iso: String): Long = runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(System.currentTimeMillis())

    // ── price ────────────────────────────────────────────────────────

    private const val PRICE_TTL_MS = 120_000L
    private var priceCacheAt = 0L
    private var priceCache: Map<String, Double> = emptyMap()

    @Serializable
    private data class CoinGeckoResponse(val bitcoin: JsonObject? = null, val ethereum: JsonObject? = null, val solana: JsonObject? = null)

    private suspend fun fetchPrice(chain: String): Double? {
        val now = System.currentTimeMillis()
        if (now - priceCacheAt < PRICE_TTL_MS) return priceCache[chain]
        return runCatching {
            val ids = COINGECKO_IDS.values.joinToString(",")
            val data = DirectHttp.getJson<CoinGeckoResponse>("https://api.coingecko.com/api/v3/simple/price?ids=$ids&vs_currencies=usd")
            val usd = mutableMapOf<String, Double>()
            data.bitcoin?.get("usd")?.jsonPrimitive?.content?.toDoubleOrNull()?.let { usd["bitcoin"] = it }
            data.ethereum?.get("usd")?.jsonPrimitive?.content?.toDoubleOrNull()?.let { usd["ethereum"] = it }
            data.solana?.get("usd")?.jsonPrimitive?.content?.toDoubleOrNull()?.let { usd["solana"] = it }
            if (usd.isNotEmpty()) {
                priceCache = usd
                priceCacheAt = now
            }
            priceCache[chain]
        }.getOrNull() ?: priceCache[chain]
    }

    // ── per-chain collectors ────────────────────────────────────────

    private data class RawWallet(
        val balance: Double,
        val txCount: Int,
        val historyComplete: Boolean,
        val flow: WalletFlow?,
        val transactions: List<TxSummary>,
        val labels: List<String>,
    )

    private const val TX_PAGE = 50

    @Serializable
    private data class BtcAddressInfo(val chain_stats: BtcChainStats? = null)
    @Serializable
    private data class BtcChainStats(val funded_txo_sum: Long = 0, val spent_txo_sum: Long = 0, val tx_count: Int = 0)

    @Serializable
    private data class BtcTx(
        val txid: String? = null,
        val status: BtcTxStatus? = null,
        val fee: Long? = null,
        val vin: List<BtcVin> = emptyList(),
        val vout: List<BtcVout> = emptyList(),
    )
    @Serializable private data class BtcTxStatus(val block_time: Long? = null)
    @Serializable private data class BtcVin(val prevout: BtcVout? = null)
    @Serializable private data class BtcVout(val scriptpubkey_address: String? = null, val value: Long? = null)

    private suspend fun collectBitcoin(address: String): RawWallet {
        val base = "https://mempool.space/api"
        val enc = URLEncoder.encode(address, "UTF-8")
        val info = DirectHttp.getJson<BtcAddressInfo>("$base/address/$enc")
        val cs = info.chain_stats ?: BtcChainStats()
        fun sats(n: Long) = n / 1e8

        val totalIn = sats(cs.funded_txo_sum)
        val totalOut = sats(cs.spent_txo_sum)

        val transactions = runCatching {
            val txs = DirectHttp.getJson<List<BtcTx>>("$base/address/$enc/txs")
            txs.mapNotNull { t ->
                val txid = t.txid ?: return@mapNotNull null
                val inFrom = t.vin.sumOf { v -> if (v.prevout?.scriptpubkey_address == address) v.prevout.value ?: 0 else 0 }
                val outTo = t.vout.sumOf { v -> if (v.scriptpubkey_address == address) v.value ?: 0 else 0 }
                val spent = sats(inFrom)
                val received = sats(outTo)
                val direction = if (spent > 0 && received > 0) "self" else if (spent > 0) "out" else if (received > 0) "in" else "unknown"
                val pool = if (direction == "out") {
                    t.vout.filter { it.scriptpubkey_address != null && it.scriptpubkey_address != address }
                } else {
                    t.vin.mapNotNull { it.prevout }.filter { it.scriptpubkey_address != null && it.scriptpubkey_address != address }
                }
                val top = pool.maxByOrNull { it.value ?: 0 }
                TxSummary(
                    hash = txid,
                    time = t.status?.block_time?.let { Instant.ofEpochSecond(it).toString() },
                    direction = direction,
                    value = if (direction == "out") spent - received else received,
                    counterparty = top?.scriptpubkey_address,
                )
            }
        }.getOrDefault(emptyList())

        return RawWallet(
            balance = totalIn - totalOut,
            txCount = cs.tx_count,
            historyComplete = transactions.size >= cs.tx_count,
            flow = WalletFlow(totalIn = totalIn, totalOut = totalOut, net = totalIn - totalOut),
            transactions = transactions,
            labels = emptyList(),
        )
    }

    @Serializable
    private data class EthAddressInfo(
        val coin_balance: String? = null,
        val ens_domain_name: String? = null,
        val is_contract: Boolean = false,
        val creation_transaction_hash: String? = null,
        val is_verified: Boolean = false,
        val has_beacon_chain_withdrawals: Boolean = false,
    )

    @Serializable
    private data class EthTxList(val items: List<EthTx> = emptyList())
    @Serializable
    private data class EthTx(
        val hash: String? = null,
        val timestamp: String? = null,
        val from: EthAddrRef? = null,
        val to: EthAddrRef? = null,
        val value: String? = null,
        val status: String? = null,
        val result: String? = null,
    )
    @Serializable private data class EthAddrRef(val hash: String? = null)

    private suspend fun collectEthereum(address: String): RawWallet {
        val base = "https://eth.blockscout.com/api/v2"
        val enc = URLEncoder.encode(address, "UTF-8")
        val info = DirectHttp.getJson<EthAddressInfo>("$base/addresses/$enc")
        val balance = (info.coin_balance?.toDoubleOrNull() ?: 0.0) / 1e18

        val labels = buildList {
            info.ens_domain_name?.let { add("ENS: $it") }
            if (info.is_contract) {
                add(
                    if (info.creation_transaction_hash != null) {
                        if (info.is_verified) "Smart contract (verified source)" else "Smart contract"
                    } else {
                        "Code at address, no deployment tx (EIP-7702 delegation)"
                    },
                )
            }
            if (info.has_beacon_chain_withdrawals) add("Beacon chain withdrawals")
        }

        val lower = address.lowercase()
        val transactions = runCatching {
            val txs = DirectHttp.getJson<EthTxList>("$base/addresses/$enc/transactions")
            txs.items.mapNotNull { t ->
                val hash = t.hash ?: return@mapNotNull null
                val from = t.from?.hash?.lowercase().orEmpty()
                val to = t.to?.hash?.lowercase().orEmpty()
                val direction = if (from == lower && to == lower) "self" else if (from == lower) "out" else if (to == lower) "in" else "unknown"
                TxSummary(
                    hash = hash,
                    time = t.timestamp,
                    direction = direction,
                    value = (t.value?.toDoubleOrNull() ?: 0.0) / 1e18,
                    counterparty = if (direction == "out") t.to?.hash else t.from?.hash,
                )
            }
        }.getOrDefault(emptyList())

        val flow = if (transactions.isNotEmpty()) {
            val totalIn = transactions.filter { it.direction == "in" }.sumOf { it.value }
            val totalOut = transactions.filter { it.direction == "out" }.sumOf { it.value }
            WalletFlow(totalIn = totalIn, totalOut = totalOut, net = totalIn - totalOut)
        } else null

        return RawWallet(
            balance = balance,
            txCount = transactions.size,
            historyComplete = transactions.isNotEmpty() && transactions.size < TX_PAGE,
            flow = flow,
            transactions = transactions,
            labels = labels,
        )
    }

    @Serializable
    private data class SolBalanceValue(val `value`: Long? = null)
    @Serializable
    private data class SolBalanceResponse(val result: SolBalanceValue? = null)
    @Serializable
    private data class SolSignature(val signature: String? = null, val blockTime: Long? = null)
    @Serializable
    private data class SolSignaturesResponse(val result: List<SolSignature>? = null)

    private suspend fun collectSolana(address: String): RawWallet {
        val url = "https://api.mainnet-beta.solana.com"
        val balanceResponse = rpcJson.decodeFromString<SolBalanceResponse>(rpcBody(url, "getBalance", listOf(address)))
        val balance = (balanceResponse.result?.value ?: 0L) / 1e9

        val transactions = runCatching {
            val sigsResponse = rpcJson.decodeFromString<SolSignaturesResponse>(
                rpcBody(url, "getSignaturesForAddress", listOf(address, mapOf("limit" to 50))),
            )
            sigsResponse.result.orEmpty().mapNotNull { s ->
                val sig = s.signature ?: return@mapNotNull null
                TxSummary(
                    hash = sig,
                    time = s.blockTime?.let { Instant.ofEpochSecond(it).toString() },
                    direction = "unknown",
                    value = 0.0,
                    counterparty = null,
                )
            }
        }.getOrDefault(emptyList())

        return RawWallet(
            balance = balance,
            txCount = transactions.size,
            historyComplete = transactions.isNotEmpty() && transactions.size < TX_PAGE,
            flow = null,
            transactions = transactions,
            labels = emptyList(),
        )
    }

    private val rpcJson = Json { ignoreUnknownKeys = true }

    private suspend fun rpcBody(url: String, method: String, params: List<Any?>): String {
        val paramsJson = encodeRpcParams(params)
        val body = """{"jsonrpc":"2.0","id":1,"method":"$method","params":$paramsJson}"""
        return DirectHttp.postJson(url, body)
    }

    private fun encodeRpcParams(params: List<Any?>): String = buildString {
        append('[')
        params.forEachIndexed { i, p ->
            if (i > 0) append(',')
            when (p) {
                is String -> append('"').append(p).append('"')
                is Map<*, *> -> {
                    append('{')
                    p.entries.forEachIndexed { j, (k, v) ->
                        if (j > 0) append(',')
                        append('"').append(k).append("\":")
                        when (v) {
                            is String -> append('"').append(v).append('"')
                            is Number -> append(v)
                            else -> append(v)
                        }
                    }
                    append('}')
                }
                else -> append(p)
            }
        }
        append(']')
    }

    // ── analysis ─────────────────────────────────────────────────────

    private fun aggregateCounterparties(txs: List<TxSummary>): List<Counterparty> {
        val map = LinkedHashMap<String, Counterparty>()
        for (t in txs) {
            val cp = t.counterparty ?: continue
            val dir = if (t.direction == "in" || t.direction == "out") t.direction else null
            val existing = map[cp]
            if (existing != null) {
                val newDirection = if (dir != null && existing.direction != dir && existing.direction != "both") "both" else existing.direction
                map[cp] = existing.copy(txs = existing.txs + 1, value = existing.value + t.value, direction = newDirection)
            } else {
                map[cp] = Counterparty(address = cp, direction = dir ?: "both", txs = 1, value = t.value)
            }
        }
        return map.values.sortedWith(compareByDescending<Counterparty> { it.txs }.thenByDescending { it.value }).take(15)
    }

    private val LEVEL_ORDER = listOf("info", "low", "medium", "high", "critical")

    private fun scoreRisk(factors: List<RiskFactor>): Pair<Int, String> {
        val score = max(0, min(100, factors.sumOf { it.weight }))
        val highest = factors.fold("info") { acc, f -> if (LEVEL_ORDER.indexOf(f.severity) > LEVEL_ORDER.indexOf(acc)) f.severity ?: acc else acc }
        if (highest == "critical") return max(score, 95) to "critical"
        val level = when {
            score >= 70 -> "high"
            score >= 40 -> "medium"
            score >= 15 -> "low"
            else -> "info"
        }
        return score to level
    }

    private fun buildFactors(
        raw: RawWallet,
        symbol: String,
        ageDays: Int?,
        dormantDays: Int?,
        counterparties: List<Counterparty>,
        sanctionHits: List<SanctionEntry>,
    ): List<RiskFactor> {
        val f = mutableListOf<RiskFactor>()

        if (sanctionHits.isNotEmpty()) {
            f += RiskFactor(
                code = "OFAC_SDN", label = "OFAC-sanctioned address", severity = "critical", weight = 100,
                detail = "Listed on the US OFAC SDN list as: ${sanctionHits.take(3).joinToString("; ") { it.name }}",
            )
        }
        if (ageDays != null && ageDays < 7) {
            f += RiskFactor(code = "NEW_ADDRESS", label = "Recently created", severity = "low", weight = 10, detail = "First activity $ageDays day(s) ago.")
        }
        if (dormantDays != null && ageDays != null && dormantDays > 365 && ageDays > 400) {
            f += RiskFactor(code = "DORMANT", label = "Long dormant", severity = "info", weight = 5, detail = "No activity for $dormantDays days.")
        }
        if (raw.txCount > 10000) {
            f += RiskFactor(
                code = "HIGH_VOLUME", label = "Exchange-scale activity", severity = "info", weight = 5,
                detail = "${raw.txCount} transactions — consistent with an exchange, service or pooled wallet rather than a personal one.",
            )
        }
        if (counterparties.size >= 12 && raw.txCount <= 10000) {
            f += RiskFactor(
                code = "FAN_PATTERN", label = "High counterparty fan-out", severity = "medium", weight = 20,
                detail = "${counterparties.size} distinct counterparties across ${raw.transactions.size} sampled transactions — a distribution pattern also seen in mixing and payout scripts.",
            )
        }
        // No FAILED_TXS factor here — the backend derives it from a per-tx `failed` flag that
        // this app's TxSummary DTO doesn't carry (nothing renders it), so there's nothing to
        // count.
        if (raw.flow != null && raw.flow.totalIn > 0 && raw.balance / raw.flow.totalIn < 0.01 && raw.txCount > 5) {
            f += RiskFactor(
                code = "DRAINED", label = "Swept balance", severity = "medium", weight = 15,
                detail = "Received ${"%.4f".format(raw.flow.totalIn)} $symbol but retains ${"%.4f".format(raw.balance)} — funds were forwarded on rather than held.",
            )
        }
        if (f.isEmpty()) {
            f += RiskFactor(code = "NOMINAL", label = "No risk indicators", severity = "info", weight = 0, detail = "Nothing in the sampled data matched a risk heuristic.")
        }
        return f
    }
}
