package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/** Curated Middle East webcams — mirrors `osiris-backend/src/app/api/cctv/route.ts`'s
 * `fetchMiddleEastCameras`, all YouTube embeds. Converted to external-link-only, same as
 * elsewhere (see [FranceCctvSource]'s doc comment on `stream_type: 'iframe'`). */
object MiddleEastCctvSource {
    suspend fun fetch(): List<CctvCamera> = listOf(
        CctvCamera(
            id = "il-israel-multicam", lat = 32.0853, lng = 34.7818, name = "Israel Multi-Cam (Live)",
            city = "Tel Aviv", country = "Israel", source = "YouTube Live",
            externalUrl = "https://www.youtube.com/watch?v=gmtlJ_m2r5A",
        ),
        CctvCamera(
            id = "il-jerusalem-live", lat = 31.7767, lng = 35.2345, name = "Jerusalem Western Wall",
            city = "Jerusalem", country = "Israel", source = "YouTube Live",
            externalUrl = "https://www.youtube.com/watch?v=77akujLn4k8",
        ),
        CctvCamera(
            id = "lb-beirut-skyline", lat = 33.8938, lng = 35.5018, name = "Beirut Skyline Live",
            city = "Beirut", country = "Lebanon", source = "YouTube Live",
            externalUrl = "https://www.youtube.com/watch?v=qJf4NqPKLjI",
        ),
        CctvCamera(
            id = "lb-me-multicam", lat = 33.2721, lng = 35.2033, name = "Middle East Multi-Cam (Live)",
            city = "Regional", country = "Middle East", source = "YouTube Live",
            externalUrl = "https://www.youtube.com/watch?v=oxT5R6I0N6E",
        ),
    )
}
