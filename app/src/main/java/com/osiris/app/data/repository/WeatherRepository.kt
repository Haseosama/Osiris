package com.osiris.app.data.repository

import com.osiris.app.data.model.WeatherEvent
import com.osiris.app.data.source.WeatherSource

class WeatherRepository {
    /** `baseUrl` is unused — fetched directly from NASA EONET/NOAA NWS/GDACS, no backend
     * involved (see [WeatherSource]); kept only so call sites don't need to change. */
    suspend fun fetch(baseUrl: String): List<WeatherEvent> = WeatherSource.fetch()
}
