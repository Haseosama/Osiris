package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/** Italy CCTV/webcams — mirrors `osiris-backend/src/app/api/cctv/italy.ts`, a fully static
 * SkylineWebcams list. Unlike the other SkylineWebcams fragments ported earlier this phase
 * (France, Switzerland), all 16 land here now: [com.osiris.app.map.CctvViewerDialog]'s image
 * loader sends the same self-referencing `Referer` header the backend's proxy used to add, so
 * the `feed_url` entries work directly instead of needing to be skipped. */
object ItalyCctvSource {
    suspend fun fetch(): List<CctvCamera> = listOf(
        cam("sky-it-trevi", 41.9009, 12.4833, "Rome - Trevi Fountain", "Rome", "live341", "roma/fontana-di-trevi"),
        cam("sky-it-pantheon", 41.8986, 12.4769, "Rome - Pantheon", "Rome", null, "roma/pantheon"),
        cam("sky-it-colosseum", 41.8902, 12.4922, "Rome - Colosseum", "Rome", null, "roma/colosseo"),
        cam("sky-it-navona", 41.8992, 12.4731, "Rome - Piazza Navona", "Rome", "live343", "roma/piazza-navona"),
        cam("sky-it-spagna", 41.9059, 12.4827, "Rome - Spanish Steps", "Rome", null, "roma/piazza-di-spagna"),
        cam("sky-it-rialto", 45.4381, 12.3358, "Venice - Rialto Bridge", "Venice", null, "venezia/ponte-di-rialto"),
        cam("sky-it-sanmarco", 45.4341, 12.3384, "Venice - St. Mark's Square", "Venice", null, "venezia/piazza-san-marco"),
        cam("sky-it-grandcanal", 45.4311, 12.3283, "Venice - Grand Canal", "Venice", null, "venezia/canal-grande"),
        cam("sky-it-duomo", 45.4642, 9.1900, "Milan - Milan Cathedral", "Milan", null, "milano/duomo-milano"),
        cam("sky-it-sanbabila", 45.4665, 9.1969, "Milan - Piazza San Babila", "Milan", "live435", "milano/piazza-san-babila"),
        cam("sky-it-signoria", 43.7695, 11.2558, "Florence - Piazza della Signoria", "Florence", "live245", "firenze/piazza-della-signoria"),
        cam("sky-it-pontevecchio", 43.7687, 11.2530, "Florence - Ponte Vecchio", "Florence", null, "firenze/ponte-vecchio"),
        cam("sky-it-vesuvio", 40.8174, 14.4261, "Naples - Mount Vesuvius", "Naples", "live66", "napoli/vesuvio"),
        cam("sky-it-plebiscito", 40.8359, 14.2487, "Naples - Piazza del Plebiscito", "Naples", "live260", "napoli/piazza-del-plebiscito"),
        cam("sky-it-amalfi", 40.6333, 14.6027, "Amalfi Coast - Positano", "Positano", "live259", "salerno/positano"),
        cam("sky-it-etna", 37.7510, 14.9934, "Mount Etna - Volcano", "Catania", null, "catania/vulcano-etna"),
    )

    private fun cam(id: String, lat: Double, lng: Double, name: String, city: String, liveId: String?, path: String): CctvCamera {
        val region = when {
            path.startsWith("roma") -> "lazio"
            path.startsWith("venezia") -> "veneto"
            path.startsWith("milano") -> "lombardia"
            path.startsWith("firenze") -> "toscana"
            path.startsWith("napoli") || path.startsWith("salerno") -> "campania"
            else -> "sicilia"
        }
        return CctvCamera(
            id = id,
            lat = lat,
            lng = lng,
            name = name,
            city = city,
            country = "Italy",
            feedUrl = liveId?.let { "https://cdn.skylinewebcams.com/$it.jpg" },
            externalUrl = "https://www.skylinewebcams.com/en/webcam/italia/$region/$path.html",
            source = "SkylineWebcams",
        )
    }
}
