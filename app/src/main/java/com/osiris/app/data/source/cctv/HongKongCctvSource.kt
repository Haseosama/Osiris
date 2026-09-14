package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.source.DirectHttp

/** Hong Kong road cameras (Transport Department, ~1,000 with published coordinates), called
 * directly from the phone — mirrors `osiris-backend/src/app/api/cctv/hongkong.ts`. Keyless XML
 * index. The backend's curated fallback (used only when data.gov.hk is unreachable and nothing
 * was ever cached) isn't ported — a transient failure here just yields an empty list for that
 * source on that poll, same simplification made throughout this migration for on-failure
 * fallbacks the app has no persistent cache to serve from anyway. */
object HongKongCctvSource {
    private const val LOCATIONS_XML =
        "https://static.data.gov.hk/td/traffic-snapshot-images/code/Traffic_Camera_Locations_En.xml"
    private const val MIN_LAT = 22.1
    private const val MAX_LAT = 22.6
    private const val MIN_LNG = 113.8
    private const val MAX_LNG = 114.5

    private val TRAILING_KEY_REGEX = Regex("""\s*\[[^\]]*\]\s*$""")

    private fun decodeXml(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&").trim()

    private fun tag(block: String, name: String): String =
        Regex("<$name>([\\s\\S]*?)</$name>").find(block)?.groupValues?.get(1)?.let(::decodeXml).orEmpty()

    suspend fun fetch(): List<CctvCamera> = runCatching {
        val xml = DirectHttp.getText(LOCATIONS_XML, headers = mapOf("Accept" to "application/xml,text/xml"))
        val seen = mutableSetOf<String>()
        xml.split("<image>").drop(1).mapNotNull { block ->
            val key = tag(block, "key").takeIf { it.isNotEmpty() && seen.add(it) } ?: return@mapNotNull null
            val url = tag(block, "url").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val lat = tag(block, "latitude").toDoubleOrNull() ?: return@mapNotNull null
            val lng = tag(block, "longitude").toDoubleOrNull() ?: return@mapNotNull null
            if (lat < MIN_LAT || lat > MAX_LAT || lng < MIN_LNG || lng > MAX_LNG) return@mapNotNull null

            val name = tag(block, "description").replace(TRAILING_KEY_REGEX, "")
            val city = tag(block, "district").ifEmpty { tag(block, "region") }.ifEmpty { "Hong Kong" }

            CctvCamera(
                id = "hk-$key",
                lat = lat,
                lng = lng,
                name = name.ifEmpty { "HK Camera $key" },
                city = city,
                country = "Hong Kong",
                feedUrl = url,
                source = "HK Transport Dept",
            )
        }
    }.getOrDefault(emptyList())
}
