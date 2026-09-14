package com.osiris.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.satelliteCategoryDataStore by preferencesDataStore(name = "osiris_satellite_categories")

/**
 * Which satellite categories (comms, navigation, earth_obs, military, science, other) the user
 * has chosen to hide — the full ~18-19k catalogue is shown by default (nothing disabled), and
 * Réglages lets them turn off the categories they don't care about instead of a fixed backend
 * cap. Storing the *disabled* set rather than the enabled one means "everything on" is the
 * natural empty-set default, with no need to enumerate every category here.
 */
class SatelliteCategoryPreferences(private val context: Context) {

    private val key = stringSetPreferencesKey("disabled_categories")

    val disabledCategoriesFlow: Flow<Set<String>> =
        context.satelliteCategoryDataStore.data.map { it[key] ?: emptySet() }

    suspend fun setDisabled(categories: Set<String>) {
        context.satelliteCategoryDataStore.edit { it[key] = categories }
    }
}
