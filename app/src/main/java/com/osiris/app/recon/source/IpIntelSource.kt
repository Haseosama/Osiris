package com.osiris.app.recon.source

import com.osiris.app.data.source.DirectHttp
import com.osiris.app.recon.IpGeo
import com.osiris.app.recon.IpIntelResult
import com.osiris.app.recon.IpReputation
import com.osiris.app.recon.SanctionsHit
import com.osiris.app.recon.SanctionsMatchBlock
import kotlinx.serialization.Serializable

/** IP geolocation + reputation, called directly from the phone — mirrors
 * `osiris-backend/src/app/api/osint/ip/route.ts`. Cross-checks the ASN/ISP/org strings against
 * the OFAC SDN list via [SanctionsIndex]. ip-api.com's free tier is plain HTTP, not HTTPS — no
 * extra manifest wiring needed for that though, `usesCleartextTraffic="true"` is already set
 * app-wide (for the self-hosted backend, often reachable over plain http:// on a LAN). */
object IpIntelSource {

    @Serializable
    private data class IpApiResponse(
        val status: String? = null,
        val country: String? = null,
        val countryCode: String? = null,
        val regionName: String? = null,
        val city: String? = null,
        val lat: Double? = null,
        val lon: Double? = null,
        val timezone: String? = null,
        val isp: String? = null,
        val org: String? = null,
        val `as`: String? = null,
        val asname: String? = null,
        val mobile: Boolean = false,
        val proxy: Boolean = false,
        val hosting: Boolean = false,
    )

    suspend fun lookup(ip: String): IpIntelResult {
        val geo = runCatching {
            val fields = "status,message,continent,country,countryCode,region,regionName,city,zip,lat,lon," +
                "timezone,isp,org,as,asname,mobile,proxy,hosting,query"
            val data = DirectHttp.getJson<IpApiResponse>("http://ip-api.com/json/$ip?fields=$fields")
            if (data.status != "success") return@runCatching null
            IpGeo(
                country = data.country,
                countryCode = data.countryCode,
                region = data.regionName,
                city = data.city,
                isp = data.isp,
                org = data.org,
                asNumber = data.`as`,
                asName = data.asname,
            ) to Triple(data.proxy, data.hosting, data.mobile)
        }.getOrNull()

        val (geoResult, flags) = geo ?: (null to null)
        val (isProxy, isHosting, isMobile) = flags ?: Triple(false, false, false)

        val reputation = IpReputation(
            isProxy = isProxy,
            isHosting = isHosting,
            isMobile = isMobile,
            riskLevel = if (isProxy) "HIGH" else if (isHosting) "MEDIUM" else "LOW",
        )

        val sanctionsMatch = runCatching {
            val candidates = linkedSetOf<String>()
            geoResult?.org?.let(candidates::add)
            geoResult?.isp?.let(candidates::add)
            geoResult?.asName?.let(candidates::add)
            val hits = candidates.mapNotNull { value ->
                val entries = SanctionsIndex.matchExact(value)
                if (entries.isNotEmpty()) SanctionsHit(matchedValue = value, entries = entries) else null
            }
            hits.takeIf { it.isNotEmpty() }?.let { SanctionsMatchBlock(source = "OFAC SDN", hits = it) }
        }.getOrNull()

        return IpIntelResult(ip = ip, geo = geoResult, reputation = reputation, sanctionsMatch = sanctionsMatch)
    }
}
