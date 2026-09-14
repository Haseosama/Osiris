package com.osiris.app.data.repository

import com.osiris.app.data.model.TrafficIncident
import com.osiris.app.data.source.TomTomTrafficSource

class TrafficRepository {
    /** `baseUrl` is unused — fetched directly from TomTom with the embedded
     * `BuildConfig.TOMTOM_API_KEY`, no backend involved (see [TomTomTrafficSource]); kept only
     * so call sites don't need to change. */
    suspend fun fetch(baseUrl: String): List<TrafficIncident> = TomTomTrafficSource.fetch()
}
