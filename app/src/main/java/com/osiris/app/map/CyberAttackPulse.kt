package com.osiris.app.map

/** One animation frame's worth of position for one attack's pulse traveling along its [ArcMath] curve. */
data class CyberAttackPulse(val id: String, val lng: Double, val lat: Double, val severity: Int)
