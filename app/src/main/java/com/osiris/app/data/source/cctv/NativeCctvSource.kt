package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Aggregates every CCTV region ported off the backend so far — see the "no backend" migration
 * plan, Phase 5 (CCTV is tackled in batches, not all ~45 `osiris-backend/src/app/api/cctv/*.ts`
 * sources at once). Batch 1: [TflCctvSource] (UK, ~900), [WsdotCctvSource] (Washington, ~500),
 * [CaltransCctvSource] (California), [FranceCctvSource] (static list + APRR/AREA's 123 highway
 * webcams) — the sources the plan itself called out as highest-value first.
 *
 * [com.osiris.app.data.repository.CctvRepository] merges this with whatever the backend still
 * serves for the ~40 regions not yet listed here, so the map keeps full coverage when a backend
 * is configured and a real (smaller) subset when it isn't.
 */
object NativeCctvSource {
    suspend fun fetch(): List<CctvCamera> = coroutineScope {
        val tfl = async { TflCctvSource.fetch() }
        val wsdot = async { WsdotCctvSource.fetch() }
        val caltrans = async { CaltransCctvSource.fetch() }
        val france = async { FranceCctvSource.fetch() }
        tfl.await() + wsdot.await() + caltrans.await() + france.await()
    }
}
