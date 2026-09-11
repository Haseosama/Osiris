package com.osiris.app.data.repository

import com.osiris.app.data.model.FlightMarker
import com.osiris.app.data.model.toMarkers
import com.osiris.app.data.remote.NetworkModule

class FlightsRepository {
    suspend fun fetch(baseUrl: String): List<FlightMarker> =
        NetworkModule.apiFor(baseUrl).flights().toMarkers()
}
