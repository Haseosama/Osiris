package com.osiris.app.data.repository

import com.osiris.app.data.model.FireEvent
import com.osiris.app.data.source.NasaFirmsSource

class FiresRepository {
    /** `baseUrl` is unused — fetched directly from NASA FIRMS/EONET, no backend involved (see
     * [NasaFirmsSource]); kept only so call sites don't need to change. */
    suspend fun fetch(baseUrl: String): List<FireEvent> = NasaFirmsSource.fetch()
}
