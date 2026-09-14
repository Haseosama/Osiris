package com.osiris.app.recon.source

import com.osiris.app.data.remote.NetworkModule
import com.osiris.app.data.source.DirectHttp
import com.osiris.app.recon.LeaksResult
import kotlinx.serialization.Serializable
import java.net.URLEncoder

/** XposedOrNot breach-analytics lookup, called directly from the phone — mirrors
 * `osiris-backend/src/app/api/osint/leaks/route.ts`. Keyless. */
object LeaksSource {

    private val UA = mapOf(
        "Accept" to "application/json",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 OSIRIS/1.0",
    )

    @Serializable
    private data class BreachAnalytics(
        val BreachesSummary: BreachesSummary? = null,
        val ExposedData: List<ExposedDataItem> = emptyList(),
    )

    @Serializable private data class BreachesSummary(val site: String? = null)
    @Serializable private data class ExposedDataItem(val data_classes: List<String> = emptyList())

    suspend fun lookup(email: String): LeaksResult {
        val url = "https://api.xposedornot.com/v1/breach-analytics?email=${URLEncoder.encode(email, "UTF-8")}"
        val body = runCatching { DirectHttp.getText(url, headers = UA) }.getOrNull()
            ?: return LeaksResult(email = email, breached = false, breaches = emptyList(), dataExposed = emptyList())
        val result = runCatching { NetworkModule.json.decodeFromString<BreachAnalytics>(body) }.getOrNull()
            ?: return LeaksResult(email = email, breached = false, breaches = emptyList(), dataExposed = emptyList())

        val breaches = result.BreachesSummary?.site?.split(';')?.filter { it.isNotBlank() } ?: emptyList()
        val exposed = result.ExposedData.flatMap { it.data_classes }.toSortedSet()

        return LeaksResult(
            email = email,
            breached = breaches.isNotEmpty(),
            breaches = breaches,
            dataExposed = exposed.toList(),
        )
    }
}
