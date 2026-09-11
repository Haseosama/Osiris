package com.osiris.app

import android.app.Application
import org.maplibre.android.MapLibre

class OsirisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}
