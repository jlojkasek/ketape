package io.github.jlojkasek.ketape

import android.view.View
import android.widget.RemoteViews
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PedalLayoutTest {
    @Test fun allVariantsFitIncludingReapplyAndMissingData() = checkLayouts()

    @Test @Config(qualifiers = "300dpi")
    fun karooDensityFits() = checkLayouts()

    private fun checkLayouts() {
        val context = RuntimeEnvironment.getApplication()
        val density = context.resources.displayMetrics.density
        for (widthDp in listOf(120, 160, 280)) for (heightDp in listOf(64, 100, 160)) {
            val width = (widthDp * density).toInt()
            val height = (heightDp * density).toInt()
            val host = RemoteViews(context.packageName, R.layout.stacked).apply(context, null)
            for (variant in PedalLayout.entries + PedalLayout.CLASSIC) for (tick in 0..12) {
                val pedals = if (tick == 12) PedalMetrics(100.0, 100.0, 100.0, 100.0, 100.0)
                    else PreviewValues.pedals(tick)
                val remote = RemoteViews(context.packageName, R.layout.stacked)
                remote.setImageViewResource(R.id.icon, R.drawable.ic_cadence)
                remote.setTextViewText(R.id.main, "110")
                remote.setTextViewText(R.id.unit, "rpm")
                remote.setTextViewText(R.id.left, "Ø 85")
                remote.setTextViewText(R.id.right, "MAX 110")
                remote.setViewVisibility(R.id.right, View.VISIBLE)
                PedalFieldLayout.apply(context, remote, width, height, 64, "110", variant, true, pedals, true)
                remote.reapply(context, host)
                host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                host.layout(0, 0, width, height)
                for (id in listOf(R.id.main, R.id.unit, R.id.left, R.id.right, R.id.pedal_header)) {
                    val view = host.findViewById<TextView>(id)
                    if (view.visibility == View.GONE) continue
                    val label = "$variant $width x $height: ${view.text}"
                    val layout = requireNotNull(view.layout)
                    assertEquals(label, 1, layout.lineCount)
                    assertEquals("Ellipsis $label", 0, layout.getEllipsisCount(0))
                    assertTrue("Width $label", layout.getLineWidth(0) <= view.width - view.totalPaddingLeft - view.totalPaddingRight + 1)
                    assertTrue("Height $label", layout.height <= view.height)
                }
                assertEquals(if (variant == PedalLayout.DYNAMICS) View.VISIBLE else View.GONE,
                    host.findViewById<View>(R.id.pedal_header).visibility)
            }
        }
    }
}
