package io.github.jlojkasek.ketape

import android.app.AlertDialog
import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.Button
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsResetTest {
    private val context = RuntimeEnvironment.getApplication()
    private val prefs = context.getSharedPreferences("stacked", Context.MODE_PRIVATE)

    @Test
    fun resetsAllOptionsWhilePreservingAccountImportsAndRideState() {
        val preserved = mapOf(
            INTERVALS_API_KEY to "encrypted fixture",
            "intervals_account_id" to "i1",
            "intervals_account_name" to "Test account",
            "intervals_cache_reserve_settings" to "import fixture",
            "intervals_local_s0_5" to 310,
            "intervals_cache_s0_at" to 123L,
            "reserve_checkpoint" to "checkpoint fixture",
            "reserve_reset_requested" to true
        )
        prefs.edit().apply {
            preserved.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Boolean -> putBoolean(key, value)
                }
            }
            putString("theme", "dark")
            putString("zone_colors", "badge")
            putBoolean("cadence_balance", true)
            putString(PEDAL_LAYOUT_KEY, PedalLayout.DYNAMICS.key)
            putInt(BALANCE_WARNING_KEY, 5)
            putString(RECORD_PERIOD, "both")
            putBoolean(RECORD_HEART, true)
            putBoolean(RECORD_SOUND, true)
            putStringSet(POWER_BEST_DURATIONS, setOf("30"))
            putBoolean("reserve_manual", true)
            putFloat("reserve_cp", 300f)
            putFloat("reserve_kj", 20f)
            putBoolean("reserve_colors", false)
            putInt("reserve_graph", 15)
            putInt("reserve_average", 30)
            putInt("reserve_threshold", 50)
            listOf("power", "heart-rate", "speed", "cadence").forEach { putInt(smoothingKey(it), 10) }
        }.apply()

        resetExtensionSettings(prefs)

        assertEquals(preserved, prefs.all)
        assertEquals(listOf("s0"), IntervalsRecords(context).periods)
        assertEquals(defaultPowerDurations, selectedPowerDurations(prefs.getStringSet(POWER_BEST_DURATIONS, null)))
        val reserve = ReservePreferences(context)
        assertFalse(reserve.manual)
        assertTrue(reserve.colors)
        assertEquals(5, reserve.graph)
        assertEquals(10, reserve.average)
        assertEquals(20, reserve.threshold)
    }

    @Test
    fun settingsAreResetOnlyWhenTheWarningIsConfirmed() {
        prefs.edit().putString("zone_colors", "badge").apply()
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()
        val buttons = arrayListOf<View>()
        activity.findViewById<View>(android.R.id.content)
            .findViewsWithText(buttons, activity.getString(R.string.settings_reset_all), View.FIND_VIEWS_WITH_TEXT)
        val reset = buttons.filterIsInstance<Button>().single()

        reset.performClick()
        assertEquals("badge", prefs.getString("zone_colors", null))
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("badge", prefs.getString("zone_colors", null))

        reset.performClick()
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(prefs.contains("zone_colors"))

        controller.pause().stop().destroy()
    }
}
