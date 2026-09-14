package com.osiris.app.data.repository

import com.osiris.app.data.model.Satellite
import com.osiris.app.data.model.SatelliteNextPass
import com.osiris.app.data.source.CelesTrakSatelliteSource

class SatellitesRepository {

    /** The full catalogue (~18-19k, mostly Starlink + tracked debris) — no cap here any more.
     * [MapViewModel] filters by category client-side based on what the user picked in Réglages,
     * which is what actually keeps the animated/rendered set to something a phone can handle,
     * rather than a fixed backend-side number the user can't adjust. `baseUrl` is unused —
     * fetched and propagated (SGP4/SDP4 via predict4java) directly on-device, no backend
     * involved (see [CelesTrakSatelliteSource]); kept only so call sites don't need to change. */
    suspend fun fetch(baseUrl: String): List<Satellite> = CelesTrakSatelliteSource.fetch().first

    /** One satellite's orbital period, fetched on tap rather than bundled into every poll. Pure
     * math on the cached TLE's mean motion (see [CelesTrakSatelliteSource.fetchOrbitPeriod]) —
     * no propagation, no network call, so unlike the backend's version this never needs
     * [epochMs] to anchor anything; kept only so call sites don't need to change. */
    suspend fun fetchOrbitPeriod(baseUrl: String, noradId: String, epochMs: Long): Double? =
        CelesTrakSatelliteSource.fetchOrbitPeriod(noradId)

    /** True live SGP4 position for the given NORAD ids, off the already-cached TLEs — see
     * [CelesTrakSatelliteSource.propagateLive]. */
    suspend fun propagateLive(noradIds: Set<String>): List<Satellite> =
        CelesTrakSatelliteSource.propagateLive(noradIds)

    /** Next time this satellite rises above the given observer's horizon — see
     * [CelesTrakSatelliteSource.nextPass]. Never existed backend-side at all. */
    suspend fun fetchNextPass(noradId: String, observerLat: Double, observerLng: Double): SatelliteNextPass? =
        CelesTrakSatelliteSource.nextPass(noradId, observerLat, observerLng)
}
