package com.osiris.app.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas

/**
 * Renders a vector drawable to a tinted square bitmap for use as a MapLibre style image — one
 * bitmap per (icon, color) pair so category colors keep working via iconImage match
 * expressions, the same way they already work via circleColor for the untouched layers.
 */
object IconBitmaps {
    private const val SIZE_PX = 96

    fun render(context: Context, resId: Int, tint: Int): Bitmap {
        val drawable = requireNotNull(context.getDrawable(resId)) { "Missing drawable resource $resId" }.mutate()
        drawable.setTint(tint)
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, SIZE_PX, SIZE_PX)
        drawable.draw(canvas)
        return bitmap
    }
}
