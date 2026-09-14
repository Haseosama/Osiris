package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/** The Gulf, Iran, Central Asia and the subcontinent via [OpenCctv] (~950 candidates — the cap
 * is a guard here, not a real quota) — mirrors `osiris-backend/src/app/api/cctv/opencctv.ts`'s
 * `fetchWestAsiaCameras`. */
object WestAsiaCctvSource {
    private val BOUNDS = OpenCctv.Bounds(minLat = 5.0, maxLat = 56.0, minLng = 25.0, maxLng = 92.0)
    suspend fun fetch(): List<CctvCamera> = OpenCctv.loadRegion(BOUNDS, cap = 600)
}
