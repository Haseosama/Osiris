package com.osiris.app.recon.source

import com.osiris.app.data.source.DirectHttp
import com.osiris.app.recon.CveAffected
import com.osiris.app.recon.CveResult
import kotlinx.serialization.Serializable
import java.net.URLEncoder

/** MITRE CVE 5.0 lookup, falling back to CIRCL — called directly from the phone, mirrors
 * `osiris-backend/src/app/api/osint/cve/route.ts`. Keyless. */
object CveSource {

    private val FORMAT = Regex("^CVE-\\d{4}-\\d{4,}$", RegexOption.IGNORE_CASE)

    suspend fun lookup(cveInput: String): CveResult {
        val cve = cveInput.trim().uppercase()
        if (!FORMAT.matches(cve)) return CveResult(id = cve, error = "Invalid CVE format. Expected: CVE-YYYY-NNNNN")

        val mitre = runCatching {
            DirectHttp.getJson<MitreCve>("https://cveawg.mitre.org/api/cve/${URLEncoder.encode(cve, "UTF-8")}")
        }.getOrNull()

        if (mitre != null) return mitre.toResult(cve)

        val circl = runCatching {
            DirectHttp.getJson<CirclCve>("https://cve.circl.lu/api/cve/${URLEncoder.encode(cve, "UTF-8")}")
        }.getOrNull()

        if (circl != null) {
            return CveResult(
                id = circl.id ?: cve,
                description = circl.summary ?: "No description available.",
                cvss = circl.cvss,
                cvssVector = circl.cvss_vector,
                references = circl.references.take(5),
                published = circl.Published,
                modified = circl.Modified,
                cwe = circl.cwe,
                source = "circl",
            )
        }

        return CveResult(id = cve, description = "CVE details could not be retrieved at this time.", source = "unavailable")
    }

    @Serializable
    private data class CirclCve(
        val id: String? = null,
        val summary: String? = null,
        val cvss: Double? = null,
        val cvss_vector: String? = null,
        val references: List<String> = emptyList(),
        val Published: String? = null,
        val Modified: String? = null,
        val cwe: String? = null,
    )

    @Serializable
    private data class MitreCve(
        val cveMetadata: MitreMeta? = null,
        val containers: MitreContainers? = null,
    ) {
        fun toResult(fallbackId: String): CveResult {
            val cna = containers?.cna
            val description = cna?.descriptions?.firstOrNull { it.lang == "en" }?.value
                ?: cna?.descriptions?.firstOrNull()?.value
                ?: "No description available."

            var cvss: Double? = null
            var cvssVector: String? = null
            var severity: String? = null
            for (m in cna?.metrics.orEmpty()) {
                val v31 = m.cvssV3_1 ?: m.cvssV3_0 ?: m.cvssV31
                if (v31 != null) {
                    cvss = v31.baseScore
                    cvssVector = v31.vectorString
                    severity = v31.baseSeverity
                    break
                }
                val v2 = m.cvssV2_0 ?: m.cvssV2
                if (v2 != null) {
                    cvss = v2.baseScore
                    cvssVector = v2.vectorString
                    break
                }
            }

            val cwe = cna?.problemTypes?.firstOrNull()?.descriptions?.firstOrNull()
                ?.let { it.cweId ?: it.description }

            val references = cna?.references.orEmpty().take(5).mapNotNull { it.url }
            val affected = cna?.affected.orEmpty().take(5).map {
                CveAffected(
                    vendor = it.vendor ?: "Unknown",
                    product = it.product ?: "Unknown",
                    versions = it.versions.take(3).mapNotNull { v -> v.version },
                )
            }

            val resolvedSeverity = severity ?: cvss?.let {
                when {
                    it >= 9 -> "CRITICAL"
                    it >= 7 -> "HIGH"
                    it >= 4 -> "MEDIUM"
                    else -> "LOW"
                }
            }

            return CveResult(
                id = cveMetadata?.cveId ?: fallbackId,
                description = description,
                cvss = cvss,
                cvssVector = cvssVector,
                severity = resolvedSeverity,
                cwe = cwe,
                affected = affected,
                references = references,
                published = cveMetadata?.datePublished,
                modified = cveMetadata?.dateUpdated,
                source = "mitre",
            )
        }
    }

    @Serializable private data class MitreMeta(val cveId: String? = null, val datePublished: String? = null, val dateUpdated: String? = null)
    @Serializable private data class MitreContainers(val cna: MitreCna? = null)
    @Serializable private data class MitreCna(
        val descriptions: List<MitreDescription> = emptyList(),
        val metrics: List<MitreMetric> = emptyList(),
        val problemTypes: List<MitreProblemType> = emptyList(),
        val references: List<MitreReference> = emptyList(),
        val affected: List<MitreAffected> = emptyList(),
    )
    @Serializable private data class MitreDescription(val lang: String? = null, val value: String? = null)
    @Serializable private data class MitreMetric(
        val cvssV3_1: MitreCvss? = null,
        val cvssV3_0: MitreCvss? = null,
        val cvssV31: MitreCvss? = null,
        val cvssV2_0: MitreCvss? = null,
        val cvssV2: MitreCvss? = null,
    )
    @Serializable private data class MitreCvss(val baseScore: Double? = null, val vectorString: String? = null, val baseSeverity: String? = null)
    @Serializable private data class MitreProblemType(val descriptions: List<MitreProblemDescription> = emptyList())
    @Serializable private data class MitreProblemDescription(val cweId: String? = null, val description: String? = null)
    @Serializable private data class MitreReference(val url: String? = null)
    @Serializable private data class MitreAffected(val vendor: String? = null, val product: String? = null, val versions: List<MitreVersion> = emptyList())
    @Serializable private data class MitreVersion(val version: String? = null)
}
