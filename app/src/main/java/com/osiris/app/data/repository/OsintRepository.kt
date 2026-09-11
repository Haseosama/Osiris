package com.osiris.app.data.repository

import com.osiris.app.data.model.OsintPost
import com.osiris.app.data.remote.NetworkModule

class OsintRepository {
    suspend fun fetch(baseUrl: String): List<OsintPost> =
        NetworkModule.apiFor(baseUrl).osintNews().news.filter { it.coords != null }
}
