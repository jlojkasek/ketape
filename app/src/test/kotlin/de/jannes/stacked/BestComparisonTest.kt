package de.jannes.stacked

import android.view.View
import android.widget.LinearLayout
import android.widget.RemoteViews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BestComparisonTest {
    private val durations = powerDurations + HEART_RECORD

    @Test fun smallestFieldCyclesThroughEveryComparison() {
        assertEquals(durations, durations.indices.flatMap { comparisonPage(durations, 1, it.toLong()) })
        assertEquals(listOf(1), comparisonPage(durations, 1, durations.size.toLong()))
    }

    @Test fun largerFieldsShowMoreRowsWithoutDroppingTheLastPage() {
        assertEquals(durations.take(4), comparisonPage(durations, 4, 0))
        assertEquals(durations.drop(4), comparisonPage(durations, 4, 1))
        assertEquals(durations, comparisonPage(durations, 10, 100))
    }

    @Test fun remoteRowsHaveSpaceInCompactAndFullLayouts() {
        val context = RuntimeEnvironment.getApplication()
        for ((width, height, count) in listOf(Triple(80, 32, 1), Triple(480, 720, durations.size))) {
            val remote = RemoteViews(context.packageName, R.layout.best_comparison)
            repeat(count) {
                val row = RemoteViews(context.packageName, R.layout.best_comparison_row)
                row.setViewVisibility(R.id.comparison_second_reference, View.VISIBLE)
                remote.addView(R.id.comparison_rows, row)
            }
            val view = remote.apply(context, null)
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            val rows = view.findViewById<LinearLayout>(R.id.comparison_rows)
            assertEquals(count, rows.childCount)
            for (index in 0 until count) {
                val row = rows.getChildAt(index) as LinearLayout
                assertTrue(row.height > 0)
                assertEquals(4, row.childCount)
                for (cell in 0 until row.childCount) assertTrue(row.getChildAt(cell).width > 0)
            }
        }
    }
}
