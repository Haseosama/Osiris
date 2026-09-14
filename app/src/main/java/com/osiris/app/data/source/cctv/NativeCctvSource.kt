package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Aggregates every CCTV region ported off the backend so far — see the "no backend" migration
 * plan, Phase 5 (CCTV is tackled in batches, not all ~45 `osiris-backend/src/app/api/cctv/` .ts
 * sources at once).
 * Batch 1: [TflCctvSource] (UK, ~900), [WsdotCctvSource] (Washington, ~500),
 * [CaltransCctvSource] (California), [FranceCctvSource] (static list + APRR/AREA's 123 highway
 * webcams) — the sources the plan itself called out as highest-value first.
 * Batch 2: the IBI 511 US states — [FloridaCctvSource] (~4,950), [GeorgiaCctvSource] (~4,040),
 * [NorthCarolinaCctvSource] (~1,140), [ArizonaCctvSource] (~640) via the shared [Ibi511] loader,
 * plus [LouisianaCctvSource] (~336) on the same platform with its own record shape.
 * Batch 3: [AustraliaCctvSource] and [FinlandCctvSource] (~470, keyless APIs), plus six small
 * static-list countries — [PolandCctvSource] (~70 real HLS streams), [BulgariaCctvSource],
 * [SerbiaCctvSource], [MacedoniaCctvSource], [GermanyCctvSource], [SlovakiaCctvSource],
 * [CzechiaCctvSource].
 *
 * [com.osiris.app.data.repository.CctvRepository] merges this with whatever the backend still
 * serves for the regions not yet listed here, so the map keeps full coverage when a backend is
 * configured and a real (smaller) subset when it isn't.
 */
object NativeCctvSource {
    private val SOURCES: List<suspend () -> List<CctvCamera>> = listOf(
        TflCctvSource::fetch,
        WsdotCctvSource::fetch,
        CaltransCctvSource::fetch,
        FranceCctvSource::fetch,
        FloridaCctvSource::fetch,
        GeorgiaCctvSource::fetch,
        NorthCarolinaCctvSource::fetch,
        ArizonaCctvSource::fetch,
        LouisianaCctvSource::fetch,
        AustraliaCctvSource::fetch,
        FinlandCctvSource::fetch,
        PolandCctvSource::fetch,
        BulgariaCctvSource::fetch,
        SerbiaCctvSource::fetch,
        MacedoniaCctvSource::fetch,
        GermanyCctvSource::fetch,
        SlovakiaCctvSource::fetch,
        CzechiaCctvSource::fetch,
    )

    suspend fun fetch(): List<CctvCamera> = coroutineScope {
        SOURCES.map { source -> async { source() } }.map { it.await() }.flatten()
    }
}
