package com.osiris.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "osiris_settings")

/** Stores the URL of the user's self-hosted Osiris backend (Settings > Backend Osiris). */
class BackendPreferences(private val context: Context) {

    private val backendUrlKey = stringPreferencesKey("backend_url")

    val backendUrlFlow: Flow<String> =
        context.dataStore.data.map { it[backendUrlKey] ?: "" }

    suspend fun setBackendUrl(url: String) {
        context.dataStore.edit { it[backendUrlKey] = url.trim() }
    }
}
