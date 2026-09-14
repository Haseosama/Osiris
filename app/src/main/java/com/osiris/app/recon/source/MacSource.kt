package com.osiris.app.recon.source

import com.osiris.app.data.source.DirectHttp
import com.osiris.app.recon.MacResult
import kotlinx.serialization.Serializable
import java.net.URLEncoder

/** maclookup.app vendor lookup, called directly from the phone — mirrors
 * `osiris-backend/src/app/api/osint/mac/route.ts`. Keyless (free anonymous tier). */
object MacSource {

    @Serializable
    private data class MacLookupResponse(
        val found: Boolean = false,
        val company: String? = null,
        val address: String? = null,
        val macPrefix: String? = null,
    )

    suspend fun lookup(macInput: String): MacResult {
        val clean = macInput.trim().uppercase().replace(Regex("[^A-F0-9:-]"), "")
        val data = runCatching {
            DirectHttp.getJson<MacLookupResponse>(
                "https://api.maclookup.app/v2/macs/${URLEncoder.encode(clean, "UTF-8")}",
                headers = mapOf("Accept" to "application/json"),
            )
        }.getOrNull() ?: return MacResult(mac = clean, error = "MAC lookup failed")

        return if (data.found) {
            MacResult(
                mac = clean,
                vendor = data.company ?: "Unknown",
                address = data.address.orEmpty(),
                prefix = data.macPrefix ?: clean.take(8),
            )
        } else {
            MacResult(mac = clean, vendor = "Not Found")
        }
    }
}
