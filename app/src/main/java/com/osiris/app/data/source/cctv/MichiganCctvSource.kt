package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.source.DirectHttp
import kotlinx.serialization.Serializable

/** Michigan road cameras (MDOT MiDrive, ~800 statewide), called directly from the phone —
 * mirrors `osiris-backend/src/app/api/cctv/michigan.ts`. Keyless, but the JSON fields carry
 * rendered HTML rather than plain values — coordinates live inside the `county` field's "Go to"
 * link and the frame URL inside `image`'s `<img src>` — both regex-extracted, same as the
 * backend. */
object MichiganCctvSource {
    private const val MIN_LAT = 41.6
    private const val MAX_LAT = 48.3
    private const val MIN_LNG = -90.5
    private const val MAX_LNG = -82.1

    private val COORDS_REGEX = Regex("""lat=(-?[\d.]+)&(?:amp;)?lon=(-?[\d.]+)""")
    // Not a raw triple-quoted string: the pattern's own trailing `"` would run straight into the
    // closing `"""` and form four quotes in a row, which Kotlin reads as the *earlier* three of
    // them closing the string — leaving a dangling quote and a syntax error. Escaped instead.
    private val SRC_REGEX = Regex("src=\"(https?://[^\"]+)\"")
    private val ID_REGEX = Regex("""[?&]id=(\d+)""")
    private val ANCHOR_REGEX = Regex("<a\\b[\\s\\S]*?</a>", RegexOption.IGNORE_CASE)
    private val ANY_TAG_REGEX = Regex("<[^>]*>")
    private val WHITESPACE_REGEX = Regex("\\s+")

    private fun stripHtml(s: String): String =
        s.replace(ANCHOR_REGEX, "").replace(ANY_TAG_REGEX, "").replace(WHITESPACE_REGEX, " ").trim()

    private fun mapRecord(rec: MiDriveRecord): CctvCamera? {
        val county = rec.county.orEmpty()
        val image = rec.image.orEmpty()

        val coords = COORDS_REGEX.find(county) ?: return null
        val src = SRC_REGEX.find(image) ?: return null
        val id = ID_REGEX.find(county)?.groupValues?.get(1) ?: return null

        val lat = coords.groupValues[1].toDoubleOrNull() ?: return null
        val lng = coords.groupValues[2].toDoubleOrNull() ?: return null
        if (lat < MIN_LAT || lat > MAX_LAT || lng < MIN_LNG || lng > MAX_LNG) return null

        val route = rec.route.orEmpty().trim()
        val location = rec.location.orEmpty().trim()
        val name = listOf(route, location).filter { it.isNotEmpty() }.joinToString(" ").trim()

        return CctvCamera(
            id = "mdot-$id",
            lat = lat,
            lng = lng,
            name = name.ifEmpty { "MDOT Camera $id" },
            city = stripHtml(county).ifEmpty { "Michigan" },
            country = "US",
            feedUrl = src.groupValues[1],
            source = "MDOT MiDrive",
        )
    }

    suspend fun fetch(): List<CctvCamera> = runCatching {
        val records = DirectHttp.getJson<List<MiDriveRecord>>(
            "https://mdotjboss.state.mi.us/MiDrive/camera/list",
            headers = mapOf("Accept" to "application/json"),
        )
        val seen = mutableSetOf<String>()
        records.mapNotNull { rec -> mapRecord(rec)?.takeIf { seen.add(it.id) } }
    }.getOrDefault(emptyList())

    @Serializable
    private data class MiDriveRecord(
        val route: String? = null,
        val county: String? = null,
        val location: String? = null,
        val direction: String? = null,
        val image: String? = null,
    )
}
