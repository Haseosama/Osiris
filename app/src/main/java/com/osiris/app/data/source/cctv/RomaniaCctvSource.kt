package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/** Romania CCTV — mirrors `osiris-backend/src/app/api/cctv/romania.ts`. One static camera. */
object RomaniaCctvSource {
    suspend fun fetch(): List<CctvCamera> = listOf(
        CctvCamera(
            id = "ro-bucharest", lat = 44.426, lng = 26.102, name = "Bucharest Panorama",
            city = "Bucharest", country = "Romania", source = "home-solutions.bg",
            feedUrl = "https://home-solutions.bg/cams/bukor.jpg",
        ),
    )
}
