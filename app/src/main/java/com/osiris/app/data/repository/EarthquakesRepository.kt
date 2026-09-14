package com.osiris.app.data.repository

import com.osiris.app.data.model.Earthquake
import com.osiris.app.data.source.UsgsEarthquakeSource

class EarthquakesRepository {
    /** `baseUrl` is unused — fetched directly from USGS, no backend involved (see
     * [UsgsEarthquakeSource]); kept only so call sites don't need to change. */
    suspend fun fetch(baseUrl: String): List<Earthquake> = UsgsEarthquakeSource.fetch()
}
