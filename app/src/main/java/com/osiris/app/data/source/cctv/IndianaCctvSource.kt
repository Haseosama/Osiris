package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.remote.NetworkModule
import com.osiris.app.data.source.DirectHttp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Indiana highway cameras (INDOT TrafficWise / 511in.org, ~730 statewide), called directly
 * from the phone — mirrors `osiris-backend/src/app/api/cctv/indiana.ts`. Every camera streams
 * HLS; the API only ever hands back a slowly-refreshing poster frame
 * (`…/INDOT_528_sdRf2LeZp7VYFgcF.flv.png`), never the playlist URL itself — the playlist is
 * rebuilt from the same `INDOT_{id}_{token}` the poster embeds. Only the `skysfs4` edge actually
 * serves real segments; the others answer 200 with an endless placeholder loop that never
 * resolves to a frame (measured against all of them over two minutes straight, per the
 * backend's own comment) — so this hardcodes that edge rather than trusting a "healthy" 200. */
object IndianaCctvSource {
    private const val GRAPHQL_URL = "https://511in.org/api/graphql"
    private const val STREAM_HOST = "https://skysfs4.trafficwise.org"
    private const val MIN_LAT = 37.7
    private const val MAX_LAT = 41.9
    private const val MIN_LNG = -88.2
    private const val MAX_LNG = -84.6

    private val POSTER_REGEX = Regex("""/cameras/IN/(INDOT_\d+_[A-Za-z0-9_-]+)\.flv\.png$""")
    private val URI_ID_REGEX = Regex("""camera/(\d+)""")
    // INDOT titles read "I-94: 1-094-035-8-1 E OF US421" — a route, then an internal asset
    // number that's noise on a map label. Only stripped when cleanly delimited: ~50 titles glue
    // the place straight onto the number with nothing marking where it ends, and those keep
    // their original title rather than get mangled.
    private val ASSET_NUMBER_REGEX = Regex("""(^|:\s*)\d+(?:-[0-9a-z_]+)*\s+""")

    // Not `const val`: a string template (even one whose only interpolation is the literal `$`
    // needed for GraphQL's own `$input` variable syntax) isn't a compile-time constant in Kotlin.
    private val QUERY = """query MapFeatures(${'$'}input: MapFeaturesArgs!) {
  mapFeaturesQuery(input: ${'$'}input) {
    mapFeatures {
      title
      uri
      features { geometry }
      __typename
      ... on Camera { active views(limit: 1) { category ... on CameraView { url } } }
    }
    error { message }
  }
}"""

    private fun cleanTitle(title: String): String =
        title.replace(ASSET_NUMBER_REGEX, "$1").replace(Regex("\\s+"), " ").trim()

    private fun mapFeature(f: MapFeature): CctvCamera? {
        if (f.typename != "Camera" || f.active == false) return null
        val url = f.views?.firstOrNull()?.url.orEmpty()
        val token = POSTER_REGEX.find(url)?.groupValues?.get(1) ?: return null

        val coords = f.features?.firstOrNull()?.geometry?.coordinates ?: return null
        val lng = coords.getOrNull(0) ?: return null
        val lat = coords.getOrNull(1) ?: return null
        if (lat < MIN_LAT || lat > MAX_LAT || lng < MIN_LNG || lng > MAX_LNG) return null

        val id = f.uri?.let { URI_ID_REGEX.find(it)?.groupValues?.get(1) } ?: return null
        val title = f.title?.let(::cleanTitle)?.takeIf { it.isNotEmpty() } ?: "INDOT Camera $id"

        return CctvCamera(
            id = "indot-$id",
            lat = lat,
            lng = lng,
            name = title,
            city = "Indiana",
            country = "US",
            streamUrl = "$STREAM_HOST/preroll/$token/playlist.m3u8",
            externalUrl = "https://511in.org/@$lat,$lng,14?show=${java.net.URLEncoder.encode(f.uri.orEmpty(), "UTF-8")}",
            source = "INDOT TrafficWise",
        )
    }

    suspend fun fetch(): List<CctvCamera> = runCatching {
        // `zoom` decides clustering server-side — it has to be high enough that the API returns
        // individual cameras rather than cluster markers, hence 16 for a single whole-state call.
        val payload = NetworkModule.json.encodeToString(
            JsonElement.serializer(),
            buildJsonObject {
                put("query", QUERY)
                put(
                    "variables",
                    buildJsonObject {
                        put(
                            "input",
                            buildJsonObject {
                                put("north", MAX_LAT)
                                put("south", MIN_LAT)
                                put("east", MAX_LNG)
                                put("west", MIN_LNG)
                                put("zoom", 16)
                                putJsonArray("layerSlugs") { add(JsonPrimitive("normalCameras")) }
                                put("nonClusterableUris", JsonNull)
                            },
                        )
                    },
                )
            },
        )
        val responseText = DirectHttp.postJson(
            GRAPHQL_URL,
            payload,
            headers = mapOf("Accept" to "application/json", "Referer" to "https://511in.org/"),
        )
        val response = NetworkModule.json.decodeFromString(GraphQlResponse.serializer(), responseText)
        val features = response.data?.mapFeaturesQuery?.mapFeatures.orEmpty()
        val seen = mutableSetOf<String>()
        features.mapNotNull { f -> mapFeature(f)?.takeIf { seen.add(it.id) } }
    }.getOrDefault(emptyList())

    @Serializable
    private data class GraphQlResponse(val data: GraphQlData? = null)

    @Serializable
    private data class GraphQlData(val mapFeaturesQuery: MapFeaturesQuery? = null)

    @Serializable
    private data class MapFeaturesQuery(val mapFeatures: List<MapFeature> = emptyList())

    @Serializable
    private data class MapFeature(
        val title: String? = null,
        val uri: String? = null,
        val features: List<FeatureGeometry>? = null,
        val __typename: String? = null,
        val active: Boolean? = null,
        val views: List<CameraView>? = null,
    ) {
        val typename get() = __typename
    }

    @Serializable
    private data class FeatureGeometry(val geometry: Geometry? = null)

    @Serializable
    private data class Geometry(val coordinates: List<Double>? = null)

    @Serializable
    private data class CameraView(val url: String? = null, val category: String? = null)
}
