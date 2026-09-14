package com.osiris.app.data.repository

import com.osiris.app.data.model.TrafficIncident
import com.osiris.app.data.remote.NetworkModule

class TrafficRepository {
    suspend fun fetch(baseUrl: String): List<TrafficIncident> =
        NetworkModule.apiFor(baseUrl).traffic().incidents
}
