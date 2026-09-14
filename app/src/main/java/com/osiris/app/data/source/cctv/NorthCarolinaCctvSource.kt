package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/** North Carolina CCTV cameras (NCDOT DriveNC / drivenc.gov, ~1,140 statewide) — mirrors
 * `osiris-backend/src/app/api/cctv/northcarolina.ts`. Same IBI 511 platform as Florida, Georgia
 * and Arizona; see [Ibi511]. Keyless. */
object NorthCarolinaCctvSource {
    private val CONFIG = Ibi511.Source(
        base = "https://drivenc.gov",
        idPrefix = "ncdot",
        source = "NCDOT",
        state = "North Carolina",
        bounds = Ibi511.Bounds(minLat = 33.8, maxLat = 36.6, minLng = -84.4, maxLng = -75.4),
    )

    suspend fun fetch(): List<CctvCamera> = runCatching { Ibi511.loadCameras(CONFIG) }.getOrDefault(emptyList())
}
