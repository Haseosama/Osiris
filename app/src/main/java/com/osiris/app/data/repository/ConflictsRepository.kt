package com.osiris.app.data.repository

import com.osiris.app.data.model.ConflictZone
import com.osiris.app.data.source.GdeltConflictsSource

class ConflictsRepository {
    /** `baseUrl` is unused — the known-zone list + live RSS enrichment happen directly on the
     * phone, no backend involved (see [GdeltConflictsSource]); kept only so call sites don't
     * need to change. */
    suspend fun fetch(baseUrl: String): List<ConflictZone> = GdeltConflictsSource.fetch()
}
