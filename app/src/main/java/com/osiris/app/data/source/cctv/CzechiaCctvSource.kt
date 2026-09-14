package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/** Czechia CCTV/webcams — mirrors `osiris-backend/src/app/api/cctv/czechia.ts`, a static list
 * of YouTube embeds. `stream_type: 'iframe'` has no player equivalent here (see
 * [FranceCctvSource]'s doc comment), so these become external-link-only entries. */
object CzechiaCctvSource {
    suspend fun fetch(): List<CctvCamera> = listOf(
        CctvCamera(
            id = "cz-prague-1", lat = 50.0878, lng = 14.4205,
            name = "Prague - Old Town Square", city = "Prague", country = "Czechia",
            source = "YouTube Live", externalUrl = "https://www.youtube.com/watch?v=IFnbDmgP69Q",
        ),
        CctvCamera(
            id = "cz-prague-2", lat = 50.0865, lng = 14.4114,
            name = "Prague - Charles Bridge", city = "Prague", country = "Czechia",
            source = "YouTube Live", externalUrl = "https://www.youtube.com/watch?v=tmlE1ct0cYk",
        ),
        CctvCamera(
            id = "cz-prague-3", lat = 50.0900, lng = 14.4000,
            name = "Prague - City View", city = "Prague", country = "Czechia",
            source = "YouTube Live", externalUrl = "https://www.youtube.com/watch?v=sspBOJIrNzU",
        ),
    )
}
