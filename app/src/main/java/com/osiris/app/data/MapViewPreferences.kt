package com.osiris.app.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.mapViewDataStore by preferencesDataStore(name = "osiris_map_view")

data class SavedMapView(
    val lat: Double,
    val lng: Double,
    val zoom: Double,
    val bearing: Double,
    val tilt: Double,
    val styleMode: String,
)

/** Remembers the camera position/zoom/bearing/tilt and street-vs-satellite style the user last
 * had on screen, so relaunching the app picks up where they left off instead of always resetting
 * to a world view (or, once a saved view exists, always re-centering on the user's current GPS
 * position — that only happens once, on the very first launch, before there's anything saved). */
class MapViewPreferences(private val context: Context) {

    private object Keys {
        val LAT = doublePreferencesKey("last_lat")
        val LNG = doublePreferencesKey("last_lng")
        val ZOOM = doublePreferencesKey("last_zoom")
        val BEARING = doublePreferencesKey("last_bearing")
        val TILT = doublePreferencesKey("last_tilt")
        val STYLE = stringPreferencesKey("last_style")
    }

    suspend fun load(): SavedMapView? {
        val prefs = context.mapViewDataStore.data.first()
        val lat = prefs[Keys.LAT] ?: return null
        val lng = prefs[Keys.LNG] ?: return null
        val zoom = prefs[Keys.ZOOM] ?: return null
        return SavedMapView(
            lat = lat,
            lng = lng,
            zoom = zoom,
            bearing = prefs[Keys.BEARING] ?: 0.0,
            tilt = prefs[Keys.TILT] ?: 0.0,
            styleMode = prefs[Keys.STYLE] ?: "SATELLITE",
        )
    }

    suspend fun save(view: SavedMapView) {
        context.mapViewDataStore.edit { prefs ->
            prefs[Keys.LAT] = view.lat
            prefs[Keys.LNG] = view.lng
            prefs[Keys.ZOOM] = view.zoom
            prefs[Keys.BEARING] = view.bearing
            prefs[Keys.TILT] = view.tilt
            prefs[Keys.STYLE] = view.styleMode
        }
    }
}
