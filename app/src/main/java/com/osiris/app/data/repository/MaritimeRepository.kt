package com.osiris.app.data.repository

import com.osiris.app.data.model.MaritimeResponse
import com.osiris.app.data.source.AisStreamSource

class MaritimeRepository {
    /** `baseUrl` is unused — connects directly to aisstream.io with the embedded
     * `BuildConfig.AIS_API_KEY`, no backend involved (see [AisStreamSource]); kept only so call
     * sites don't need to change. */
    suspend fun fetch(baseUrl: String): MaritimeResponse = AisStreamSource.fetch()
}
