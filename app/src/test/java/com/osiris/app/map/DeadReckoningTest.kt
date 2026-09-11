package com.osiris.app.map

import org.junit.Assert.assertEquals
import org.junit.Test

class DeadReckoningTest {

    @Test
    fun `no time elapsed leaves position unchanged`() {
        val (lat, lng) = DeadReckoning.project(48.0, 2.0, headingDeg = 90.0, speedKnots = 100.0, elapsedMs = 0)
        assertEquals(48.0, lat, 0.0)
        assertEquals(2.0, lng, 0.0)
    }

    @Test
    fun `zero speed leaves position unchanged`() {
        val (lat, lng) = DeadReckoning.project(48.0, 2.0, headingDeg = 90.0, speedKnots = 0.0, elapsedMs = 60_000)
        assertEquals(48.0, lat, 0.0)
        assertEquals(2.0, lng, 0.0)
    }

    @Test
    fun `heading north for one hour at 60 knots moves latitude by about one degree`() {
        // 60 knots for 1h = 60 nautical miles = 111 120 m; 1 degree of latitude is ~111 320 m,
        // so this should land just under 1 degree north.
        val (lat, lng) = DeadReckoning.project(0.0, 0.0, headingDeg = 0.0, speedKnots = 60.0, elapsedMs = 3_600_000)
        assertEquals(0.998, lat, 0.001)
        assertEquals(0.0, lng, 1e-9)
    }

    @Test
    fun `heading east at the equator moves longitude only`() {
        val (lat, lng) = DeadReckoning.project(0.0, 0.0, headingDeg = 90.0, speedKnots = 60.0, elapsedMs = 3_600_000)
        assertEquals(0.0, lat, 1e-9)
        assertEquals(0.998, lng, 0.001)
    }

    @Test
    fun `same eastward speed covers roughly twice the longitude degrees at 60 degrees latitude`() {
        // cos(60°) = 0.5, so a degree of longitude spans half the ground distance there —
        // the same real-world displacement should span twice the longitude degrees.
        val (_, lngAtEquator) = DeadReckoning.project(0.0, 0.0, headingDeg = 90.0, speedKnots = 60.0, elapsedMs = 3_600_000)
        val (_, lngAt60) = DeadReckoning.project(60.0, 0.0, headingDeg = 90.0, speedKnots = 60.0, elapsedMs = 3_600_000)
        assertEquals(lngAtEquator * 2, lngAt60, 0.01)
    }
}
