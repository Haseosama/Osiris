package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/** Indochina, Indonesia and the Philippines via [OpenCctv] (~7,700 candidates, sampled to 800) —
 * mirrors `osiris-backend/src/app/api/cctv/opencctv.ts`'s `fetchSeAsiaCameras`. */
object SeAsiaCctvSource {
    private val BOUNDS = OpenCctv.Bounds(minLat = -11.0, maxLat = 24.0, minLng = 92.0, maxLng = 130.0)
    suspend fun fetch(): List<CctvCamera> = OpenCctv.loadRegion(BOUNDS, cap = 800)
}
