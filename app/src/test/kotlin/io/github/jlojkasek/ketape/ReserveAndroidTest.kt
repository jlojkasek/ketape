package io.github.jlojkasek.ketape

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.hammerhead.karooext.models.ViewConfig
import org.json.JSONObject
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
class ReserveAndroidTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val parameters = ReserveParameters(300.0, 22_000.0, "Manual CP")

    @Test fun cyclingSettingsUseJoulesAndPreferIndoorFtpForIndoorProfiles() {
        val settings = JSONObject("""{"ftp":300,"indoor_ftp":280,"w_prime":22000,"p_max":1000}""")
        assertEquals("Intervals.icu FTP", reserveSportParameters(settings)?.source)
        assertEquals(22_000.0, reserveSportParameters(settings)!!.capacity, 0.0)
        assertEquals(280.0, reserveSportParameters(settings, indoor = true)!!.watts, 0.0)
        assertNull(reserveSportParameters(JSONObject("""{"ftp":300,"w_prime":22}""")))
        assertNull(reserveSportParameters(null))
    }

    @Test fun checkpointPreservesAggregatesAndCanBeCleared() {
        val preferences = ReservePreferences(context)
        val engine = ReserveEngine(parameters)
        repeat(121) { engine.sample(it.toDouble(), 400.0) }
        preferences.save(engine, parameters)
        val (parametersAfterRestart, restored) = requireNotNull(preferences.restore())
        assertEquals(parameters, parametersAfterRestart)
        assertEquals(engine.totals, restored.totals)
        assertTrue(restored.snapshot(200.0, 10, false).restored)
        preferences.clear()
        assertNull(preferences.restore())
    }

    @Test @Config(qualifiers = "de-mdpi")
    fun germanRowsFitWithContinuityWarningAndNegativeBalance() = checkLayouts()

    @Test fun rowsFitAndRemoteViewsUpdatesReplacePreviousChildren() = checkLayouts()

    private fun checkLayouts() {
        val settings = ReservePreferences(context)
        val engine = ReserveEngine(parameters)
        repeat(121) { engine.sample(it.toDouble(), 400.0) }
        val depleting = engine.snapshot(120.0, 10, false)
        engine.gap()
        repeat(21) { engine.sample(130.0 + it, 100.0) }
        val recovering = engine.snapshot(150.0, 10, false)
        val missing = engine.snapshot(160.0, 10, false)
        for (size in listOf(108 to 42, 144 to 72, 226 to 82, 300 to 144)) {
            val config = ViewConfig(60 to 15, size, 24)
            for (field in ReserveField.entries) {
                val first = ReserveRenderer(context, config, settings).render(field, depleting)
                val view = first.apply(context, null)
                for (state in listOf(depleting, recovering, missing,
                    missing.copy(restored = true),
                    depleting.copy(totals = depleting.totals!!.copy(balance = -20_000.0, minimum = -20_000.0), restored = true))) {
                    ReserveRenderer(context, config, settings).render(field, state).reapply(context, view)
                    view.measure(exact(size.first), exact(size.second))
                    view.layout(0, 0, size.first, size.second)
                    val rows = view.findViewById<ViewGroup>(R.id.reserve_rows)
                    assertTrue(rows.childCount in 2..8)
                    repeat(rows.childCount) { index ->
                        val child = rows.getChildAt(index)
                        val label = "$field $size ${state.status} ${(child as? TextView)?.text}"
                        assertTrue("$label is clipped vertically", child.top >= 0 && child.bottom <= rows.height)
                        if (child is TextView) {
                            assertEquals("$label is truncated", 0, child.layout.getEllipsisCount(0))
                        }
                    }
                }
            }
        }
    }

    @Test fun smallestAvailableFieldHidesForecastsDuringMissingPowerAndPause() {
        val settings = ReservePreferences(context)
        val engine = ReserveEngine(parameters)
        engine.sample(0.0, 400.0)
        engine.sample(1.0, 400.0)
        val live = engine.snapshot(1.0, 10, false)
        assertNotNull(live.available(60))
        for (status in listOf(ReserveStatus.MISSING, ReserveStatus.PAUSED)) {
            val snapshot = live.copy(status = status, power = null)
            val host = ReserveRenderer(context, ViewConfig(60 to 15, 108 to 42, 24), settings)
                .render(ReserveField.AVAILABLE, snapshot).apply(context, null)
            val rows = host.findViewById<ViewGroup>(R.id.reserve_rows)
            val texts = (0 until rows.childCount).mapNotNull { (rows.getChildAt(it) as? TextView)?.text?.toString() }
            assertTrue(texts.any { status.symbol in it })
            assertFalse(texts.any { " W" in it })
        }
    }

    @Test fun displayChangesInvalidateAnUnchangedModelFrame() {
        val settings = ReservePreferences(context)
        val before = settings.displayKey()
        settings.prefs.edit().putBoolean("reserve_colors", !settings.colors).commit()
        assertNotEquals(before, settings.displayKey())
        val colored = settings.displayKey()
        settings.prefs.edit().putString("theme", "dark").commit()
        assertNotEquals(colored, settings.displayKey())
    }

    private fun exact(value: Int) = View.MeasureSpec.makeMeasureSpec(value, View.MeasureSpec.EXACTLY)
}
