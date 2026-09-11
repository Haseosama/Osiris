package com.osiris.app.data.repository

import com.osiris.app.data.model.WeatherEvent
import com.osiris.app.data.remote.NetworkModule

class WeatherRepository {
    suspend fun fetch(baseUrl: String): List<WeatherEvent> =
        NetworkModule.apiFor(baseUrl).weather().events
}
