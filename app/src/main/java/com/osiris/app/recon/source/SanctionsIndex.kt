package com.osiris.app.recon.source

import com.osiris.app.data.source.DirectHttp
import com.osiris.app.recon.SanctionEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * OFAC SDN sanctioned-entity lookup, called directly from the phone — mirrors
 * `osiris-backend/src/lib/sanctions.ts`, shared there by the WHOIS/IP/Sanctions routes exactly
 * as it is here. Source: [OpenSanctions](https://www.opensanctions.org/) `us_ofac_sdn` CSV
 * (~7MB, low-tens-of-thousands of rows), fetched once and cached in memory for 24h. A [Mutex]
 * plays the same role the backend's single in-flight promise did — concurrent lookups that
 * arrive while a refresh is running wait on the same fetch instead of triggering their own.
 */
object SanctionsIndex {

    private const val SDN_CSV_URL = "https://data.opensanctions.org/datasets/latest/us_ofac_sdn/targets.simple.csv"
    private const val TTL_MS = 24 * 60 * 60 * 1000L

    private class LoadedList(
        val entries: List<SanctionEntry>,
        val byNormName: Map<String, List<SanctionEntry>>,
        val fetchedAt: Long,
    )

    private val mutex = Mutex()
    @Volatile private var cache: LoadedList? = null

    /** Lower-case, strip punctuation, collapse whitespace — used for both index keys and
     * incoming query normalization so the same string yields the same key on both sides. */
    private fun normName(s: String): String =
        s.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private suspend fun loadList(): LoadedList {
        cache?.let { if (System.currentTimeMillis() - it.fetchedAt < TTL_MS) return it }
        return mutex.withLock {
            cache?.let { if (System.currentTimeMillis() - it.fetchedAt < TTL_MS) return@withLock it }
            runCatching { fetchAndParse() }
                .onSuccess { cache = it }
                .getOrElse { cache ?: throw it }
        }
    }

    private suspend fun fetchAndParse(): LoadedList {
        val text = DirectHttp.getText(SDN_CSV_URL, headers = mapOf("Accept" to "text/csv"))
        val rows = parseCsv(text)
        require(rows.size >= 2) { "OpenSanctions CSV empty" }

        val headers = rows[0]
        fun idx(col: String) = headers.indexOf(col)
        val iId = idx("id"); val iSchema = idx("schema"); val iName = idx("name")
        val iAliases = idx("aliases"); val iCountries = idx("countries")
        val iPrograms = idx("program_ids"); val iSanctions = idx("sanctions")

        val entries = mutableListOf<SanctionEntry>()
        val byNormName = mutableMapOf<String, MutableList<SanctionEntry>>()

        for (r in 1 until rows.size) {
            val row = rows[r]
            val name = row.getOrNull(iName)?.takeIf { it.isNotBlank() } ?: continue
            val aliases = row.getOrNull(iAliases).orEmpty().split(";").map { it.trim() }.filter { it.isNotEmpty() }
            val entry = SanctionEntry(
                id = row.getOrNull(iId).orEmpty(),
                schema = row.getOrNull(iSchema)?.takeIf { it.isNotBlank() } ?: "LegalEntity",
                name = name,
                aliases = aliases,
                countries = row.getOrNull(iCountries).orEmpty().split(";").map { it.trim() }.filter { it.isNotEmpty() },
                programs = row.getOrNull(iPrograms).orEmpty().split(";").map { it.trim() }.filter { it.isNotEmpty() },
                sanctions = row.getOrNull(iSanctions),
            )
            entries += entry

            val keys = (listOf(entry.name) + entry.aliases).map { normName(it) }.filter { it.isNotEmpty() }.toSet()
            for (key in keys) byNormName.getOrPut(key) { mutableListOf() }.add(entry)
        }

        return LoadedList(entries, byNormName, System.currentTimeMillis())
    }

    /** Minimal CSV parser tolerant of double-quoted fields with embedded commas, newlines and
     * `""` escapes — the OpenSanctions CSV is well-formed so nothing heavier is needed. */
    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else when (c) {
                '"' -> inQuotes = true
                ',' -> { row.add(field.toString()); field = StringBuilder() }
                '\n' -> { row.add(field.toString()); rows.add(row); row = mutableListOf(); field = StringBuilder() }
                '\r' -> {}
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }

    /** Strict exact-match lookup — used for inline cross-checks (WHOIS/IP) where false
     * positives must be avoided: "Acme" should NOT match "Acme Holdings" automatically. */
    suspend fun matchExact(query: String): List<SanctionEntry> {
        if (query.length < 3) return emptyList()
        return loadList().byNormName[normName(query)].orEmpty()
    }

    /** Substring + alias-aware search for the standalone Sanctions tool — ranked exact name,
     * exact alias, substring of name, substring of alias. */
    suspend fun search(query: String, schema: String? = null, limit: Int = 50): List<SanctionEntry> {
        if (query.length < 4) return emptyList()
        val list = loadList()
        val q = normName(query)

        val exactName = mutableListOf<SanctionEntry>()
        val exactAlias = mutableListOf<SanctionEntry>()
        val subName = mutableListOf<SanctionEntry>()
        val subAlias = mutableListOf<SanctionEntry>()
        val seen = mutableSetOf<String>()

        fun push(bucket: MutableList<SanctionEntry>, e: SanctionEntry) {
            if (!seen.add(e.id)) return
            if (schema != null && e.schema != schema) { seen.remove(e.id); return }
            bucket += e
        }

        for (entry in list.entries) {
            val nameNorm = normName(entry.name)
            when {
                nameNorm == q -> push(exactName, entry)
                entry.aliases.any { normName(it) == q } -> push(exactAlias, entry)
                nameNorm.contains(q) -> push(subName, entry)
                entry.aliases.any { normName(it).contains(q) } -> push(subAlias, entry)
            }
            if (seen.size >= limit * 4) break
        }

        return (exactName + exactAlias + subName + subAlias).take(limit)
    }
}
