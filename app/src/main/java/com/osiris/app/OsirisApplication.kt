package com.osiris.app

import android.app.Application
import com.osiris.app.map.AlertNotifier
import org.maplibre.android.MapLibre

class OsirisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        AlertNotifier.createChannel(this)
    }
}
