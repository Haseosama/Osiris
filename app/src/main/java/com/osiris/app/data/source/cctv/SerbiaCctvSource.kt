package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/** Serbia CCTV/webcams — mirrors `osiris-backend/src/app/api/cctv/serbia.ts`. Static list. */
object SerbiaCctvSource {
    suspend fun fetch(): List<CctvCamera> = listOf(
        CctvCamera(
            id = "rs-belgrade-live", lat = 44.817, lng = 20.456,
            name = "Belgrade Live Cam", city = "Belgrade", country = "Serbia",
            feedUrl = "https://stream.uzivobeograd.rs/live/cam_7.jpg",
            source = "Uzivo Beograd",
        ),
        CctvCamera(
            id = "rs-kalotina-gradina-1", lat = 42.997, lng = 22.882,
            name = "Kalotina – Gradina Border (lane 1)", city = "Gradina", country = "Serbia",
            streamUrl = "https://kamere.amss.org.rs/gradina1/gradina1.m3u8",
            source = "AMSS / GKPP",
        ),
    )
}
