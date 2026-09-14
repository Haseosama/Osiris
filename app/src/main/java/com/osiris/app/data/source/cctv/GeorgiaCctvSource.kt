package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/** Georgia CCTV cameras (GDOT NaviGAtor / 511ga.org, ~4,040 statewide) — mirrors
 * `osiris-backend/src/app/api/cctv/georgia.ts`. Same IBI 511 platform as Florida, North
 * Carolina and Arizona; see [Ibi511]. Keyless. */
object GeorgiaCctvSource {
    private val CONFIG = Ibi511.Source(
        base = "https://511ga.org",
        idPrefix = "gdot",
        source = "GDOT",
        state = "Georgia",
        bounds = Ibi511.Bounds(minLat = 30.3, maxLat = 35.1, minLng = -85.7, maxLng = -80.8),
    )

    suspend fun fetch(): List<CctvCamera> = runCatching { Ibi511.loadCameras(CONFIG) }.getOrDefault(emptyList())
}
