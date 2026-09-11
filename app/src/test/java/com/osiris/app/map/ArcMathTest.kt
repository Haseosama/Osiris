package com.osiris.app.map

import org.junit.Assert.assertEquals
import org.junit.Test

class ArcMathTest {

    @Test
    fun `t=0 returns the source point`() {
        val (lng, lat) = ArcMath.pointAt(srcLng = 2.0, srcLat = 48.0, dstLng = 10.0, dstLat = 52.0, t = 0.0)
        assertEquals(2.0, lng, 1e-9)
        assertEquals(48.0, lat, 1e-9)
    }

    @Test
    fun `t=1 returns the destination point`() {
        val (lng, lat) = ArcMath.pointAt(srcLng = 2.0, srcLat = 48.0, dstLng = 10.0, dstLat = 52.0, t = 1.0)
        assertEquals(10.0, lng, 1e-9)
        assertEquals(52.0, lat, 1e-9)
    }

    @Test
    fun `midpoint bulges away from the straight line between src and dst`() {
        // src=(0,0), dst=(10,0): a straight line would sit at lat=0 for its whole length, so a
        // nonzero midpoint latitude proves the curve actually bulges off it, not just interpolates.
        val (midLng, midLat) = ArcMath.pointAt(srcLng = 0.0, srcLat = 0.0, dstLng = 10.0, dstLat = 0.0, t = 0.5)
        assertEquals(5.0, midLng, 1e-9)
        assertEquals(0.75, midLat, 1e-9)
    }

    @Test
    fun `curve returns segments+1 points running from source to destination`() {
        val points = ArcMath.curve(srcLng = 0.0, srcLat = 0.0, dstLng = 10.0, dstLat = 5.0, segments = 24)
        assertEquals(25, points.size)
        assertEquals(0.0 to 0.0, points.first())
        assertEquals(10.0 to 5.0, points.last())
    }

    @Test
    fun `degenerate curve (identical src and dst) does not divide by zero`() {
        val points = ArcMath.curve(srcLng = 3.0, srcLat = 45.0, dstLng = 3.0, dstLat = 45.0, segments = 4)
        points.forEach { (lng, lat) ->
            assertEquals(3.0, lng, 1e-9)
            assertEquals(45.0, lat, 1e-9)
        }
    }
}
