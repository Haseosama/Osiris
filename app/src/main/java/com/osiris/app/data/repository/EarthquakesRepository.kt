package com.osiris.app.data.repository

import com.osiris.app.data.model.Earthquake
import com.osiris.app.data.remote.NetworkModule

class EarthquakesRepository {
    suspend fun fetch(baseUrl: String): List<Earthquake> =
        NetworkModule.apiFor(baseUrl).earthquakes().earthquakes
}
