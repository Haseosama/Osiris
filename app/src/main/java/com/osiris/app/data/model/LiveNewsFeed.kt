package com.osiris.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors the JSON returned by GET /api/live-news (broadcaster locations + stream URLs). */
@Serializable
data class LiveNewsResponse(
    val feeds: List<LiveNewsFeed> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class LiveNewsFeed(
    val id: String,
    val name: String,
    val city: String? = null,
    val country: String? = null,
    val lat: Double,
    val lng: Double,
    val url: String,
    @SerialName("embed_allowed") val embedAllowed: Boolean = false,
    val category: String? = null,
    val language: String? = null,
)
