package com.osiris.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the JSON returned by GET /api/cyber-attacks (attributed malware C2 traffic, real
 * data from abuse.ch Feodo Tracker). The web app animates these as flying arcs; this client
 * renders them as static lines for now — see README roadmap.
 */
@Serializable
data class CyberAttacksResponse(
    val attacks: List<CyberAttack> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class CyberAttack(
    val id: String,
    @SerialName("src_lng") val srcLng: Double,
    @SerialName("src_lat") val srcLat: Double,
    @SerialName("dst_lng") val dstLng: Double,
    @SerialName("dst_lat") val dstLat: Double,
    val malware: String? = null,
    @SerialName("target_ip") val targetIp: String? = null,
    @SerialName("target_country") val targetCountry: String? = null,
    val severity: Int = 5,
    val action: String? = null,
)
