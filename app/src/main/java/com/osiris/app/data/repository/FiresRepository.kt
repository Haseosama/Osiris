package com.osiris.app.data.repository

import com.osiris.app.data.model.FireEvent
import com.osiris.app.data.remote.NetworkModule

class FiresRepository {
    suspend fun fetch(baseUrl: String): List<FireEvent> =
        NetworkModule.apiFor(baseUrl).fires().fires
}
