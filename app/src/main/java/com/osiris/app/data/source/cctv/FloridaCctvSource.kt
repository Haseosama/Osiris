package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/** Florida CCTV cameras (FDOT / fl511.com, ~4,950 statewide) — mirrors
 * `osiris-backend/src/app/api/cctv/florida.ts`. Same IBI 511 platform as Georgia, North
 * Carolina and Arizona; see [Ibi511]. Keyless. */
object FloridaCctvSource {
    private val CONFIG = Ibi511.Source(
        base = "https://fl511.com",
        idPrefix = "fdot",
        source = "FDOT",
        state = "Florida",
        bounds = Ibi511.Bounds(minLat = 24.4, maxLat = 31.1, minLng = -87.7, maxLng = -79.9),
    )

    suspend fun fetch(): List<CctvCamera> = runCatching { Ibi511.loadCameras(CONFIG) }.getOrDefault(emptyList())
}
