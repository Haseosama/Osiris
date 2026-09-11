package com.osiris.app.data.repository

import com.osiris.app.data.model.LiveNewsFeed
import com.osiris.app.data.remote.NetworkModule

class LiveNewsRepository {
    suspend fun fetch(baseUrl: String): List<LiveNewsFeed> =
        NetworkModule.apiFor(baseUrl).liveNews().feeds
}
