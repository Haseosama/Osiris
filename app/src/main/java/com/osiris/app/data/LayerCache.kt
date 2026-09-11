package com.osiris.app.data

import android.content.Context
import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.model.ConflictZone
import com.osiris.app.data.model.CyberAttack
import com.osiris.app.data.model.Earthquake
import com.osiris.app.data.model.FireEvent
import com.osiris.app.data.model.FlightMarker
import com.osiris.app.data.model.LiveNewsFeed
import com.osiris.app.data.model.MaritimeResponse
import com.osiris.app.data.model.OsintPost
import com.osiris.app.data.model.Satellite
import com.osiris.app.data.model.WeatherEvent
import com.osiris.app.map.MapLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.File

/**
 * Persists the last successfully fetched payload per layer to a private file, so the map
 * shows the previous session's data immediately on launch instead of starting empty while
 * the first poll is still in flight. One `save`/`load` pair per layer rather than a single
 * generic+reified API: a `public inline` function can't touch this class's private [json]/
 * [cacheDir], and making those non-private just to allow it isn't worth the trade.
 */
class LayerCache(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheDir = File(context.filesDir, "layer_cache").apply { mkdirs() }

    suspend fun saveFlights(data: List<FlightMarker>) = save(MapLayer.FLIGHTS, data)
    suspend fun loadFlights(): List<FlightMarker>? = load(MapLayer.FLIGHTS)

    suspend fun saveEarthquakes(data: List<Earthquake>) = save(MapLayer.EARTHQUAKES, data)
    suspend fun loadEarthquakes(): List<Earthquake>? = load(MapLayer.EARTHQUAKES)

    suspend fun saveFires(data: List<FireEvent>) = save(MapLayer.FIRES, data)
    suspend fun loadFires(): List<FireEvent>? = load(MapLayer.FIRES)

    suspend fun saveWeather(data: List<WeatherEvent>) = save(MapLayer.WEATHER, data)
    suspend fun loadWeather(): List<WeatherEvent>? = load(MapLayer.WEATHER)

    suspend fun saveConflicts(data: List<ConflictZone>) = save(MapLayer.CONFLICTS, data)
    suspend fun loadConflicts(): List<ConflictZone>? = load(MapLayer.CONFLICTS)

    suspend fun saveMaritime(data: MaritimeResponse) = save(MapLayer.MARITIME, data)
    suspend fun loadMaritime(): MaritimeResponse? = load(MapLayer.MARITIME)

    suspend fun saveSatellites(data: List<Satellite>) = save(MapLayer.SATELLITES, data)
    suspend fun loadSatellites(): List<Satellite>? = load(MapLayer.SATELLITES)

    suspend fun saveNews(data: List<LiveNewsFeed>) = save(MapLayer.NEWS, data)
    suspend fun loadNews(): List<LiveNewsFeed>? = load(MapLayer.NEWS)

    suspend fun saveCyberAttacks(data: List<CyberAttack>) = save(MapLayer.CYBER_ATTACKS, data)
    suspend fun loadCyberAttacks(): List<CyberAttack>? = load(MapLayer.CYBER_ATTACKS)

    suspend fun saveCctv(data: List<CctvCamera>) = save(MapLayer.CCTV, data)
    suspend fun loadCctv(): List<CctvCamera>? = load(MapLayer.CCTV)

    suspend fun saveOsint(data: List<OsintPost>) = save(MapLayer.OSINT, data)
    suspend fun loadOsint(): List<OsintPost>? = load(MapLayer.OSINT)

    private suspend inline fun <reified T> save(layer: MapLayer, data: T): Result<Unit> {
        // Resolved here, not inside withContext's lambda: that lambda is passed to a
        // non-inline function, so it never actually gets inlined into this call site —
        // and reified T only survives in code that is.
        val serializer = serializer<T>()
        return withContext(Dispatchers.IO) {
            runCatching { fileFor(layer).writeText(json.encodeToString(serializer, data)) }
        }
    }

    private suspend inline fun <reified T> load(layer: MapLayer): T? {
        val serializer = serializer<T>()
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = fileFor(layer)
                if (!file.exists()) null else json.decodeFromString(serializer, file.readText())
            }.getOrNull()
        }
    }

    private fun fileFor(layer: MapLayer): File = File(cacheDir, "${layer.name}.json")
}
