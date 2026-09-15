package com.osiris.app.globe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.osiris.app.data.source.DirectHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * NASA's "Blue Marble" whole-Earth composite (public domain, Visible Earth/Earth Observatory —
 * the same image widely reused across open-source globe demos) — an equirectangular (Plate
 * Carrée) projection, which is exactly the projection [SphereMesh]'s texture coordinates assume.
 * A single Web Mercator tile (what the rest of this app's map layers use) would NOT work here:
 * Mercator is heavily stretched away from the equator and simply stops short of the poles, so it
 * would wrap onto a sphere badly distorted and with visible gaps at the top/bottom.
 *
 * Cached to disk after the first successful download — at ~2.5MB this isn't something to
 * re-fetch on every visit to the globe screen the way, say, a 60-second flight poll is; unlike
 * the satellite TLE cache elsewhere in this app (deliberately memory-only, refills fine on a cold
 * start), a multi-megabyte image is worth the two lines of File I/O to avoid repeating.
 */
object EarthTextureLoader {

    private const val TEXTURE_URL =
        "https://eoimages.gsfc.nasa.gov/images/imagerecords/73000/73909/world.topo.bathy.200412.3x5400x2700.jpg"
    private const val CACHE_FILE_NAME = "earth_texture_v1.jpg"

    // Downsampled from the source's native 5400px width: plenty of detail for a phone-sized
    // globe, and a quarter the decode memory of the full image. The GLES 2.0 spec technically
    // only guarantees GL_MAX_TEXTURE_SIZE >= 64, but every real device from this app's minSdk 26
    // (2017+) era supports 4096 or more — 2048 has margin to spare in practice.
    private const val TARGET_WIDTH = 2048

    /** Null on any failure (offline, host unreachable, corrupt download) — [GlobeRenderer] just
     * keeps showing its plain placeholder sphere rather than crashing or leaving the screen
     * blank; there's always something to look at either way. */
    suspend fun load(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
        if (cacheFile.exists()) {
            runCatching { decodeSampled(cacheFile.readBytes()) }.getOrNull()?.let { return@withContext it }
        }

        val bytes = runCatching { DirectHttp.getBytes(TEXTURE_URL) }.getOrNull() ?: return@withContext null
        runCatching { cacheFile.writeBytes(bytes) }
        runCatching { decodeSampled(bytes) }.getOrNull()
    }

    private fun decodeSampled(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= TARGET_WIDTH) sampleSize *= 2

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: error("BitmapFactory returned null")
    }
}
