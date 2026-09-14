package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.remote.NetworkModule
import com.osiris.app.data.source.DirectHttp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Ottawa, Quebec, Ontario, Montreal, Toronto, Alberta and British Columbia, called directly
 * from the phone — mirrors `osiris-backend/src/app/api/cctv/route.ts`'s `fetchCanadaCameras`.
 * Seven independent, unrelated sub-sources fetched in parallel; each is manually JsonElement-
 * walked rather than modeled with @Serializable data classes since every one has its own
 * unrelated shape and this is the only place in the app that reads any of them. A sub-source
 * that fails or times out just contributes nothing for that poll — no retry, no distinction
 * between a 4xx and a timeout, unlike the backend's own `subSource` helper; a simplification
 * consistent with the rest of this migration's on-failure handling. */
object CanadaCctvSource {

    private suspend fun fetchJson(url: String): JsonElement? =
        runCatching { NetworkModule.json.parseToJsonElement(DirectHttp.getText(url, headers = mapOf("Accept" to "application/json"))) }
            .getOrNull()

    private fun JsonElement?.asArray(): List<JsonElement> = (this as? JsonArray).orEmpty()
    private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject
    private fun JsonElement.str(key: String): String? = asObject()?.get(key)?.jsonPrimitive?.contentOrNull
    private fun JsonElement.num(key: String): Double? = asObject()?.get(key)?.jsonPrimitive?.doubleOrNull

    private suspend fun ottawa(): List<CctvCamera> {
        val data = fetchJson("https://traffic.ottawa.ca/beta/camera_list") ?: return emptyList()
        return data.asArray().mapNotNull { cam ->
            val lat = cam.num("latitude") ?: return@mapNotNull null
            val lng = cam.num("longitude") ?: return@mapNotNull null
            val id = cam.str("id") ?: return@mapNotNull null
            val number = cam.str("number") ?: id
            CctvCamera(
                id = "ottawa-muni-$id", lat = lat, lng = lng,
                name = cam.str("description") ?: "Ottawa Traffic Camera",
                city = "Ottawa", country = "Canada",
                feedUrl = "https://traffic.ottawa.ca/map/camera?id=$number",
                source = "City of Ottawa",
            )
        }
    }

    private suspend fun quebec(): List<CctvCamera> {
        val data = fetchJson(
            "https://ws.mapserver.transports.gouv.qc.ca/swtq?service=wfs&version=2.0.0&request=getfeature" +
                "&typename=ms:infos_cameras&outfile=Camera&srsname=EPSG:4326&outputformat=geojson",
        )?.asObject() ?: return emptyList()
        return (data["features"] as? JsonArray).orEmpty().mapNotNull { feature ->
            val obj = feature.asObject() ?: return@mapNotNull null
            val coords = (obj["geometry"]?.jsonObject?.get("coordinates") as? JsonArray) ?: return@mapNotNull null
            val lng = coords.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lat = coords.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val props = obj["properties"]?.jsonObject ?: return@mapNotNull null
            val camId = props["IDEcamera"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val flux = props["URL_FLUX_DONNEE"]?.jsonPrimitive?.contentOrNull
            val streamUrl = if (!flux.isNullOrBlank()) {
                flux.replace("FenetreVideo.html", "camera.ashx") + "&format=mp4"
            } else {
                "https://www.quebec511.info/Carte/Fenetres/camera.ashx?id=$camId&format=mp4"
            }
            CctvCamera(
                id = "quebec511-$camId", lat = lat, lng = lng,
                name = props["DescriptionLocalisationEn"]?.jsonPrimitive?.contentOrNull
                    ?: props["DescriptionLocalisationFr"]?.jsonPrimitive?.contentOrNull
                    ?: "Quebec 511 Camera",
                city = props["NomRegionDiffusion"]?.jsonPrimitive?.contentOrNull ?: "Quebec",
                country = "Canada",
                streamUrl = streamUrl,
                source = "Quebec 511",
            )
        }
    }

    private suspend fun ontario(): List<CctvCamera> {
        val data = fetchJson("https://511on.ca/api/v2/get/cameras") ?: return emptyList()
        return data.asArray().mapNotNull { cam ->
            val obj = cam.asObject() ?: return@mapNotNull null
            val views = (obj["Views"] as? JsonArray).orEmpty()
            val view = views.firstOrNull { it.asObject()?.get("Status")?.jsonPrimitive?.contentOrNull == "Enabled" } ?: views.firstOrNull()
            val url = view?.asObject()?.get("Url")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val lat = obj["Latitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lng = obj["Longitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val id = obj["Id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            CctvCamera(
                id = "on-$id", lat = lat, lng = lng,
                name = obj["Location"]?.jsonPrimitive?.contentOrNull ?: obj["Roadway"]?.jsonPrimitive?.contentOrNull ?: "Ontario Camera",
                city = "Ontario", country = "Canada",
                feedUrl = url, source = "511 Ontario",
            )
        }
    }

    private suspend fun montreal(): List<CctvCamera> {
        val data = fetchJson(
            "https://ville.montreal.qc.ca/circulation/sites/ville.montreal.qc.ca.circulation/files/cameras.json",
        ) ?: return emptyList()
        return data.asArray().mapIndexedNotNull { index, cam ->
            val lat = cam.num("latitude") ?: cam.num("lat") ?: return@mapIndexedNotNull null
            val lng = cam.num("longitude") ?: cam.num("lng") ?: return@mapIndexedNotNull null
            val feedUrl = cam.str("url") ?: cam.str("imageUrl") ?: return@mapIndexedNotNull null
            CctvCamera(
                id = "mtl-muni-$index", lat = lat, lng = lng,
                name = cam.str("description") ?: cam.str("name") ?: "Montréal Camera",
                city = "Montréal", country = "Canada",
                feedUrl = feedUrl, source = "Ville MTL",
            )
        }
    }

    private fun torontoCurated(): List<CctvCamera> = listOf(
        CctvCamera(id = "tor-1", lat = 43.6532, lng = -79.3832, name = "Yonge / Dundas Square", city = "Toronto", country = "Canada", feedUrl = "https://511on.ca/api/v2/get/cameras", source = "511 Ontario"),
        CctvCamera(id = "tor-2", lat = 43.6426, lng = -79.3871, name = "CN Tower / Lakeshore", city = "Toronto", country = "Canada", feedUrl = "https://511on.ca/api/v2/get/cameras", source = "511 Ontario"),
        CctvCamera(id = "tor-3", lat = 43.6711, lng = -79.3868, name = "Bloor / Yonge", city = "Toronto", country = "Canada", feedUrl = "https://511on.ca/api/v2/get/cameras", source = "511 Ontario"),
    )

    private suspend fun alberta(): List<CctvCamera> {
        val data = fetchJson("https://511.alberta.ca/api/v2/get/cameras") ?: return emptyList()
        return data.asArray().mapIndexedNotNull { index, cam ->
            val obj = cam.asObject() ?: return@mapIndexedNotNull null
            val lat = obj["Latitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapIndexedNotNull null
            val lng = obj["Longitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapIndexedNotNull null
            val url = (obj["Views"] as? JsonArray)?.firstOrNull()?.asObject()?.get("Url")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            val id = obj["Id"]?.jsonPrimitive?.contentOrNull ?: index.toString()
            CctvCamera(
                id = "ab-$id", lat = lat, lng = lng,
                name = obj["Location"]?.jsonPrimitive?.contentOrNull ?: "Alberta Camera",
                city = "Alberta", country = "Canada",
                feedUrl = url, source = "Alberta 511",
            )
        }
    }

    private suspend fun torontoOpenData(): List<CctvCamera> {
        val data = fetchJson(
            "https://ckan0.cf.opendata.inter.prod-toronto.ca/dataset/a3309088-5fd4-4d34-8297-77c8301840ac/" +
                "resource/4a568300-c7f8-496d-b150-dff6f5dc6d4f/download/traffic-camera-list-4326.geojson",
        )?.asObject() ?: return emptyList()
        return (data["features"] as? JsonArray).orEmpty().mapNotNull { feature ->
            val obj = feature.asObject() ?: return@mapNotNull null
            var coords = obj["geometry"]?.jsonObject?.get("coordinates") as? JsonArray ?: return@mapNotNull null
            // Some rows nest an extra array level ([[lng, lat]] instead of [lng, lat]).
            (coords.getOrNull(0) as? JsonArray)?.let { coords = it }
            val lng = coords.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lat = coords.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val props = obj["properties"]?.jsonObject ?: return@mapNotNull null
            val recId = props["REC_ID"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val imageUrl = props["IMAGEURL"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val mainRoad = props["MAINROAD"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val crossRoad = props["CROSSROAD"]?.jsonPrimitive?.contentOrNull.orEmpty()
            CctvCamera(
                id = "tor-open-$recId", lat = lat, lng = lng,
                name = "$mainRoad / $crossRoad",
                city = "Toronto", country = "Canada",
                feedUrl = imageUrl, source = "City of Toronto",
            )
        }
    }

    private suspend fun driveBc(): List<CctvCamera> {
        val data = fetchJson("https://drivebc.ca/api/webcams") ?: return emptyList()
        return data.asArray().mapNotNull { cam ->
            val obj = cam.asObject() ?: return@mapNotNull null
            val coords = obj["location"]?.jsonObject?.get("coordinates") as? JsonArray ?: return@mapNotNull null
            val lng = coords.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lat = coords.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val imageDisplay = obj["links"]?.jsonObject?.get("imageDisplay")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            CctvCamera(
                id = "bc-cam-$id", lat = lat, lng = lng,
                name = obj["name"]?.jsonPrimitive?.contentOrNull ?: obj["caption"]?.jsonPrimitive?.contentOrNull ?: "BC Highway Camera",
                city = "British Columbia", country = "Canada",
                feedUrl = "https://drivebc.ca$imageDisplay", source = "DriveBC",
            )
        }
    }

    suspend fun fetch(): List<CctvCamera> = coroutineScope {
        val ottawaD = async { ottawa() }
        val quebecD = async { quebec() }
        val ontarioD = async { ontario() }
        val montrealD = async { montreal() }
        val albertaD = async { alberta() }
        val torontoOpenD = async { torontoOpenData() }
        val driveBcD = async { driveBc() }
        ottawaD.await() + quebecD.await() + ontarioD.await() + montrealD.await() +
            torontoCurated() + albertaD.await() + torontoOpenD.await() + driveBcD.await()
    }
}
