package com.osiris.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.osiris.app.map.MapLayer
import kotlinx.coroutines.flow.first

private val Context.layerToggleDataStore by preferencesDataStore(name = "osiris_layer_toggles")

/**
 * Which map layers the user has turned on/off — [MapViewModel] used to hold this purely in
 * memory, resetting to [MapLayer.defaultEnabled] on every app restart, which read as "my layer
 * choices don't get remembered" (they didn't). Stores the full set of currently-*enabled* layer
 * names rather than a diff against the defaults, so a never-saved store (first launch) is the one
 * case that needs a fallback — every real toggle after that is a plain read/write of the whole set.
 */
class LayerTogglePreferences(private val context: Context) {

    private val key = stringSetPreferencesKey("enabled_layers")

    suspend fun load(): Map<MapLayer, Boolean> {
        val stored = context.layerToggleDataStore.data.first()[key]
        return if (stored == null) {
            MapLayer.entries.associateWith { it.defaultEnabled }
        } else {
            MapLayer.entries.associateWith { it.name in stored }
        }
    }

    suspend fun save(enabled: Map<MapLayer, Boolean>) {
        val names = enabled.filterValues { it }.keys.map { it.name }.toSet()
        context.layerToggleDataStore.edit { it[key] = names }
    }
}
