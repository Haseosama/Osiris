package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/** Arizona CCTV cameras (ADOT / az511.gov, ~640 statewide) — mirrors
 * `osiris-backend/src/app/api/cctv/arizona.ts`. Same IBI 511 platform as Florida, Georgia and
 * North Carolina; see [Ibi511]. ADOT publishes no video for these, only refreshing stills.
 * Keyless. */
object ArizonaCctvSource {
    private val CONFIG = Ibi511.Source(
        base = "https://az511.gov",
        idPrefix = "adot",
        source = "ADOT",
        state = "Arizona",
        bounds = Ibi511.Bounds(minLat = 31.3, maxLat = 37.1, minLng = -115.0, maxLng = -109.0),
    )

    suspend fun fetch(): List<CctvCamera> = runCatching { Ibi511.loadCameras(CONFIG) }.getOrDefault(emptyList())
}
