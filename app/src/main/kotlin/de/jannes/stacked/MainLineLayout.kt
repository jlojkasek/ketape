package de.jannes.stacked

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.RemoteViews

/** Match the wrap-content number and unit to the host's actual pixel bounds. */
internal object MainLineLayout {
    fun apply(context: Context, view: RemoteViews, widthPx: Int, heightPx: Int,
        recommendedSp: Int, number: String, unit: String) {
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val scale = metrics.scaledDensity
        val width = if (widthPx > 0) widthPx.toFloat() else 120f * density
        val height = if (heightPx > 0) heightPx.toFloat() else 80f * density
        // Root padding (8 dp), icon (22 dp), icon gap (5 dp), unit gap (4 dp).
        val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT
            textSize = 11f * scale
        }
        val roomWidth = (width - 39f * density - unitPaint.measureText(unit) - 2f * density).coerceAtLeast(1f)
        // Footer 20 dp and root padding 8 dp. Two pixels spare for rounding in Android layout.
        val roomHeight = (height - 28f * density - 2f).coerceAtLeast(1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            fontFeatureSettings = "tnum"
        }
        var size = (if (recommendedSp > 0) recommendedSp.toFloat() else 64f).coerceIn(8f, 64f)
        while (size > 8f) {
            paint.textSize = size * scale
            val fm = paint.fontMetrics
            if (paint.measureText(number) <= roomWidth && fm.descent - fm.ascent <= roomHeight) break
            size -= 1f
        }
        view.setTextViewTextSize(R.id.main, TypedValue.COMPLEX_UNIT_SP, size)
        view.setTextViewTextSize(R.id.unit, TypedValue.COMPLEX_UNIT_SP, 11f)
        view.setInt(R.id.main, "setMaxWidth", roomWidth.toInt())
    }
}
