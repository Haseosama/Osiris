package com.osiris.app.map

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Flat-earth dead reckoning: projects a lat/lng forward by [elapsedMs] given a heading (compass
 * bearing, 0°=N clockwise) and speed in knots. Flights/ships only report a position every poll
 * (60s/20s) — without this the marker teleports from one fix to the next instead of gliding,
 * which reads as fake. Good enough over the few-km distances covered between polls; not meant
 * for long-range navigation.
 *
 * Satellites don't come with a heading/speed at all (the backend reports a lat/lng from SGP4,
 * nothing else) — [bearing]/[speedKnots] derive an *implied* velocity from two consecutive polls
 * instead, which [project] can then extrapolate from just like a flight's reported one. A LEO
 * satellite covers hundreds of km between 60s polls, so those two use a proper great-circle
 * (haversine) distance rather than [project]'s flat approximation — accurate for measuring the
 * gap between two known fixes; only the short per-tick forward steps lean on the flat shortcut.
 */
object DeadReckoning {
    private const val KNOTS_TO_METERS_PER_MS = 1852.0 / 3_600_000.0
    private const val METERS_PER_DEGREE_LAT = 111_320.0
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun project(lat: Double, lng: Double, headingDeg: Double, speedKnots: Double, elapsedMs: Long): Pair<Double, Double> {
        if (elapsedMs <= 0 || speedKnots <= 0.0) return lat to lng
        val distanceM = speedKnots * KNOTS_TO_METERS_PER_MS * elapsedMs
        val headingRad = Math.toRadians(headingDeg)
        val metersPerDegreeLng = METERS_PER_DEGREE_LAT * cos(Math.toRadians(lat)).coerceAtLeast(0.01)
        val newLat = lat + (distanceM * cos(headingRad)) / METERS_PER_DEGREE_LAT
        val newLng = lng + (distanceM * sin(headingRad)) / metersPerDegreeLng
        return newLat to newLng
    }

    /** Great-circle bearing (compass degrees, 0°=N clockwise) from fix 1 to fix 2. */
    fun bearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lng2 - lng1)
        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Ground speed in knots implied by two fixes [elapsedMs] apart. */
    fun speedKnots(lat1: Double, lng1: Double, lat2: Double, lng2: Double, elapsedMs: Long): Double {
        if (elapsedMs <= 0) return 0.0
        return haversineMeters(lat1, lng1, lat2, lng2) / (KNOTS_TO_METERS_PER_MS * elapsedMs)
    }

    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lng2 - lng1)
        val a = sin(deltaPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(deltaLambda / 2).pow(2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
