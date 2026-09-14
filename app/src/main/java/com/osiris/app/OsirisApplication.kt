package com.osiris.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.osiris.app.map.AlertNotifier
import com.osiris.app.recon.WatchlistWorker
import org.maplibre.android.MapLibre
import java.util.concurrent.TimeUnit

private const val WATCHLIST_WORK_NAME = "recon_watchlist_check"

class OsirisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        AlertNotifier.createChannel(this)
        scheduleWatchlistChecks()
    }

    // 6h: frequent enough that a changed CVE/WHOIS/breach doesn't sit unnoticed for a full day,
    // infrequent enough not to burn battery/data re-running potentially a dozen network lookups
    // for no reason most checks — KEEP rather than REPLACE so re-installing/updating the app (a
    // fresh Application.onCreate every time) doesn't reset an already-running periodic schedule
    // back to a fresh 6h window.
    private fun scheduleWatchlistChecks() {
        val request = PeriodicWorkRequestBuilder<WatchlistWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(WATCHLIST_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
