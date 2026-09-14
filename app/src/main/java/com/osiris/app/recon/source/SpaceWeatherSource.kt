package com.osiris.app.recon.source

import com.osiris.app.data.remote.NetworkModule
import com.osiris.app.data.source.DirectHttp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant

/** Solar activity from NOAA's Space Weather Prediction Center, called directly from the phone —
 * mirrors `osiris-backend/src/app/api/space-weather/route.ts`, the very last piece that still
 * went through the self-hosted backend (every RECON tool and map layer had already been ported).
 * Keyless. No typed [com.osiris.app.recon.ReconResult] shape for this one — it fell back to the
 * generic indented-JSON view even when it was backend-proxied, so this just reshapes the same
 * fields the backend used to and hands back pretty-printed JSON text, same as
 * [SslCertsSource]/[com.osiris.app.recon.source.NetworkScannerSource]. */
object SpaceWeatherSource {
    private val prettyJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun fetch(): String = runCatching {
        coroutineScope {
            val kpDeferred = async { fetchArray("https://services.swpc.noaa.gov/json/planetary_k_index_1m.json") }
            val alertsDeferred = async { fetchArray("https://services.swpc.noaa.gov/json/alerts.json") }
            val flaresDeferred = async { fetchArray("https://services.swpc.noaa.gov/json/goes/primary/xray-flares-latest.json") }

            val latest = kpDeferred.await()?.lastOrNull()?.jsonObject
            val kpIndex = latest?.get("kp_index")?.jsonPrimitive?.doubleOrNull
                ?: latest?.get("Kp")?.jsonPrimitive?.doubleOrNull
                ?: 0.0
            val kpTimestamp = latest?.get("time_tag")?.jsonPrimitive?.contentOrNull.orEmpty()

            val (stormLevel, stormColor) = when {
                kpIndex >= 8 -> "Extreme (G5)" to "#FF1744"
                kpIndex >= 7 -> "Severe (G4)" to "#FF3D3D"
                kpIndex >= 6 -> "Strong (G3)" to "#FF9500"
                kpIndex >= 5 -> "Moderate (G2)" to "#FFD700"
                kpIndex >= 4 -> "Minor (G1)" to "#FFD700"
                kpIndex >= 3 -> "Unsettled" to "#D4AF37"
                else -> "Quiet" to "#00E676"
            }

            val alerts = alertsDeferred.await().orEmpty().take(10).map { el ->
                val obj = el.jsonObject
                buildJsonObject {
                    put("id", obj["product_id"]?.jsonPrimitive?.contentOrNull ?: "alert-${System.currentTimeMillis()}")
                    put("issue_datetime", obj["issue_datetime"]?.jsonPrimitive?.contentOrNull)
                    put("message", obj["message"]?.jsonPrimitive?.contentOrNull.orEmpty().take(200))
                }
            }

            val flares = flaresDeferred.await().orEmpty().take(5).mapNotNull { el ->
                val obj = el.jsonObject
                val maxClass = obj["max_class"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                buildJsonObject {
                    put("class", maxClass)
                    put("begin", obj["begin_time"]?.jsonPrimitive?.contentOrNull)
                    put("peak", obj["max_time"]?.jsonPrimitive?.contentOrNull)
                    put("end", obj["end_time"]?.jsonPrimitive?.contentOrNull)
                }
            }

            val result = buildJsonObject {
                put("kp_index", kpIndex)
                put("storm_level", stormLevel)
                put("storm_color", stormColor)
                put("kp_timestamp", kpTimestamp)
                putJsonArray("alerts") { alerts.forEach { add(it) } }
                putJsonArray("solar_flares") { flares.forEach { add(it) } }
                put("timestamp", Instant.now().toString())
            }
            prettyJson.encodeToString(JsonElement.serializer(), result)
        }
    }.getOrElse {
        prettyJson.encodeToString(
            JsonElement.serializer(),
            buildJsonObject {
                put("kp_index", 0)
                put("storm_level", "Unknown")
                put("storm_color", "#555")
                putJsonArray("alerts") {}
                putJsonArray("solar_flares") {}
                put("error", "Failed to fetch space weather data")
            },
        )
    }

    private suspend fun fetchArray(url: String): JsonArray? =
        runCatching { NetworkModule.json.parseToJsonElement(DirectHttp.getText(url)) as? JsonArray }.getOrNull()
}
