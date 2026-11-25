package de.jannes.stacked

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import kotlin.math.roundToInt

class SettingsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("stacked", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            addView(ImageButton(this@SettingsActivity).apply {
                val background = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, background, true)
                setBackgroundResource(background.resourceId)
                setImageResource(R.drawable.ic_back)
                imageTintList = ColorStateList.valueOf(TextView(this@SettingsActivity).currentTextColor)
                contentDescription = getString(R.string.settings_back)
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(TextView(this@SettingsActivity).apply {
                setText(R.string.app_name)
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
            })
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(16))
            addView(label(R.string.settings_theme, heading = true))
            addView(choices("theme", listOf(
                "auto" to R.string.settings_system,
                "light" to R.string.settings_light,
                "dark" to R.string.settings_dark
            )))

            addView(label(R.string.settings_zones, heading = true))
            addView(label(R.string.settings_zones_detail))
            addView(choices("zone_colors", listOf(
                "background" to R.string.settings_zone_background,
                "badge" to R.string.settings_zone_badge,
                "off" to R.string.settings_off
            )))

            addView(label(R.string.field_6, heading = true))
            addView(Switch(this@SettingsActivity).apply {
                setText(R.string.settings_balance)
                minHeight = dp(48)
                isChecked = prefs.getBoolean("cadence_balance", false)
                setOnCheckedChangeListener { _, enabled ->
                    prefs.edit().putBoolean("cadence_balance", enabled).apply()
                }
            })
            addView(label(R.string.settings_balance_detail))

            addView(label(R.string.settings_about, heading = true))
            addView(label(R.string.settings_setup))
            addView(label(R.string.settings_updates).apply { setPadding(0, dp(8), 0, 0) })
            addView(TextView(this@SettingsActivity).apply {
                text = getString(R.string.settings_version, BuildConfig.VERSION_NAME)
                setPadding(0, dp(8), 0, 0)
            })
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar)
            addView(ScrollView(this@SettingsActivity).apply {
                addView(content)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
    }

    private fun choices(key: String, options: List<Pair<String, Int>>) = RadioGroup(this).apply {
        orientation = RadioGroup.VERTICAL
        val selected = prefs.getString(key, options.first().first)
        options.forEach { (value, title) ->
            val button = RadioButton(this@SettingsActivity).apply {
                id = View.generateViewId()
                setText(title)
                minHeight = dp(48)
                setOnClickListener { prefs.edit().putString(key, value).apply() }
            }
            addView(button)
            if (value == selected) check(button.id)
        }
    }

    private fun label(title: Int, heading: Boolean = false) = TextView(this).apply {
        setText(title)
        textSize = if (heading) 16f else 14f
        if (heading) {
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(4))
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
