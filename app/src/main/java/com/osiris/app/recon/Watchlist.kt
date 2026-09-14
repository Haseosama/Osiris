package com.osiris.app.recon

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One saved RECON query the user wants re-checked periodically in the background — a domain to
 * watch for WHOIS changes, an email for new breaches, a CVE ID for updates, and so on for any of
 * the eleven typed tools. [lastSnapshotHash] is [ReconResult.snapshotHash]'s output from the last
 * check (null until the first background check has actually run, which only establishes a
 * baseline and never notifies — see [WatchlistWorker], the same "never fire on the first poll"
 * convention [com.osiris.app.map.MapViewModel]'s earthquake/conflict alerts already use).
 */
@Serializable
data class WatchlistEntry(
    val id: String,
    val tool: ReconTool,
    val value: String,
    val secondaryValue: String? = null,
    val label: String,
    val lastSnapshotHash: String? = null,
    val lastCheckedMs: Long = 0L,
    val createdMs: Long = System.currentTimeMillis(),
) {
    companion object {
        /** Same tool+value+secondary always produces the same id — lets the UI check "is the
         * current query already watched" without scanning for a structural match. */
        fun idFor(tool: ReconTool, value: String, secondaryValue: String?): String =
            "${tool.name}:$value:${secondaryValue.orEmpty()}"
    }
}

private val Context.watchlistDataStore by preferencesDataStore(name = "osiris_recon_watchlist")

private val watchlistJson = Json { ignoreUnknownKeys = true }

/** DataStore-backed, unlike every other preferences class in this package — those each hold one
 * or two primitives, this holds a growing list of structured entries, so it's stored as one JSON
 * blob under a single key rather than one DataStore key per field. */
class WatchlistPreferences(private val context: Context) {

    private val key = stringPreferencesKey("entries")

    val entriesFlow: Flow<List<WatchlistEntry>> = context.watchlistDataStore.data.map { prefs ->
        prefs[key]?.let { raw -> runCatching { watchlistJson.decodeFromString<List<WatchlistEntry>>(raw) }.getOrNull() }
            ?: emptyList()
    }

    suspend fun load(): List<WatchlistEntry> = entriesFlow.first()

    suspend fun save(entries: List<WatchlistEntry>) {
        context.watchlistDataStore.edit { it[key] = watchlistJson.encodeToString(entries) }
    }

    suspend fun add(entry: WatchlistEntry) {
        val current = load()
        if (current.any { it.id == entry.id }) return
        save(current + entry)
    }

    suspend fun remove(id: String) {
        save(load().filterNot { it.id == id })
    }
}
