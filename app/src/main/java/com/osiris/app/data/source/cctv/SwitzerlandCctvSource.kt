package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/** Switzerland CCTV/webcams — mirrors `osiris-backend/src/app/api/cctv/switzerland.ts`, a fully
 * static list. CHUV's heliport cam is tagged `stream_type: 'jpg'` in the backend — a refreshing
 * still image, not a video — so it becomes [CctvCamera.feedUrl] here, not [CctvCamera.streamUrl]
 * (which would hand a JPEG to the video player). */
object SwitzerlandCctvSource {
    suspend fun fetch(): List<CctvCamera> = listOf(
        CctvCamera(
            id = "chuv-heliport", lat = 46.5250, lng = 6.6420, name = "CHUV Heliport Webcam",
            city = "Lausanne", country = "Switzerland", source = "chuv.ch",
            feedUrl = "https://wc-heli.chuv.ch/axis-cgi/jpg/image.cgi?resolution=640x480",
            externalUrl = "https://wc-heli.chuv.ch/view/view.shtml",
        ),
        CctvCamera(
            id = "sky-ch-matterhorn", lat = 45.9763, lng = 7.6586, name = "Zermatt - Matterhorn",
            city = "Zermatt", country = "Switzerland", source = "SkylineWebcams",
            externalUrl = "https://www.skylinewebcams.com/en/webcam/suisse/valais/zermatt/matterhorn.html",
        ),
        CctvCamera(
            id = "sky-ch-lugano", lat = 46.0037, lng = 8.9511, name = "Lake Lugano",
            city = "Lugano", country = "Switzerland", source = "SkylineWebcams",
            externalUrl = "https://www.skylinewebcams.com/en/webcam/suisse/ticino/lugano/lake-lugano.html",
        ),
        CctvCamera(
            id = "sky-ch-st-moritz", lat = 46.4908, lng = 9.8355, name = "St. Moritz - Lake",
            city = "St. Moritz", country = "Switzerland", source = "SkylineWebcams",
            externalUrl = "https://www.skylinewebcams.com/en/webcam/suisse/grisons/st-moritz/st-moritz.html",
        ),
        CctvCamera(
            id = "sky-ch-jungfrau", lat = 46.5475, lng = 7.9826, name = "Jungfraujoch - Top of Europe",
            city = "Interlaken", country = "Switzerland", source = "SkylineWebcams",
            externalUrl = "https://www.skylinewebcams.com/en/webcam/suisse/bern/interlaken/jungfraujoch.html",
        ),
        CctvCamera(
            id = "sky-ch-geneva", lat = 46.2044, lng = 6.1432, name = "Geneva - Jet d'Eau",
            city = "Geneva", country = "Switzerland", source = "SkylineWebcams",
            feedUrl = "https://cdn.skylinewebcams.com/live497.jpg",
            externalUrl = "https://www.skylinewebcams.com/en/webcam/suisse/geneve/geneve/geneva-jet-deau.html",
        ),
    )
}
