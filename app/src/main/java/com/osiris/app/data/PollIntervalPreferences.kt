package com.osiris.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.osiris.app.map.MapLayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pollIntervalDataStore by preferencesDataStore(name = "osiris_poll_intervals")

/** Per-layer poll interval override (Réglages > Cadence de polling) — each [MapLayer] ships a
 * sensible default ([MapLayer.pollIntervalMs]) that this falls back to until the user picks
 * something else. */
class PollIntervalPreferences(private val context: Context) {

    private fun key(layer: MapLayer) = longPreferencesKey("poll_interval_${layer.name}")

    fun intervalFlow(layer: MapLayer): Flow<Long> =
        context.pollIntervalDataStore.data.map { it[key(layer)] ?: layer.pollIntervalMs }

    suspend fun setInterval(layer: MapLayer, intervalMs: Long) {
        context.pollIntervalDataStore.edit { it[key(layer)] = intervalMs }
    }
}
