package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/** China, Japan, the Koreas and Taiwan via [OpenCctv] (~24,000 candidates, sampled to 1,200) —
 * mirrors `osiris-backend/src/app/api/cctv/opencctv.ts`'s `fetchEastAsiaCameras`. */
object EastAsiaCctvSource {
    private val BOUNDS = OpenCctv.Bounds(minLat = 18.0, maxLat = 46.0, minLng = 73.5, maxLng = 146.0)
    suspend fun fetch(): List<CctvCamera> = OpenCctv.loadRegion(BOUNDS, cap = 1200)
}
