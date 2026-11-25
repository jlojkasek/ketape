package io.github.jlojkasek.ketape

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.*

internal class PowerBestType(private val monitor: RideRecordMonitor) :
    DataTypeImpl("ketape", "palmares-auto") {

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        emitter.setCancellable { scope.cancel() }
        val prefs = context.getSharedPreferences("stacked", Context.MODE_PRIVATE)
        val records = IntervalsRecords(context)
        val started = SystemClock.elapsedRealtime()
        val gravity = when (config.alignment) {
            ViewConfig.Alignment.LEFT -> Gravity.LEFT
            ViewConfig.Alignment.RIGHT -> Gravity.RIGHT
            ViewConfig.Alignment.CENTER -> Gravity.CENTER_HORIZONTAL
        } or Gravity.CENTER_VERTICAL
        val background = if (config.boundariesEnabled)
            R.drawable.field_background_rounded else android.R.color.white

        scope.launch {
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - started
                val interval = if (config.preview) 2200L else 4000L
                val durations = selectedPowerDurations(prefs.getStringSet(POWER_BEST_DURATIONS, null))
                val duration = durations[((elapsed / interval) % durations.size).toInt()]
                val durationText = context.getString(
                    if (duration < 60) R.string.records_seconds else R.string.records_minutes,
                    if (duration < 60) duration else duration / 60
                )
                val theme = prefs.getString("theme", "auto")
                val dark = theme == "dark" || (theme == "auto" &&
                    context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES)
                val foreground = if (dark) Color.WHITE else Color.rgb(17, 17, 17)
                val periods = monitor.comparisonPeriods()
                val ride = if (config.preview) PreviewValues.previewRide else monitor.rideBestSnapshot()
                val mainText = ride[duration]?.toString() ?: "–"
                val view = RemoteViews(context.packageName, R.layout.stacked)
                view.setImageViewResource(R.id.field_background, background)
                view.setInt(R.id.field_background, "setColorFilter", if (dark) Color.BLACK else Color.WHITE)
                view.setInt(R.id.main_row, "setGravity", gravity)
                view.setViewVisibility(R.id.icon, View.GONE)
                view.setViewVisibility(R.id.duration_icon, View.VISIBLE)
                view.setTextViewText(R.id.duration_icon, powerBestDurationIcon(duration))
                view.setContentDescription(R.id.duration_icon, durationText)
                view.setTextViewText(R.id.main, mainText)
                view.setTextViewText(R.id.unit, "W")
                listOf(R.id.duration_icon, R.id.main, R.id.unit).forEach {
                    view.setTextColor(it, foreground)
                }
                MainLineLayout.apply(context, view, config.viewSize.first, config.viewSize.second,
                    config.textSize, mainText, "W")

                view.setViewVisibility(R.id.left, View.GONE)
                view.setViewVisibility(R.id.right, View.GONE)
                view.setViewVisibility(R.id.power_best_references, View.VISIBLE)
                view.removeAllViews(R.id.power_best_references)
                periods.forEach { period ->
                    val recent = period == "42d"
                    val value = if (config.preview) {
                        (if (recent) PreviewValues.previewRecent else PreviewValues.previewReference)[duration]
                    } else records.reference(period, duration)
                    val label = context.getString(if (recent) R.string.power_best_short_recent else R.string.power_best_short_season)
                    val fullLabel = context.getString(if (recent) R.string.comparison_42_days else R.string.records_season_short)
                    val cell = RemoteViews(context.packageName, R.layout.power_best_reference)
                    cell.setTextViewText(R.id.reference_badge, label)
                    cell.setTextViewText(R.id.reference_value, value?.toString() ?: "–")
                    cell.setTextColor(R.id.reference_badge, foreground)
                    cell.setTextColor(R.id.reference_value, foreground)
                    val reference = value?.let { context.getString(R.string.records_watts, it) } ?: "–"
                    cell.setContentDescription(R.id.reference_value, context.getString(
                        R.string.comparison_accessible_reference, fullLabel, reference))
                    view.addView(R.id.power_best_references, cell)
                }
                emitter.updateView(view)
                delay(1100)
            }
        }
    }
}

internal fun powerBestDurationIcon(seconds: Int): String =
    if (seconds < 60) "${seconds}S" else "${seconds / 60}M"
