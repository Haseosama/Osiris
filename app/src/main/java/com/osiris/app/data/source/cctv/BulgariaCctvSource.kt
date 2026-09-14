package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/** Bulgaria CCTV/webcams — mirrors `osiris-backend/src/app/api/cctv/bulgaria.ts`. Static list;
 * the backend also merges in a generated border-crossing dataset
 * (`bulgaria-fwcbg.generated.ts`) that is empty in the current backend source, so there's
 * nothing to port there. */
object BulgariaCctvSource {
    suspend fun fetch(): List<CctvCamera> = listOf(
        CctvCamera(
            id = "bg-sofia-tsarigradsko-uab", lat = 42.662, lng = 23.376,
            name = "Tsarigradsko Shose (UAB)", city = "Sofia", country = "Bulgaria",
            feedUrl = "https://cdn.uab.org/images/cctv/images/cctv/cctv_103/cctv.jpg",
            source = "UAB / KAMEPA",
        ),
        CctvCamera(
            id = "bg-sofia-banishora", lat = 42.704, lng = 23.327,
            name = "Banishora / Opalchenska", city = "Sofia", country = "Bulgaria",
            feedUrl = "https://meteo.chavo.biz/Camera_streem/live_snap.jpg",
            source = "meteo.chavo.biz",
        ),
        CctvCamera(
            id = "bg-burgas-center", lat = 42.497, lng = 27.47,
            name = "Burgas Center (Smart Burgas HLS)", city = "Burgas", country = "Bulgaria",
            streamUrl = "https://pics.smartburgas.eu/m3u8/burgas_town_Center.m3u8",
            externalUrl = "https://www.weather-webcam.eu/cams/burgas-centar.html",
            source = "Smart Burgas",
        ),
    )
}
