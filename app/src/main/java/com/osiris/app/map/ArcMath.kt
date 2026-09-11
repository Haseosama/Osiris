package com.osiris.app.map

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Quadratic-bezier approximation of a "flight arc" between two points — bulges perpendicular to
 * the straight line by a fraction of its length. The web app animates cyberattacks as flying
 * arcs across the globe; this is the native equivalent of that curve. Shared by LayersController
 * (draws the static curve) and MapViewModel (animates a pulse along that same curve) so the two
 * agree on the path instead of drifting apart.
 */
object ArcMath {
    private const val BULGE_FRACTION = 0.15

    private fun control(srcLng: Double, srcLat: Double, dstLng: Double, dstLat: Double): Pair<Double, Double> {
        val midLng = (srcLng + dstLng) / 2
        val midLat = (srcLat + dstLat) / 2
        val dx = dstLng - srcLng
        val dy = dstLat - srcLat
        val dist = sqrt(dx * dx + dy * dy).let { if (it < 1e-6) 1.0 else it }
        val bulge = dist * BULGE_FRACTION
        return (midLng + (-dy / dist) * bulge) to (midLat + (dx / dist) * bulge)
    }

    fun pointAt(srcLng: Double, srcLat: Double, dstLng: Double, dstLat: Double, t: Double): Pair<Double, Double> {
        val (ctrlLng, ctrlLat) = control(srcLng, srcLat, dstLng, dstLat)
        val lng = (1 - t).pow(2) * srcLng + 2 * (1 - t) * t * ctrlLng + t.pow(2) * dstLng
        val lat = (1 - t).pow(2) * srcLat + 2 * (1 - t) * t * ctrlLat + t.pow(2) * dstLat
        return lng to lat
    }

    fun curve(srcLng: Double, srcLat: Double, dstLng: Double, dstLat: Double, segments: Int = 24): List<Pair<Double, Double>> =
        (0..segments).map { i -> pointAt(srcLng, srcLat, dstLng, dstLat, i.toDouble() / segments) }
}
