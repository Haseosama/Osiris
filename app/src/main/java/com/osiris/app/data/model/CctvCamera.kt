package com.osiris.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the JSON returned by GET /api/cctv — called with no query params, which loads every
 * region the backend knows about (README: "17,000+ Cameras"). Most cameras expose a static JPEG
 * snapshot ([feedUrl]); a smaller subset (e.g. Quebec 511) expose an MP4 [streamUrl] instead.
 */
@Serializable
data class CctvResponse(
    val cameras: List<CctvCamera> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class CctvCamera(
    val id: String,
    val lat: Double,
    val lng: Double,
    val name: String? = null,
    val city: String? = null,
    val country: String? = null,
    @SerialName("feed_url") val feedUrl: String? = null,
    @SerialName("stream_url") val streamUrl: String? = null,
    val source: String? = null,
)
