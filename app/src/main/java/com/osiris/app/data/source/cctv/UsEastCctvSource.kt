package com.osiris.app.data.source.cctv

import com.osiris.app.data.model.CctvCamera

/** A handful of curated US-East cameras, called directly from the phone — mirrors `us-east` in
 * `osiris-backend/src/app/api/cctv/route.ts` (`fetchUSEastCameras`). Florida used to be fetched
 * here too (fl511.com's old API, long dead) — the backend's own comment notes it was dropped in
 * favour of [FloridaCctvSource]'s IBI 511 index, already ported in an earlier batch. */
object UsEastCctvSource {
    suspend fun fetch(): List<CctvCamera> = listOf(
        CctvCamera(
            id = "butler-oh-hamilton", lat = 39.3988617, lng = -84.5595353, name = "Hamilton, OH",
            city = "Hamilton", country = "US", source = "Butler County, OH",
            feedUrl = "https://gsccam.butlersheriff.org/axis-cgi/jpg/image.cgi",
            externalUrl = "https://gsccam.butlersheriff.org/camera/index.html#/video",
        ),
        CctvCamera(
            id = "butler-oh-129-747", lat = 39.381435, lng = -84.438423, name = "OH-129 at 747",
            city = "Butler County", country = "US", source = "Butler County, OH",
            feedUrl = "https://towercam.butlersheriff.org/axis-cgi/jpg/image.cgi",
            externalUrl = "https://towercam.butlersheriff.org/aca/index.html#view",
        ),
        CctvCamera(
            id = "cincinnati-cincyvision-yt", lat = 39.089101, lng = -84.527943, name = "CincyVision YT",
            city = "Cincinnati", country = "US", source = "Cincinnati, OH",
            externalUrl = "https://www.youtube.com/@AaronPreslin/live",
        ),
        CctvCamera(
            id = "cincinnati-covington-earthcam", lat = 39.090510, lng = -84.510413, name = "Cincinnati-Covington EarthCam",
            city = "Covington", country = "US", source = "Cincinnati, OH",
            externalUrl = "https://www.earthcam.com/usa/kentucky/covington/?cam=covington",
        ),
    )
}
