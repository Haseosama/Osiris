package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.remote.NetworkModule
import com.osiris.app.data.source.DirectHttp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Illinois DOT highway cameras, called directly from the phone — mirrors `us-central` in
 * `osiris-backend/src/app/api/cctv/route.ts` (`fetchUSCentralCameras`). Keyless. The endpoint
 * answers `{updatedMessage, noDataMessage}` with no cameras at all when IDOT has nothing to
 * report — an object rather than the array a naive caller would assume — handled here the same
 * defensive way the backend does. */
object UsCentralCctvSource {
    suspend fun fetch(): List<CctvCamera> = runCatching {
        val root = NetworkModule.json.parseToJsonElement(
            DirectHttp.getText("https://www.travelmidwest.com/lmiga/cameraReport.json"),
        )
        val rows: JsonArray = when {
            root is JsonObject && root["cameraReports"] is JsonArray -> root["cameraReports"] as JsonArray
            root is JsonArray -> root
            else -> JsonArray(emptyList())
        }
        rows.take(800).mapIndexedNotNull { index, element ->
            val obj = element as? JsonObject ?: return@mapIndexedNotNull null
            val lat = obj["latitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapIndexedNotNull null
            val lng = obj["longitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapIndexedNotNull null
            val feedUrl = (obj["imageUrl"] ?: obj["url"])?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@mapIndexedNotNull null
            val name = (obj["cameraName"] ?: obj["description"])?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: "IDOT Camera"
            CctvCamera(
                id = "ildot-$index",
                lat = lat,
                lng = lng,
                name = name,
                city = "Illinois",
                country = "US",
                feedUrl = feedUrl,
                source = "IDOT",
            )
        }
    }.getOrDefault(emptyList())
}
