package com.osiris.app.data.repository

import com.osiris.app.data.model.MaritimeResponse
import com.osiris.app.data.remote.NetworkModule

class MaritimeRepository {
    suspend fun fetch(baseUrl: String): MaritimeResponse =
        NetworkModule.apiFor(baseUrl).maritime()
}
