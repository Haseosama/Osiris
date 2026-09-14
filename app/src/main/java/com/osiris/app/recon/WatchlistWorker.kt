package com.osiris.app.recon

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.osiris.app.map.AlertNotifier

/**
 * Periodic background re-check of every [WatchlistEntry] — re-runs its saved query, compares the
 * result's [ReconResult.snapshotHash] against what was stored last time, and fires a notification
 * on a change (see [AlertNotifier.notifyWatchlistChange]). Scheduled from OsirisApplication, not
 * this class — a Worker only ever describes *what* one run does, not *when* runs happen.
 *
 * A query that fails outright (network down, upstream error) just leaves that entry's hash
 * untouched rather than treating "couldn't check" as "nothing changed" or clearing the baseline —
 * either would be wrong, and the next scheduled run tries again regardless.
 */
class WatchlistWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = WatchlistPreferences(applicationContext)
        val repository = ReconRepository()
        val entries = prefs.load()
        if (entries.isEmpty()) return Result.success()

        val now = System.currentTimeMillis()
        val updated = entries.map { entry ->
            val result = repository.query(entry.tool, entry.value, entry.secondaryValue).getOrNull()
                ?: return@map entry
            val hash = result.snapshotHash()
            // null means this is the entry's first-ever check: that's establishing a baseline,
            // not a change to notify about — same convention as the earthquake/conflict alerts.
            if (entry.lastSnapshotHash != null && entry.lastSnapshotHash != hash) {
                AlertNotifier.notifyWatchlistChange(applicationContext, entry)
            }
            entry.copy(lastSnapshotHash = hash, lastCheckedMs = now)
        }
        prefs.save(updated)
        return Result.success()
    }
}
