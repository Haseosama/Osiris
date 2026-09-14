package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/** Thailand live webcams — mirrors `osiris-backend/src/app/api/cctv/thailand.ts`, a fully
 * static list. Thailand publishes no machine-readable CCTV feed of its own (the BMA's portal
 * refuses connections from off-network, the Highways Department serves only a viewer, and
 * SkylineWebcams' Bangkok posters turned out to be catalog stills, not live frames) — these are
 * fixed-position 24/7 YouTube streams instead, converted to external-link-only same as
 * elsewhere (see [FranceCctvSource]'s doc comment on `stream_type: 'iframe'`). */
object ThailandCctvSource {
    suspend fun fetch(): List<CctvCamera> = listOf(
        CctvCamera(
            id = "th-bangkok-sukhumvit-soi-11", lat = 13.7437, lng = 100.5556,
            name = "Sukhumvit Soi 11 — Bangkok", city = "Bangkok", country = "Thailand",
            source = "The Real Samui Webcam", externalUrl = "https://www.youtube.com/watch?v=UemFRPrl1hk",
        ),
        CctvCamera(
            id = "th-bangkok-sukhumvit-soi-19", lat = 13.7396, lng = 100.5601,
            name = "Sukhumvit Soi 19 — Bangkok", city = "Bangkok", country = "Thailand",
            source = "The Real Samui Webcam", externalUrl = "https://www.youtube.com/watch?v=Q71sLS8h9a4",
        ),
        CctvCamera(
            id = "th-samui-chaweng-green-mango", lat = 9.5071545, lng = 99.9957562,
            name = "Chaweng — Soi Green Mango", city = "Ko Samui", country = "Thailand",
            source = "The Real Samui Webcam", externalUrl = "https://www.youtube.com/watch?v=DwKCna1mumk",
        ),
        CctvCamera(
            id = "th-samui-chaweng-munchies", lat = 9.5071545, lng = 99.9957562,
            name = "Chaweng — Soi Green Mango (Munchies)", city = "Ko Samui", country = "Thailand",
            source = "The Real Samui Webcam", externalUrl = "https://www.youtube.com/watch?v=yFgVmioYkys",
        ),
        CctvCamera(
            id = "th-samui-lamai-crystal-bay", lat = 9.4700032, lng = 100.0459763,
            name = "Lamai — Crystal Bay Beach", city = "Ko Samui", country = "Thailand",
            source = "The Real Samui Webcam", externalUrl = "https://www.youtube.com/watch?v=Fw9hgttWzIg",
        ),
        CctvCamera(
            id = "th-phangan-srithanu-sanskara", lat = 9.7333, lng = 99.9833,
            name = "Koh Phangan — Srithanu Beach", city = "Ko Phangan", country = "Thailand",
            source = "The Real Samui Webcam", externalUrl = "https://www.youtube.com/watch?v=MW3fisTCXRQ",
        ),
    )
}
