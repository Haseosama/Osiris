package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.source.DirectHttp

/** New Zealand state-highway cameras (NZTA Waka Kotahi, ~320 nationwide), called directly from
 * the phone — mirrors `osiris-backend/src/app/api/cctv/newzealand.ts`. Keyless XML feed.
 * Cameras flagged offline/under maintenance are skipped, same as the backend. */
object NewZealandCctvSource {
    private const val BASE = "https://trafficnz.info"
    private const val MIN_LAT = -47.5
    private const val MAX_LAT = -34.0
    private const val MIN_LNG = 166.0
    private const val MAX_LNG = 179.0

    // <region> is the only nested block whose own <name> we want; strip the rest before a flat
    // tag scan so a nested <id>/<name> inside them can't be mistaken for the camera's own.
    private val NESTED_REGEX = Regex("<(journey|journeyLeg|region|way)>[\\s\\S]*?</\\1>")
    private val REGION_BLOCK_REGEX = Regex("<region>[\\s\\S]*?</region>")

    private fun decodeXml(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&").trim()

    private fun tag(block: String, name: String): String =
        Regex("<$name>([\\s\\S]*?)</$name>").find(block)?.groupValues?.get(1)?.let(::decodeXml).orEmpty()

    suspend fun fetch(): List<CctvCamera> = runCatching {
        val xml = DirectHttp.getText(
            "https://trafficnz.info/service/traffic/rest/4/cameras/all",
            headers = mapOf("Accept" to "application/xml,text/xml"),
        )
        val seen = mutableSetOf<String>()
        xml.split("<camera>").drop(1).mapNotNull { raw ->
            val block = raw.substringBefore("</camera>")
            val region = REGION_BLOCK_REGEX.find(block)?.value?.let { tag(it, "name") }.orEmpty()
            val flat = block.replace(NESTED_REGEX, "")

            val id = tag(flat, "id").takeIf { it.isNotEmpty() && seen.add(it) } ?: return@mapNotNull null
            val imageUrl = tag(flat, "imageUrl").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val lat = tag(flat, "latitude").toDoubleOrNull() ?: return@mapNotNull null
            val lng = tag(flat, "longitude").toDoubleOrNull() ?: return@mapNotNull null
            if (tag(flat, "offline") == "true" || tag(flat, "underMaintenance") == "true") return@mapNotNull null
            if (lat < MIN_LAT || lat > MAX_LAT || lng < MIN_LNG || lng > MAX_LNG) return@mapNotNull null

            val name = tag(flat, "name").takeIf { it.isNotEmpty() }
                ?: tag(flat, "description").takeIf { it.isNotEmpty() }
                ?: "NZTA Camera $id"
            val direction = tag(flat, "direction")

            CctvCamera(
                id = "nz-$id",
                lat = lat,
                lng = lng,
                name = if (direction.isNotEmpty() && direction != "NA") "$name ($direction)" else name,
                city = region.ifEmpty { "New Zealand" },
                country = "New Zealand",
                feedUrl = "$BASE$imageUrl",
                externalUrl = "$BASE/camera/view/$id",
                source = "NZTA",
            )
        }
    }.getOrDefault(emptyList())
}
