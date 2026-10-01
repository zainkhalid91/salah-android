package com.zainkhalid.salah.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.zainkhalid.salah.R
import kotlin.math.ceil
import kotlin.math.max

/**
 * Widgets can't use custom fonts, so the dot matrix text is drawn into a bitmap
 * with the Doto font and shown as an image.
 */
object PixelBitmap {
    @Volatile private var typeface: Typeface? = null

    private fun font(context: Context): Typeface =
        typeface ?: (runCatching { ResourcesCompat.getFont(context, R.font.doto_black) }.getOrNull() ?: Typeface.MONOSPACE)
            .also { typeface = it }

    /** Renders [text] at [heightPx], shrunk if needed to fit [maxWidthPx]. */
    fun render(context: Context, text: String, heightPx: Float, maxWidthPx: Float, color: Int): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = font(context)
            this.color = color
            textSize = heightPx
        }
        val width = paint.measureText(text)
        if (width > maxWidthPx && maxWidthPx > 0f) paint.textSize = heightPx * maxWidthPx / width
        val fm = paint.fontMetrics
        val w = max(1, ceil(paint.measureText(text)).toInt())
        val h = max(1, ceil(fm.descent - fm.ascent).toInt())
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, 0f, -fm.ascent, paint)
        return bitmap
    }
}
