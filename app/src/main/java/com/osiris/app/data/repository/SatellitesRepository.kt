package com.osiris.app.data.repository

import com.osiris.app.data.model.Satellite
import com.osiris.app.data.remote.NetworkModule

class SatellitesRepository {
    suspend fun fetch(baseUrl: String): List<Satellite> =
        NetworkModule.apiFor(baseUrl).satellites().satellites
}
