package com.osiris.app.data.repository

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.source.cctv.NativeCctvSource

/** Every CCTV region `route.ts` served now has a native equivalent (see the "no backend"
 * migration plan, Phase 5) — [NativeCctvSource] is the whole layer. `baseUrl` is unused, kept
 * only so [com.osiris.app.map.MapViewModel]'s call site doesn't need to change. */
class CctvRepository {
    suspend fun fetch(baseUrl: String): List<CctvCamera> = NativeCctvSource.fetch()
}
