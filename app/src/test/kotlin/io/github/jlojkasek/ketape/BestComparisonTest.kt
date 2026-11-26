package io.github.jlojkasek.ketape

import android.app.AlertDialog
import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RemoteViews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
class BestComparisonTest {
    private val durations = defaultPowerDurations

    @Test fun comparisonPeriodsSupportEveryPairAndPreserveLegacySettings() {
        fun selected(stored: String?) = ComparisonPeriod.selected(stored).map { it.id }
        assertEquals(listOf("s0"), selected(null))
        assertEquals(listOf("42d"), selected("42d"))
        assertEquals(listOf("s0", "42d"), selected("both"))
        assertEquals(listOf("s0", "all"), selected("all,s0"))
        assertEquals(listOf("42d", "all"), selected("42d,all"))
        assertEquals(2, selected("s0,42d,all").size)
    }

    @Test fun comparisonPeriodDialogPreventsEmptyOrThirdSelectionAndSavesOnlyOnApply() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()
        val prefs = activity.getSharedPreferences("stacked", Context.MODE_PRIVATE)
        fun button(text: String): Button {
            val matches = arrayListOf<View>()
            activity.findViewById<View>(android.R.id.content)
                .findViewsWithText(matches, text, View.FIND_VIEWS_WITH_TEXT)
            return matches.filterIsInstance<Button>().single()
        }
        button(activity.getString(R.string.best_comparison)).performClick()
        val summary = button(activity.getString(R.string.records_period_value,
            activity.getString(R.string.records_season_short)))
        summary.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val list = dialog.listView
        repeat(list.adapter.count) { list.adapter.getView(it, null, list) }
        assertTrue(list.isItemChecked(0))
        fun toggle(index: Int) {
            list.performItemClick(View(activity), index, list.adapter.getItemId(index))
        }
        toggle(0)
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        toggle(1)
        toggle(2)
        toggle(0)
        assertFalse(list.isItemChecked(0))
        assertTrue(list.isItemChecked(1))
        assertTrue(list.isItemChecked(2))
        assertFalse(prefs.contains(RECORD_PERIOD))

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("42d,all", prefs.getString(RECORD_PERIOD, null))
        assertEquals(activity.getString(R.string.records_period_value,
            listOf(R.string.comparison_42_days, R.string.records_all_time)
                .joinToString(" · ") { activity.getString(it) }), summary.text.toString())
        controller.pause().stop().destroy()
    }

    @Test fun selectedDurationsKeepTheirOrderAndFallBackToAll() {
        assertEquals(listOf(5, 1200), selectedPowerDurations(setOf("1200", "invalid", "5")))
        assertEquals(defaultPowerDurations, selectedPowerDurations(null))
        assertEquals(defaultPowerDurations, selectedPowerDurations(emptySet()))
    }

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
            if (count > 1) {
                remote.setViewVisibility(R.id.comparison_header, View.VISIBLE)
                val header = RemoteViews(context.packageName, R.layout.best_comparison_row)
                header.setViewVisibility(R.id.comparison_second_reference, View.VISIBLE)
                remote.addView(R.id.comparison_header, header)
            }
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
            if (count > 1) {
                val header = view.findViewById<LinearLayout>(R.id.comparison_header)
                assertTrue(header.height > 0)
                assertEquals(header.bottom, rows.top)
                assertEquals(rows.getChildAt(0).width, header.getChildAt(0).width)
            }
            for (index in 0 until count) {
                val row = rows.getChildAt(index) as LinearLayout
                assertTrue(row.height > 0)
                assertEquals(4, row.childCount)
                for (cell in 0 until row.childCount) assertTrue(row.getChildAt(cell).width > 0)
            }
        }
    }
}
