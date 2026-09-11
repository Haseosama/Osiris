package com.osiris.app.data.repository

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.remote.NetworkModule

class CctvRepository {
    suspend fun fetch(baseUrl: String): List<CctvCamera> =
        NetworkModule.apiFor(baseUrl).cctv().cameras
}
