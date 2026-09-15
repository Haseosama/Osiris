package com.osiris.app.globe

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * A UV-sphere (latitude/longitude rings of quads, each split into two triangles) — the standard
 * way to approximate a sphere with a triangle mesh when you need a simple, predictable texture
 * mapping (an equirectangular world map wraps onto this exactly the way it wraps onto a globe).
 * [latBands]/[lonBands] default to a middle ground: dense enough that the silhouette reads as
 * round even zoomed in on the limb, not so dense that (latBands+1)*(lonBands+1) vertices risks
 * the 32,767-vertex ceiling of the 16-bit indices [GlobeRenderer] draws with (GLES 2.0 has no
 * 32-bit index drawing without an extension, and this globe has no real need for one).
 *
 * Position/normal are identical for a unit sphere scaled by [radius] (a sphere's surface normal
 * at any point is just that point's own direction from the center) — kept as separate buffers
 * anyway since the shader uses normals unscaled for lighting but positions scaled for placement.
 */
class SphereMesh(latBands: Int = 48, lonBands: Int = 96, radius: Float = 1f) {

    val vertexBuffer: FloatBuffer
    val normalBuffer: FloatBuffer
    val texCoordBuffer: FloatBuffer
    val indexBuffer: ShortBuffer
    val indexCount: Int

    init {
        val vertices = ArrayList<Float>((latBands + 1) * (lonBands + 1) * 3)
        val normals = ArrayList<Float>((latBands + 1) * (lonBands + 1) * 3)
        val texCoords = ArrayList<Float>((latBands + 1) * (lonBands + 1) * 2)

        for (lat in 0..latBands) {
            // theta sweeps 0 (north pole) to PI (south pole) — y=cos(theta) is +1 at the north
            // pole, matching a standard north-up equirectangular texture's v=0 row.
            val theta = Math.PI * lat / latBands
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)

            for (lon in 0..lonBands) {
                // phi sweeps a full 0..2*PI turn around the polar axis.
                val phi = 2.0 * Math.PI * lon / lonBands
                val sinPhi = sin(phi)
                val cosPhi = cos(phi)

                val x = (cosPhi * sinTheta).toFloat()
                val y = cosTheta.toFloat()
                val z = (sinPhi * sinTheta).toFloat()

                normals.add(x); normals.add(y); normals.add(z)
                vertices.add(x * radius); vertices.add(y * radius); vertices.add(z * radius)
                // u wraps with longitude, v with latitude — if a build ever shows the continents
                // mirrored east-west, flip this to lon.toFloat() / lonBands (a sign convention
                // that depends on phi's rotation direction versus the texture's own, impossible
                // to nail down for certain without seeing it actually render).
                texCoords.add(1f - lon.toFloat() / lonBands)
                texCoords.add(lat.toFloat() / latBands)
            }
        }

        val indices = ArrayList<Short>(latBands * lonBands * 6)
        for (lat in 0 until latBands) {
            for (lon in 0 until lonBands) {
                val first = lat * (lonBands + 1) + lon
                val second = first + lonBands + 1
                indices.add(first.toShort()); indices.add(second.toShort()); indices.add((first + 1).toShort())
                indices.add(second.toShort()); indices.add((second + 1).toShort()); indices.add((first + 1).toShort())
            }
        }
        indexCount = indices.size

        vertexBuffer = directFloatBuffer(vertices)
        normalBuffer = directFloatBuffer(normals)
        texCoordBuffer = directFloatBuffer(texCoords)
        indexBuffer = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply {
            indices.forEach { put(it) }
            position(0)
        }
    }

    private fun directFloatBuffer(values: List<Float>): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            values.forEach { put(it) }
            position(0)
        }
}
