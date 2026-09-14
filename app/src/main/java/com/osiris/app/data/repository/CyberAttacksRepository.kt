package com.osiris.app.data.repository

import com.osiris.app.data.model.CyberAttack
import com.osiris.app.data.source.FeodoTrackerSource

class CyberAttacksRepository {
    /** `baseUrl` is unused — fetched directly from abuse.ch Feodo Tracker, no backend involved
     * (see [FeodoTrackerSource]); kept only so call sites don't need to change. */
    suspend fun fetch(baseUrl: String): List<CyberAttack> = FeodoTrackerSource.fetch()
}
