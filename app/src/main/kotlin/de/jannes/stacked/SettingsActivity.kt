package de.jannes.stacked

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.text.InputType
import android.text.format.DateFormat
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import kotlinx.coroutines.*
import java.util.Date
import kotlin.math.roundToInt

class SettingsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("stacked", MODE_PRIVATE) }

    private val records by lazy { IntervalsRecords(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var recordStatus: TextView
    private lateinit var connectButton: Button
    private lateinit var syncButton: Button
    private var recordJob: Job? = null

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
            val balanceEnabled = prefs.getBoolean("cadence_balance", false)
            val warningSettings = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (balanceEnabled) View.VISIBLE else View.GONE
                addView(label(R.string.settings_balance_warning_detail))
                addView(Button(this@SettingsActivity).apply {
                    val options = balanceWarningThresholds.map { threshold ->
                        if (threshold == 0) getString(R.string.settings_off)
                        else getString(R.string.settings_balance_warning_threshold, threshold)
                    }.toTypedArray()
                    var selected = balanceWarningThresholds.indexOf(prefs.getInt(BALANCE_WARNING_KEY, 0))
                        .coerceAtLeast(0)
                    text = getString(R.string.settings_balance_warning_value, options[selected])
                    setOnClickListener {
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle(R.string.settings_balance_warning)
                            .setSingleChoiceItems(options, selected) { dialog, index ->
                                selected = index
                                prefs.edit().putInt(BALANCE_WARNING_KEY, balanceWarningThresholds[index]).apply()
                                text = getString(R.string.settings_balance_warning_value, options[index])
                                dialog.dismiss()
                            }
                            .setNegativeButton(R.string.settings_cancel, null)
                            .show()
                    }
                })
            }
            addView(Switch(this@SettingsActivity).apply {
                setText(R.string.settings_balance)
                minHeight = dp(48)
                isChecked = balanceEnabled
                setOnCheckedChangeListener { _, enabled ->
                    prefs.edit().putBoolean("cadence_balance", enabled).apply()
                    warningSettings.visibility = if (enabled) View.VISIBLE else View.GONE
                }
            })
            addView(label(R.string.settings_balance_detail))
            addView(warningSettings)

            addView(label(R.string.records_title, heading = true))
            addView(label(R.string.records_detail))
            recordStatus = TextView(this@SettingsActivity).also { addView(it) }
            connectButton = Button(this@SettingsActivity).apply {
                setOnClickListener {
                    if (records.connected) {
                        runRecordAction { records.disconnect() }
                    } else {
                        showApiKeyDialog()
                    }
                }
            }.also { addView(it) }
            syncButton = Button(this@SettingsActivity).apply {
                setText(R.string.records_sync)
                setOnClickListener { syncRecords(force = true) }
            }.also { addView(it) }
            addView(choices(RECORD_PERIOD, listOf("s0" to R.string.records_season, "42d" to R.string.records_42_days)) {
                syncRecords()
            })
            addView(Switch(this@SettingsActivity).apply {
                setText(R.string.records_heart)
                minHeight = dp(48)
                isChecked = prefs.getBoolean(RECORD_HEART, false)
                setOnCheckedChangeListener { _, enabled ->
                    prefs.edit().putBoolean(RECORD_HEART, enabled).apply()
                    if (enabled) syncRecords(force = true)
                }
            })
            addView(Switch(this@SettingsActivity).apply {
                setText(R.string.records_sound)
                minHeight = dp(48)
                isChecked = prefs.getBoolean(RECORD_SOUND, false)
                setOnCheckedChangeListener { _, enabled ->
                    prefs.edit().putBoolean(RECORD_SOUND, enabled).apply()
                }
            })

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
        updateRecordStatus()
        syncRecords()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun showApiKeyDialog() {
        val input = EditText(this).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setHint(R.string.records_api_key)
            isSaveEnabled = false
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.records_api_key)
            .setMessage(R.string.records_key_help)
            .setView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), 0, dp(24), 0)
                addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT))
            })
            .setNegativeButton(R.string.settings_cancel, null)
            .setPositiveButton(R.string.records_connect, null)
            .create()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val apiKey = input.text.toString().trim()
                if (apiKey.isEmpty()) {
                    input.error = getString(R.string.records_key_required)
                } else {
                    input.text.clear()
                    dialog.dismiss()
                    runRecordAction { records.connect(apiKey) }
                }
            }
        }
        dialog.show()
    }

    private fun syncRecords(force: Boolean = false) {
        if (records.connected) runRecordAction { records.sync(force) }
        else updateRecordStatus()
    }

    private fun runRecordAction(action: suspend () -> Unit) {
        recordJob?.cancel()
        recordJob = scope.launch {
            updateRecordStatus(R.string.records_working, busy = true)
            try {
                action()
                updateRecordStatus()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IntervalsKeyRejected) {
                updateRecordStatus(R.string.records_key_rejected)
            } catch (_: Exception) {
                updateRecordStatus(R.string.records_error)
            }
        }
    }

    private fun updateRecordStatus(message: Int? = null, busy: Boolean = false) {
        val connected = records.connected
        val syncedAt = records.syncedAt()
        connectButton.setText(if (connected) R.string.records_disconnect else R.string.records_connect)
        connectButton.isEnabled = !busy
        syncButton.isEnabled = connected && !busy
        recordStatus.text = when {
            message != null -> getString(message)
            !connected -> getString(R.string.records_not_connected)
            syncedAt == 0L -> getString(R.string.records_pending)
            else -> Date(syncedAt).let {
                getString(R.string.records_last_sync,
                    DateFormat.getDateFormat(this).format(it), DateFormat.getTimeFormat(this).format(it))
            }
        }
    }

    private fun choices(key: String, options: List<Pair<String, Int>>, onChange: () -> Unit = {}) = RadioGroup(this).apply {
        orientation = RadioGroup.VERTICAL
        val selected = prefs.getString(key, options.first().first)
        options.forEach { (value, title) ->
            val button = RadioButton(this@SettingsActivity).apply {
                id = View.generateViewId()
                setText(title)
                minHeight = dp(48)
                setOnClickListener {
                    prefs.edit().putString(key, value).apply()
                    onChange()
                }
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
