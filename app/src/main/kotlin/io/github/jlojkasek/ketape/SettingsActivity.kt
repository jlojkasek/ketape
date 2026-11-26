package io.github.jlojkasek.ketape

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.text.TextUtils
import android.text.method.PasswordTransformationMethod
import android.text.format.DateFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.*
import java.util.Date
import kotlin.math.roundToInt

class SettingsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("stacked", MODE_PRIVATE) }

    private val records by lazy { IntervalsRecords(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var recordStatus: TextView
    private lateinit var defaultRecordStatusColors: ColorStateList
    private lateinit var recordFeedback: TextView
    private lateinit var referenceFeedback: TextView
    private lateinit var recordDetails: LinearLayout
    private lateinit var savedReferences: LinearLayout
    private lateinit var referenceConnection: Button
    private lateinit var recordHeart: Switch
    private lateinit var recordSound: Switch
    private lateinit var openConnectionSettings: () -> Unit
    private lateinit var recordValues: LinearLayout
    private lateinit var recordDates: TextView
    private lateinit var disconnectButton: Button
    private lateinit var connectButton: Button
    private lateinit var periodButton: Button
    private lateinit var syncButton: Button
    private var recordJob: Job? = null
    private var selectedTab = 0
    private val accountStatusListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == INTERVALS_API_KEY || key == INTERVALS_KEY_REJECTED ||
            key == "intervals_cache_s0_at" || key == "intervals_cache_42d_at" ||
            key == "intervals_cache_all_at") {
            val busy = recordJob?.isActive == true
            updateRecordStatus(R.string.records_working.takeIf { busy }, busy)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            setBackgroundColor(getColor(R.color.settings_header_background))
            addView(ImageButton(this@SettingsActivity).apply {
                background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, ColorDrawable(Color.WHITE))
                setImageResource(R.drawable.ic_back)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
                contentDescription = getString(R.string.settings_back)
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(ImageView(this@SettingsActivity).apply {
                setImageResource(R.drawable.ic_stacked)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                marginEnd = dp(8)
            })
            addView(TextView(this@SettingsActivity).apply {
                setText(R.string.app_name)
                setTextColor(Color.WHITE)
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
            })
        }

        lateinit var connectionSection: LinearLayout
        val display = settingsPage {
            addView(label(R.string.stacked_fields_name, heading = true))
            addView(label(R.string.settings_zones, heading = true))
            addView(label(R.string.settings_zones_detail))
            addView(choices("zone_colors", listOf(
                "background" to R.string.settings_zone_background,
                "badge" to R.string.settings_zone_badge,
                "off" to R.string.settings_off
            )))

            addView(label(R.string.settings_smoothing, heading = true))
            addView(label(R.string.settings_smoothing_detail))
            addView(smoothingButton("power", R.string.field_0))
            addView(smoothingButton("heart-rate", R.string.field_3))
            addView(smoothingButton("speed", R.string.field_4))

            addView(label(R.string.pedal_settings_title, heading = true))
            addView(pedalSettings())

            connectionSection = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(R.string.intervals_connection_title, heading = true))
                addView(label(R.string.intervals_connection_detail))
                recordStatus = TextView(this@SettingsActivity).apply {
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(0, dp(8), 0, 0)
                }.also {
                    defaultRecordStatusColors = it.textColors
                    addView(it)
                }
                recordFeedback = TextView(this@SettingsActivity).also { addView(it) }
                connectButton = Button(this@SettingsActivity).apply {
                    setText(R.string.records_connect)
                    setOnClickListener { showApiKeyDialog() }
                }.also { addView(it) }
                disconnectButton = Button(this@SettingsActivity, null, android.R.attr.borderlessButtonStyle).apply {
                    setText(R.string.records_disconnect)
                    setOnClickListener {
                        AlertDialog.Builder(this@SettingsActivity)
                            .setMessage(R.string.records_disconnect_detail)
                            .setNegativeButton(R.string.settings_cancel, null)
                            .setPositiveButton(R.string.records_disconnect) { _, _ ->
                                runRecordAction { records.disconnect() }
                            }.show()
                    }
                }.also { addView(it) }
            }.also { addView(it) }

            addView(Button(this@SettingsActivity, null, android.R.attr.borderlessButtonStyle).apply {
                setText(R.string.settings_reset_all)
                isAllCaps = false
                setOnClickListener { confirmSettingsReset() }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) })
        }

        val powerBest = settingsPage {
            addView(label(R.string.power_best_durations, heading = true))
            addView(label(R.string.power_best_durations_detail))
            addView(Button(this@SettingsActivity).apply {
                setText(R.string.power_best_choose_durations)
                setOnClickListener { showPowerDurationDialog() }
            })
            addView(label(R.string.records_title, heading = true))
            addView(label(R.string.records_detail))
            periodButton = Button(this@SettingsActivity).apply {
                isAllCaps = false
                setOnClickListener { showComparisonPeriodDialog() }
            }.also { addView(it) }
            referenceFeedback = TextView(this@SettingsActivity).also { addView(it) }
            referenceConnection = Button(this@SettingsActivity).apply {
                setOnClickListener { openConnectionSettings() }
            }.also { addView(it) }
            recordDetails = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                syncButton = Button(this@SettingsActivity).apply {
                    setText(R.string.records_sync)
                    setOnClickListener { syncRecords(force = true) }
                }.also { addView(it) }
            }.also { addView(it) }

            addView(label(R.string.records_notifications, heading = true))
            addView(label(R.string.records_notifications_detail))
            recordHeart = Switch(this@SettingsActivity).apply {
                setText(R.string.records_heart)
                minHeight = dp(48)
                isChecked = prefs.getBoolean(RECORD_HEART, false)
                setOnCheckedChangeListener { _, enabled ->
                    prefs.edit().putBoolean(RECORD_HEART, enabled).apply()
                    if (enabled && !records.keyRejected) syncRecords(force = true)
                }
            }.also { addView(it) }
            recordSound = Switch(this@SettingsActivity).apply {
                setText(R.string.records_sound)
                minHeight = dp(48)
                isChecked = prefs.getBoolean(RECORD_SOUND, false)
                setOnCheckedChangeListener { _, enabled ->
                    prefs.edit().putBoolean(RECORD_SOUND, enabled).apply()
                }
            }.also { addView(it) }
            savedReferences = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(R.string.records_saved_values, heading = true))
                recordDates = TextView(this@SettingsActivity).also { addView(it) }
                recordValues = LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                }.also { addView(it) }
            }.also { addView(it) }
            addView(label(R.string.ride_continuity_help))
        }

        val about = settingsPage {
            addView(label(R.string.settings_about, heading = true))
            addView(label(R.string.settings_setup))
            addView(label(R.string.settings_updates).apply { setPadding(0, dp(8), 0, 0) })
            addView(TextView(this@SettingsActivity).apply {
                text = getString(R.string.settings_version, BuildConfig.VERSION_NAME)
                setPadding(0, dp(8), 0, 0)
            })
            addView(label(R.string.endurance_field, heading = true))
            addView(label(R.string.endurance_help).apply { setPadding(0, dp(8), 0, 0) })
        }

        val reserve = settingsPage { addView(createReserveSettings()) }
        val pages = listOf(display, powerBest, reserve, about)
        val accent = TypedValue()
        theme.resolveAttribute(android.R.attr.colorAccent, accent, true)
        val tabTitles = listOf(R.string.settings_tab_display, R.string.best_comparison, R.string.settings_tab_reserve, R.string.settings_tab_about)
        val tabs = LinearLayout(this)
        val buttons = mutableListOf<Button>()
        val indicators = mutableListOf<View>()
        fun selectTab(index: Int) {
            selectedTab = index
            pages.forEachIndexed { position, page ->
                val selected = position == index
                page.visibility = if (selected) View.VISIBLE else View.GONE
                buttons[position].isSelected = selected
                buttons[position].setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
                indicators[position].visibility = if (selected) View.VISIBLE else View.INVISIBLE
            }
        }
        openConnectionSettings = {
            selectTab(0)
            display.post { display.smoothScrollTo(0, connectionSection.top) }
        }
        tabTitles.forEachIndexed { index, title ->
            tabs.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(Button(this@SettingsActivity, null, android.R.attr.borderlessButtonStyle).apply {
                    setText(title)
                    isAllCaps = false
                    textSize = 14f
                    maxLines = 1
                    setHorizontallyScrolling(false)
                    ellipsize = TextUtils.TruncateAt.END
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setAutoSizeTextTypeUniformWithConfiguration(11, 14, 1, TypedValue.COMPLEX_UNIT_SP)
                    }
                    minHeight = dp(48)
                    minimumWidth = 0
                    setPadding(dp(2), 0, dp(2), 0)
                    setOnClickListener { selectTab(index) }
                }.also { buttons.add(it) }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(View(this@SettingsActivity).apply {
                    setBackgroundColor(accent.data)
                }.also { indicators.add(it) }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(3)))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar)
            addView(tabs)
            pages.forEach { addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)) }
        })
        selectTab((savedInstanceState?.getInt("selected_tab") ?: 0).coerceIn(pages.indices))
        prefs.registerOnSharedPreferenceChangeListener(accountStatusListener)
        updateRecordStatus()
        syncRecords()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("selected_tab", selectedTab)
        super.onSaveInstanceState(outState)
    }

    private fun settingsPage(content: LinearLayout.() -> Unit) = ScrollView(this).apply {
        addView(LinearLayout(this@SettingsActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(16))
            content()
        })
    }

    private fun confirmSettingsReset() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_reset_title)
            .setMessage(R.string.settings_reset_warning)
            .setNegativeButton(R.string.settings_cancel, null)
            .setPositiveButton(R.string.settings_reset_confirm) { _, _ ->
                resetExtensionSettings(prefs)
                Toast.makeText(this, R.string.settings_reset_done, Toast.LENGTH_SHORT).show()
                recreate()
            }
            .show()
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(accountStatusListener)
        scope.cancel()
        super.onDestroy()
    }

    private fun createReserveSettings() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val settings = ReservePreferences(this@SettingsActivity)
        fun caption(value: String) = TextView(this@SettingsActivity).apply {
            text = value
            setPadding(0, dp(8), 0, dp(6))
        }
        fun button(value: String) = Button(this@SettingsActivity).apply {
            text = value
            isAllCaps = false
        }
        val parameterSummary = caption("")
        val feedback = caption("")
        val source = button("")
        val manual = button(getString(R.string.reserve_edit_manual))
        val sync = button("")
        val resetSection = LinearLayout(this@SettingsActivity).apply {
            orientation = LinearLayout.VERTICAL
        }
        var syncing = false
        fun refresh() {
            source.text = if (settings.manual) getString(R.string.reserve_source_manual) else getString(R.string.reserve_source_intervals)
            manual.visibility = if (settings.manual) View.VISIBLE else View.GONE
            sync.visibility = if (settings.manual) View.GONE else View.VISIBLE
            sync.setText(when {
                records.keyRejected -> R.string.records_update_key
                !records.connected -> R.string.records_connect
                else -> R.string.reserve_sync
            })
            sync.isEnabled = !syncing && (!ReserveMonitor.rideInProgress || !records.connected || records.keyRejected)
            resetSection.visibility = if (ReserveMonitor.rideInProgress) View.VISIBLE else View.GONE
            val parameters = settings.parameters
            parameterSummary.text = buildList {
                add(parameters?.let { getString(
                    if (settings.manual) R.string.reserve_manual_values else R.string.reserve_ftp_values,
                    it.watts, it.capacity / 1000
                ) } ?: getString(R.string.reserve_no_parameters))
                if (!settings.manual) {
                    settings.indoorCached?.takeIf { it.watts != parameters?.watts }?.let {
                        add(getString(R.string.reserve_indoor_ftp, it.watts))
                    }
                    val lastSync = settings.syncedAt.takeIf { it > 0 }?.let {
                        DateFormat.getMediumDateFormat(this@SettingsActivity).format(Date(it)) + " " +
                            DateFormat.getTimeFormat(this@SettingsActivity).format(Date(it))
                    } ?: getString(R.string.reserve_never)
                    add(getString(R.string.reserve_synced_at, lastSync))
                    if (ReserveMonitor.rideInProgress && records.connected && !records.keyRejected) {
                        add(getString(R.string.reserve_sync_after_ride))
                    }
                }
            }.joinToString("\n")
            feedback.visibility = if (!settings.manual && feedback.text.isNotEmpty()) View.VISIBLE else View.GONE
        }
        source.setOnClickListener {
            val options = arrayOf("Intervals.icu", getString(R.string.reserve_manual))
            AlertDialog.Builder(this@SettingsActivity).setTitle(getString(R.string.reserve_source))
                .setSingleChoiceItems(options, if (settings.manual) 1 else 0) { dialog, index ->
                    prefs.edit().putBoolean("reserve_manual", index == 1).apply()
                    feedback.text = ""
                    refresh()
                    dialog.dismiss()
                }.setNegativeButton(R.string.settings_cancel, null).show()
        }
        manual.setOnClickListener {
            val inputs = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), 0, dp(20), 0)
            }
            fun input(label: String, stored: Float) = EditText(this@SettingsActivity).apply {
                hint = label
                contentDescription = label
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                if (stored > 0) setText(String.format(java.util.Locale.getDefault(), "%.1f", stored))
                inputs.addView(caption(label))
                inputs.addView(this)
            }
            val cp = input(getString(R.string.reserve_cp_input), prefs.getFloat("reserve_cp", 0f))
            val capacity = input(getString(R.string.reserve_capacity_input), prefs.getFloat("reserve_kj", 0f))
            val dialog = createInputDialog(R.string.reserve_manual_parameters, inputs)
            dialog.show()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val watts = cp.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
                val kj = capacity.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
                if (watts !in 30.0..800.0) cp.error = getString(R.string.reserve_cp_error)
                else if (kj !in 1.0..100.0) capacity.error = getString(R.string.reserve_capacity_error)
                else {
                    prefs.edit().putFloat("reserve_cp", watts.toFloat()).putFloat("reserve_kj", kj.toFloat()).apply()
                    refresh()
                    dialog.dismiss()
                }
            }
        }
        sync.setOnClickListener {
            if (!records.connected || records.keyRejected) {
                openConnectionSettings()
                return@setOnClickListener
            }
            syncing = true
            feedback.text = getString(R.string.reserve_syncing)
            refresh()
            scope.launch {
                try {
                    records.syncReserve()
                    feedback.text = getString(R.string.reserve_synced)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: IntervalsKeyRejected) {
                    feedback.text = getString(R.string.reserve_key_rejected)
                } catch (error: IntervalsHttpError) {
                    feedback.text = getString(R.string.reserve_http_error, error.status)
                } catch (error: ReserveSyncError) {
                    feedback.text = getString(error.messageRes)
                } catch (_: Exception) {
                    feedback.text = getString(R.string.reserve_sync_error)
                } finally {
                    syncing = false
                    refresh()
                }
            }
        }
        fun options(title: String, key: String, values: List<Int>, default: Int, suffix: String) {
            val choice = button("")
            fun update() { choice.text = getString(R.string.reserve_option_value, title, prefs.getInt(key, default), suffix) }
            update()
            choice.setOnClickListener {
                AlertDialog.Builder(this@SettingsActivity).setTitle(title)
                    .setSingleChoiceItems(values.map { "$it$suffix" }.toTypedArray(),
                        values.indexOf(prefs.getInt(key, default))) { dialog, index ->
                        prefs.edit().putInt(key, values[index]).apply()
                        update()
                        dialog.dismiss()
                    }.setNegativeButton(R.string.settings_cancel, null).show()
            }
            addView(choice)
        }
        addView(caption("Réserve").apply {
            setTypeface(null, Typeface.BOLD)
            textSize = 18f
        })
        addView(source)
        addView(manual)
        addView(parameterSummary)
        addView(sync)
        addView(feedback)
        addView(caption(getString(R.string.reserve_display)).apply { setTypeface(null, Typeface.BOLD) })
        addView(Switch(this@SettingsActivity).apply {
            text = getString(R.string.reserve_dynamic_colors)
            isChecked = settings.colors
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("reserve_colors", checked).apply() }
        })
        options(getString(R.string.reserve_graph_duration), "reserve_graph", listOf(3, 5, 10, 15), 5, " min")
        options(getString(R.string.reserve_power_average), "reserve_average", listOf(3, 10, 30), 10, " s")
        options(getString(R.string.reserve_low_threshold), "reserve_threshold", (5..50 step 5).toList(), 20, "%")
        val resetFeedback = caption("").apply { visibility = View.GONE }
        resetSection.addView(button(getString(R.string.reserve_reset)).apply {
            setOnClickListener {
                AlertDialog.Builder(this@SettingsActivity)
                    .setMessage(R.string.reserve_reset_confirm)
                    .setNegativeButton(R.string.settings_cancel, null)
                    .setPositiveButton(R.string.reserve_reset) { _, _ ->
                        if (ReserveMonitor.rideInProgress) {
                            settings.requestReset()
                            resetFeedback.setText(R.string.reserve_reset_requested)
                            resetFeedback.visibility = View.VISIBLE
                        }
                    }.show()
            }
        })
        resetSection.addView(resetFeedback)
        addView(resetSection)
        addView(caption(getString(R.string.reserve_model)))
        addView(caption(getString(R.string.reserve_explanation)))
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key?.startsWith("intervals_cache_reserve_") == true || key == INTERVALS_API_KEY ||
                key == INTERVALS_KEY_REJECTED) refresh()
        }
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                prefs.registerOnSharedPreferenceChangeListener(listener)
                refresh()
            }
            override fun onViewDetachedFromWindow(view: View) = prefs.unregisterOnSharedPreferenceChangeListener(listener)
        })
        scope.launch {
            ReserveMonitor.activeRide.collect { active ->
                if (!active) resetFeedback.visibility = View.GONE
                refresh()
            }
        }
        refresh()
    }

    private fun pedalSettings() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val titles = listOf(R.string.pedal_classic, R.string.pedal_performance, R.string.pedal_dynamics)
        val descriptions = listOf(R.string.pedal_classic_detail, R.string.pedal_performance_detail,
            R.string.pedal_dynamics_detail)
        var layout = PedalLayout.from(prefs.getString(PEDAL_LAYOUT_KEY, null))
        val detail = label(descriptions[layout.ordinal])
        val balance = Switch(this@SettingsActivity).apply {
            setText(R.string.settings_balance)
            minHeight = dp(48)
            isChecked = prefs.getBoolean("cadence_balance", false)
        }
        val warning = Button(this@SettingsActivity).apply {
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
        }
        val picker = Button(this@SettingsActivity)
        fun refresh() {
            picker.text = getString(R.string.pedal_layout_value, getString(titles[layout.ordinal]))
            detail.setText(descriptions[layout.ordinal])
            balance.visibility = if (layout == PedalLayout.CLASSIC) View.VISIBLE else View.GONE
            warning.visibility = if (layout != PedalLayout.CLASSIC || balance.isChecked)
                View.VISIBLE else View.GONE
        }
        picker.setOnClickListener {
            AlertDialog.Builder(this@SettingsActivity)
                .setTitle(R.string.pedal_layout_title)
                .setSingleChoiceItems(titles.map { getString(it) }.toTypedArray(), layout.ordinal) { dialog, index ->
                    layout = PedalLayout.entries[index]
                    prefs.edit().putString(PEDAL_LAYOUT_KEY, layout.key).apply()
                    refresh()
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.settings_cancel, null)
                .show()
        }
        balance.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean("cadence_balance", enabled).apply()
            refresh()
        }
        addView(picker)
        addView(detail)
        addView(smoothingButton("cadence", R.string.field_6))
        addView(balance)
        addView(warning)
        refresh()
    }

    private fun smoothingButton(field: String, fieldTitle: Int) = Button(this).apply {
        val key = smoothingKey(field)
        val options = smoothingOptions.map { seconds ->
            if (seconds == 1) getString(R.string.settings_smoothing_live)
            else getString(R.string.records_seconds, seconds)
        }.toTypedArray()
        var selected = smoothingOptions.indexOf(prefs.getInt(key, 1)).coerceAtLeast(0)
        text = getString(R.string.settings_smoothing_value, getString(fieldTitle), options[selected])
        setOnClickListener {
            AlertDialog.Builder(this@SettingsActivity)
                .setTitle(fieldTitle)
                .setSingleChoiceItems(options, selected) { dialog, index ->
                    selected = index
                    prefs.edit().putInt(key, smoothingOptions[index]).apply()
                    text = getString(R.string.settings_smoothing_value, getString(fieldTitle), options[index])
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.settings_cancel, null)
                .show()
        }
    }

    private fun showPowerDurationDialog() {
        val selected = selectedPowerDurations(prefs.getStringSet(POWER_BEST_DURATIONS, null))
        val checked = powerDurations.map { it in selected }.toBooleanArray()
        val labels = powerDurations.map(::durationLabel).toTypedArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.power_best_durations)
            .setMultiChoiceItems(labels, checked) { popup, position, enabled ->
                checked[position] = enabled
                (popup as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = checked.any { it }
            }
            .setNegativeButton(R.string.settings_cancel, null)
            .setPositiveButton(R.string.settings_apply) { _, _ ->
                prefs.edit().putStringSet(POWER_BEST_DURATIONS,
                    powerDurations.filterIndexed { index, _ -> checked[index] }.map { it.toString() }.toSet()).apply()
                updateRecordStatus()
            }
            .create()
        dialog.show()
    }

    private fun showComparisonPeriodDialog() {
        val options = ComparisonPeriod.entries
        val previous = records.comparisonPeriods
        val checked = options.map { it in previous }.toBooleanArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.records_period)
            .setMultiChoiceItems(options.map { getString(it.title) }.toTypedArray(), checked) { dialog, index, selected ->
                val alert = dialog as AlertDialog
                if (selected && checked.indices.count { checked[it] && it != index } >= ComparisonPeriod.MAX_SELECTION) {
                    checked[index] = false
                    alert.listView.setItemChecked(index, false)
                    Toast.makeText(this, R.string.records_period_limit, Toast.LENGTH_SHORT).show()
                } else {
                    checked[index] = selected
                }
                alert.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = checked.any { it }
            }
            .setNegativeButton(R.string.settings_cancel, null)
            .setPositiveButton(R.string.settings_apply) { _, _ ->
                val selection = options.filterIndexed { index, _ -> checked[index] }
                if (selection != previous) {
                    prefs.edit().putString(RECORD_PERIOD, selection.joinToString(",") { it.id }).apply()
                    syncRecords()
                }
            }
            .show()
    }

    private fun durationLabel(duration: Int): String =
        if (duration < 60) getString(R.string.records_seconds, duration)
        else getString(R.string.records_minutes, duration / 60)

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
        var keyVisible = false
        val toggleVisibility = ImageButton(this, null, android.R.attr.borderlessButtonStyle).apply {
            imageTintList = input.textColors
            fun updateIcon() {
                setImageResource(if (keyVisible) R.drawable.ic_key_visible else R.drawable.ic_key_hidden)
                contentDescription = getString(if (keyVisible)
                    R.string.records_hide_api_key else R.string.records_show_api_key)
            }
            updateIcon()
            setOnClickListener {
                keyVisible = !keyVisible
                val start = input.selectionStart.coerceIn(0, input.length())
                val end = input.selectionEnd.coerceIn(0, input.length())
                input.setSingleLine(!keyVisible)
                input.maxLines = if (keyVisible) 4 else 1
                input.transformationMethod = if (keyVisible) null else PasswordTransformationMethod.getInstance()
                input.setSelection(start, end)
                updateIcon()
            }
        }
        val replacing = records.connected
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, dp(24), dp(8))
            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(toggleVisibility, LinearLayout.LayoutParams(dp(48), dp(48)))
            })
            addView(label(R.string.records_key_help))
            if (replacing) {
                addView(label(R.string.records_replace_help).apply { setPadding(0, dp(8), 0, 0) })
            }
        }
        val dialog = createInputDialog(R.string.records_api_key, content, secure = true)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val apiKey = input.text.toString().trim()
                if (apiKey.isEmpty()) {
                    input.error = getString(R.string.records_key_required)
                } else {
                    input.text.clear()
                    dialog.dismiss()
                    runRecordAction {
                        records.connect(apiKey)
                        updateRecordStatus(R.string.records_working, busy = true)
                        records.sync(force = true)
                    }
                }
            }
        }
        dialog.show()
    }

    @Suppress("DEPRECATION")
    private fun createInputDialog(title: Int, content: View, secure: Boolean = false): AlertDialog {
        return AlertDialog.Builder(this)
            .setTitle(title)
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton(R.string.settings_cancel, null)
            .setPositiveButton(R.string.settings_save, null)
            .create().apply {
                window?.apply {
                    if (secure) addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
                }
            }
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
            } catch (error: IntervalsHttpError) {
                updateRecordStatus(feedback = getString(R.string.records_http_error, error.status))
            } catch (_: org.json.JSONException) {
                updateRecordStatus(R.string.records_data_error)
            } catch (_: java.io.IOException) {
                updateRecordStatus(R.string.records_network_error)
            } catch (_: Exception) {
                updateRecordStatus(R.string.records_error)
            }
        }
    }

    private fun updateRecordStatus(message: Int? = null, busy: Boolean = false, feedback: String? = null) {
        val connected = records.connected
        val needsKey = records.keyRejected
        val periods = records.comparisonPeriods
        periodButton.text = getString(R.string.records_period_value,
            periods.joinToString(" · ") { getString(it.shortTitle) })
        connectButton.setText(if (connected) R.string.records_update_key else R.string.records_connect)
        connectButton.isEnabled = !busy
        referenceConnection.visibility = if (!connected || needsKey) View.VISIBLE else View.GONE
        referenceConnection.setText(if (needsKey) R.string.records_update_key else R.string.records_connect)
        recordDetails.visibility = if (connected) View.VISIBLE else View.GONE
        savedReferences.visibility = recordDetails.visibility
        syncButton.isEnabled = !busy && !needsKey
        recordHeart.isEnabled = connected
        recordSound.isEnabled = recordHeart.isEnabled
        disconnectButton.visibility = if (connected) View.VISIBLE else View.GONE
        disconnectButton.isEnabled = !busy
        val showConnected = connected && !needsKey
        recordStatus.setTextColor(if (showConnected)
            ColorStateList.valueOf(getColor(R.color.connected_status)) else defaultRecordStatusColors)
        recordStatus.text = when {
            needsKey -> getString(R.string.records_connection_needs_key)
            !connected -> getString(R.string.records_not_connected)
            records.accountName != null -> getString(R.string.records_connected_as, records.accountName)
            else -> getString(R.string.records_connected)
        }
        recordFeedback.text = feedback ?: message?.let { getString(it) }.orEmpty()
        recordFeedback.visibility = if (recordFeedback.text.isEmpty()) View.GONE else View.VISIBLE
        referenceFeedback.text = recordFeedback.text
        referenceFeedback.visibility = recordFeedback.visibility
        recordDates.text = periods.joinToString("\n") { period ->
            val syncedAt = records.syncedAt(period.id)
            val detail = if (syncedAt == 0L) getString(R.string.records_pending) else Date(syncedAt).let {
                getString(R.string.records_last_sync,
                    DateFormat.getDateFormat(this).format(it), DateFormat.getTimeFormat(this).format(it))
            }
            if (periods.size > 1) "${getString(period.shortTitle)} · $detail" else detail
        }
        recordValues.removeAllViews()
        if (periods.none { records.syncedAt(it.id) > 0 }) return
        for (duration in selectedPowerDurations(prefs.getStringSet(POWER_BEST_DURATIONS, null))) {
            val title = durationLabel(duration)
            val valueText = periods.joinToString("\n") { period ->
                val value = records.reference(period.id, duration)
                val text = when {
                    value == null -> getString(R.string.records_no_value)
                    else -> getString(R.string.records_watts, value)
                }
                if (periods.size > 1) "${getString(period.shortTitle)} · $text" else text
            }
            recordValues.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(28)
                addView(TextView(this@SettingsActivity).apply { text = title },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(this@SettingsActivity).apply {
                    text = valueText
                    setTypeface(typeface, Typeface.BOLD)
                })
            })
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

internal fun resetExtensionSettings(prefs: SharedPreferences) {
    val keys = listOf(
        "theme", "zone_colors", "cadence_balance", PEDAL_LAYOUT_KEY, BALANCE_WARNING_KEY,
        RECORD_PERIOD, RECORD_HEART, RECORD_SOUND, POWER_BEST_DURATIONS,
        "reserve_manual", "reserve_cp", "reserve_kj", "reserve_colors",
        "reserve_graph", "reserve_average", "reserve_threshold"
    )
    val editor = prefs.edit()
    keys.forEach { editor.remove(it) }
    prefs.all.keys.filter { it.startsWith("smoothing_") }.forEach { editor.remove(it) }
    editor.apply()
}
