package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/** Slovakia CCTV/webcams — mirrors `osiris-backend/src/app/api/cctv/slovakia.ts`, a static list
 * of YouTube embeds. `stream_type: 'iframe'` has no player equivalent here (see
 * [FranceCctvSource]'s doc comment), so these become external-link-only entries. */
object SlovakiaCctvSource {
    suspend fun fetch(): List<CctvCamera> = listOf(
        CctvCamera(
            id = "sk-bratislava-1", lat = 48.1486, lng = 17.1077,
            name = "Bratislava - Old Town", city = "Bratislava", country = "Slovakia",
            source = "YouTube Live", externalUrl = "https://www.youtube.com/watch?v=kYDIwCLGKL0",
        ),
        CctvCamera(
            id = "sk-bratislava-3", lat = 48.1450, lng = 17.1000,
            name = "Bratislava - Danube River", city = "Bratislava", country = "Slovakia",
            source = "YouTube Live", externalUrl = "https://www.youtube.com/watch?v=xFdvZ4eGzPg",
        ),
    )
}
