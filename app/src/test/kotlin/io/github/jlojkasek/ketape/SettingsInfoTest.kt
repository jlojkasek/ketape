package io.github.jlojkasek.ketape

import android.content.Intent
import android.view.View
import android.widget.Button
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS")
class SettingsInfoTest {
    @Test
    fun reserveDetailsExpandAndOpenTheDifferentialModelSource() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            val activity = controller.get()
            activity.findText(R.string.settings_tab_about).performClick()
            val topic = activity.findText(R.string.info_reserve_title) as Button
            assertEquals("Réserve · CP and W′", topic.text.toString())
            assertEquals("Collapsed", topic.stateDescription.toString())

            topic.performClick()
            val details = activity.findText(R.string.info_reserve)
            assertTrue(details.isShown)
            assertTrue(activity.findText(R.string.info_source_skiba_original).isShown)
            activity.findText(R.string.info_source_skiba).performClick()
            val intent = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("https://doi.org/10.1123/ijspp.2021-0205", intent.dataString)

            topic.performClick()
            assertFalse(details.isShown)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(qualifiers = "de-rDE")
    fun germanInfoUsesLocalizedLabelsAndBody() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            val activity = controller.get()
            activity.findText(R.string.settings_tab_about).performClick()
            assertTrue(activity.findText(R.string.info_fields).isShown)
            val topic = activity.findText(R.string.pedal_settings_title)
            topic.performClick()
            assertEquals("Aufgeklappt", topic.stateDescription.toString())
            assertTrue(activity.getString(R.string.info_pedals).contains("Trittgleichmäßigkeit"))
            assertTrue(activity.findText(R.string.info_pedals).isShown)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun SettingsActivity.findText(resource: Int): View {
        val matches = arrayListOf<View>()
        findViewById<View>(android.R.id.content).findViewsWithText(
            matches, getString(resource), View.FIND_VIEWS_WITH_TEXT
        )
        return matches.single { it.isShown }
    }
}
