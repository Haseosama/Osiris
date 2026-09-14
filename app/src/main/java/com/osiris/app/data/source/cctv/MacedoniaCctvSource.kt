package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/** North Macedonia CCTV/webcams — mirrors `osiris-backend/src/app/api/cctv/macedonia.ts`.
 * Static list. */
object MacedoniaCctvSource {
    suspend fun fetch(): List<CctvCamera> = listOf(
        CctvCamera(
            id = "mk-deve-bair", lat = 42.149, lng = 22.537,
            name = "Deve Bair – Gyueshevo Border", city = "Deve Bair", country = "North Macedonia",
            streamUrl = "https://streaming1.neotel.net.mk/stream/deve_bair.m3u8",
            source = "Neotel / GKPP",
        ),
        CctvCamera(
            id = "mk-tabanovce", lat = 42.232, lng = 21.718,
            name = "Tabanovce – Preševo Border", city = "Tabanovce", country = "North Macedonia",
            streamUrl = "https://streaming1.neotel.net.mk/stream/tabanovce.m3u8",
            source = "Neotel / GKPP",
        ),
    )
}
