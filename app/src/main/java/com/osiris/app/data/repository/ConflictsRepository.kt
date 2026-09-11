package com.osiris.app.data.repository

import com.osiris.app.data.model.ConflictZone
import com.osiris.app.data.remote.NetworkModule

class ConflictsRepository {
    suspend fun fetch(baseUrl: String): List<ConflictZone> =
        NetworkModule.apiFor(baseUrl).conflicts().zones
}
