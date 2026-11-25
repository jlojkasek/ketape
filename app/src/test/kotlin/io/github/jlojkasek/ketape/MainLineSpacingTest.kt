package io.github.jlojkasek.ketape

import android.content.Context
import android.view.View
import android.widget.TextView
import android.widget.RemoteViews
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses real Android TextView measurements, including RemoteViews reapply on an existing cell. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainLineSpacingTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val transitions = listOf("9", "10", "99", "100", "999", "1000", "9999", "10000", "--", "99")

    @Test fun digitChangesNeverWrapOrResizeTheCell() {
        run {
            for (width in listOf(120, 160, 280)) {
                for (height in listOf(64, 100, 160)) {
                    for (metricUnit in listOf("W", "bpm", "km/h", "km", "rpm")) {
                        val distance = metricUnit == "km"
                        for (badge in listOf(false, true)) {
                            val host = RemoteViews(context.packageName, R.layout.stacked).apply(context, null)
                            var previousMainHeight: Int? = null
                            for (digits in transitions) {
                                val main = if ((distance || metricUnit == "km/h") && digits != "--") "$digits,9" else digits
                                val left = if (distance) "↗ $digits m" else "Ø $digits"
                                val remote = RemoteViews(context.packageName, R.layout.stacked).apply {
                                    setTextViewText(R.id.main, main)
                                    setTextViewText(R.id.left, left)
                                    setTextViewText(R.id.right, if (distance) "↘ $digits m" else "NP $digits")
                                    setTextViewText(R.id.unit, metricUnit)
                                    setViewVisibility(R.id.right, View.VISIBLE)
                                    setTextViewText(R.id.zone, "Z7")
                                    setViewVisibility(R.id.zone, if (badge && !distance) View.VISIBLE else View.GONE)
                                }
                                MainLineLayout.apply(context, remote, px(width), px(height), 64,
                                    main, metricUnit)
                                remote.reapply(context, host)
                                host.measure(exact(width), exact(height))
                                host.layout(0, 0, px(width), px(height))
                                val label = "$width x $height dp, distance=$distance, badge=$badge, value=$digits"
                                val mainView = host.findViewById<TextView>(R.id.main)
                                assertEquals(label, main, mainView.text.toString())
                                val row = host.findViewById<View>(R.id.main_row)
                                previousMainHeight?.let { assertEquals(label, it, row.height) }
                                previousMainHeight = row.height
                                val icon = host.findViewById<View>(R.id.icon)
                                val unit = host.findViewById<TextView>(R.id.unit)
                                assertTrue("Unit detached from number: $label", unit.left - mainView.right <= px(4) + 1)
                                assertTrue("Icon detached from number: $label", mainView.left - icon.right <= px(5) + 1)
                                for (id in listOf(R.id.main, R.id.left, R.id.right, R.id.unit, R.id.zone)) {
                                    val view = host.findViewById<TextView>(id)
                                    if (view.visibility == View.GONE) continue
                                    val layout = requireNotNull(view.layout)
                                    assertEquals(label, 1, layout.lineCount)
                                    assertEquals("Unexpected truncation: $label / ${view.text}", 0, layout.getEllipsisCount(0))
                                    assertTrue("Horizontal clipping: $label / ${view.text}",
                                        layout.getLineWidth(0) <= view.width - view.totalPaddingLeft - view.totalPaddingRight + 1f)
                                    assertTrue("Vertical clipping: $label / ${view.text}",
                                        layout.height <= view.height - view.totalPaddingTop - view.totalPaddingBottom)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun px(dp: Int) = (dp * context.resources.displayMetrics.density + .5f).toInt()
    private fun exact(dp: Int) = View.MeasureSpec.makeMeasureSpec(px(dp), View.MeasureSpec.EXACTLY)
}
