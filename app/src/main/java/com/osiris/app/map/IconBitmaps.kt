package com.osiris.app.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Shader

/**
 * Renders a vector drawable to a shaded bitmap for use as a MapLibre style image — one bitmap
 * per (icon, color) pair so category colors keep working via iconImage match expressions. A
 * flat single-tone fill (plain `Drawable.setTint`) reads poorly against a photographic satellite
 * basemap, so this fakes a bit of depth from that one category color instead: a soft drop
 * shadow, a top-lit gradient across the fill, and a thin dark outline for edge contrast — no
 * per-icon asset work needed.
 */
object IconBitmaps {
    // The glyph itself still renders at the same 96px it always has (matching the iconSize
    // values already tuned per layer in LayersController) — PADDING_PX is extra room around it
    // for the shadow/outline to bleed into without clipping at the bitmap edge.
    private const val PADDING_PX = 8
    private const val GLYPH_PX = 96
    private const val SIZE_PX = GLYPH_PX + PADDING_PX * 2

    fun render(context: Context, resId: Int, tint: Int): Bitmap {
        val mask = renderMask(context, resId)
        val size = SIZE_PX.toFloat()

        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Drop shadow: the same silhouette, dark and blurred, offset down-right — BlurMaskFilter
        // only works on a software-rendered canvas, which this off-screen bitmap always is.
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 110
            maskFilter = BlurMaskFilter(size * 0.05f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(mask, size * 0.06f, size * 0.08f, shadowPaint)

        // Outline: the silhouette scaled up slightly and recolored dark, so a thin rim of it
        // peeks out from behind the full-size gradient fill drawn on top of it below.
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = PorterDuffColorFilter(shade(tint, 0.35f), PorterDuff.Mode.SRC_IN)
        }
        val outlineMatrix = Matrix().apply { postScale(1.12f, 1.12f, size / 2f, size / 2f) }
        canvas.drawBitmap(mask, outlineMatrix, outlinePaint)

        // Fill: a top-lit gradient (lightened tint at top, base tint at bottom), masked to the
        // glyph via a saved layer + SRC_IN composite — a plain colorFilter can't carry a shader.
        val layer = canvas.saveLayer(0f, 0f, size, size, null)
        canvas.drawBitmap(mask, 0f, 0f, null)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, size, shade(tint, 1.5f), shade(tint, 0.85f), Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        canvas.drawRect(0f, 0f, size, size, fillPaint)
        canvas.restoreToCount(layer)

        return bitmap
    }

    /** White silhouette of the drawable, inset by [PADDING_PX] so [render] has room to draw the
     * shadow/outline around it without clipping at the bitmap edge. */
    private fun renderMask(context: Context, resId: Int): Bitmap {
        val drawable = requireNotNull(context.getDrawable(resId)) { "Missing drawable resource $resId" }.mutate()
        drawable.setTint(Color.WHITE)
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(PADDING_PX, PADDING_PX, SIZE_PX - PADDING_PX, SIZE_PX - PADDING_PX)
        drawable.draw(canvas)
        return bitmap
    }

    /** [factor] > 1 lightens toward white, < 1 darkens toward black — used to derive the
     * gradient's highlight/shadow stops and the outline color from a single category tint. */
    private fun shade(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
        return Color.HSVToColor(Color.alpha(color), hsv)
    }
}
