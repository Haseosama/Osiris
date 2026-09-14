package com.osiris.app.data.repository

import com.osiris.app.data.model.CctvCamera
import com.osiris.app.data.remote.NetworkModule
import com.osiris.app.data.source.cctv.NativeCctvSource
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** CCTV is being migrated off the backend one batch of regions at a time (see the "no backend"
 * migration plan, Phase 5) — [NativeCctvSource] covers what's been ported so far, the backend
 * still covers the rest. Merged so the map has full coverage with a backend configured, and a
 * real (smaller) subset without one. */
class CctvRepository {
    suspend fun fetch(baseUrl: String): List<CctvCamera> = coroutineScope {
        val native = async { NativeCctvSource.fetch() }
        val backend: Deferred<List<CctvCamera>>? = if (baseUrl.isNotBlank()) {
            async { runCatching { NetworkModule.apiFor(baseUrl).cctv().cameras }.getOrDefault(emptyList()) }
        } else {
            null
        }

        val nativeCameras = native.await()
        val nativeIds = nativeCameras.mapTo(mutableSetOf()) { it.id }
        nativeCameras + backend?.await().orEmpty().filter { it.id !in nativeIds }
    }
}
