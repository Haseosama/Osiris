package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/** Germany CCTV/webcams — mirrors `osiris-backend/src/app/api/cctv/germany.ts`, a static list of
 * YouTube embeds. `stream_type: 'iframe'` has no player equivalent here (see
 * [FranceCctvSource]'s doc comment), so these become external-link-only entries. */
object GermanyCctvSource {
    suspend fun fetch(): List<CctvCamera> = listOf(
        CctvCamera(
            id = "de-berlin-1", lat = 52.5200, lng = 13.4050,
            name = "Berlin - Alexanderplatz", city = "Berlin", country = "Germany",
            source = "YouTube Live", externalUrl = "https://www.youtube.com/watch?v=IRqboacDNFg",
        ),
        CctvCamera(
            id = "de-munich-1", lat = 48.1351, lng = 11.5820,
            name = "Munich - Marienplatz", city = "Munich", country = "Germany",
            source = "YouTube Live", externalUrl = "https://www.youtube.com/watch?v=KxWuwC7R5kY",
        ),
    )
}
