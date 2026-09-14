package com.osiris.app.map

import kotlin.math.cos
import kotlin.math.sin

/**
 * Flat-earth dead reckoning: projects a lat/lng forward by [elapsedMs] given a heading (compass
 * bearing, 0°=N clockwise) and speed in knots. Flights/ships only report a position every poll
 * (60s/20s) — without this the marker teleports from one fix to the next instead of gliding,
 * which reads as fake. Good enough over the few-km distances covered between polls; not meant
 * for long-range navigation.
 *
 * Satellites don't use this at all: they're propagated live via SGP4 every animation tick instead
 * (see [com.osiris.app.data.source.CelesTrakSatelliteSource.propagateLive]) rather than
 * dead-reckoned from an implied velocity, since the actual orbit math is already cheap and
 * available on-device.
 */
object DeadReckoning {
    private const val KNOTS_TO_METERS_PER_MS = 1852.0 / 3_600_000.0
    private const val METERS_PER_DEGREE_LAT = 111_320.0

    fun project(lat: Double, lng: Double, headingDeg: Double, speedKnots: Double, elapsedMs: Long): Pair<Double, Double> {
        if (elapsedMs <= 0 || speedKnots <= 0.0) return lat to lng
        val distanceM = speedKnots * KNOTS_TO_METERS_PER_MS * elapsedMs
        val headingRad = Math.toRadians(headingDeg)
        val metersPerDegreeLng = METERS_PER_DEGREE_LAT * cos(Math.toRadians(lat)).coerceAtLeast(0.01)
        val newLat = lat + (distanceM * cos(headingRad)) / METERS_PER_DEGREE_LAT
        val newLng = lng + (distanceM * sin(headingRad)) / metersPerDegreeLng
        return newLat to newLng
    }
}
