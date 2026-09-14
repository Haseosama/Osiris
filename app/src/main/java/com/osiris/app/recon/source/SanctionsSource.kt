package com.osiris.app.recon.source

import com.osiris.app.recon.SanctionsResult

/** Standalone OFAC SDN search tool, called directly from the phone — mirrors
 * `osiris-backend/src/app/api/osint/sanctions/route.ts`. Thin wrapper around [SanctionsIndex]. */
object SanctionsSource {

    private val ALLOWED_SCHEMAS = setOf("Person", "Organization", "Company", "Vessel", "Airplane", "LegalEntity")

    suspend fun search(query: String, schema: String?, limit: Int = 25): SanctionsResult {
        val trimmed = query.trim()
        if (trimmed.length < 4) return SanctionsResult(query = trimmed, error = "Query must be at least 4 characters")
        if (schema != null && schema !in ALLOWED_SCHEMAS) {
            return SanctionsResult(query = trimmed, error = "Invalid schema. Allowed: ${ALLOWED_SCHEMAS.joinToString(", ")}")
        }
        val clampedLimit = limit.coerceIn(1, 100)
        val matches = SanctionsIndex.search(trimmed, schema, clampedLimit)
        return SanctionsResult(
            query = trimmed,
            schema = schema,
            total = matches.size,
            matches = matches,
            source = "OpenSanctions / US OFAC SDN",
        )
    }
}
