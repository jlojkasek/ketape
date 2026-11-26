package io.github.jlojkasek.ketape

import android.content.Context
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
                val foreground = context.getColor(R.color.foreground)
                val periods = records.comparisonPeriods
                val ride = if (config.preview) PreviewValues.previewRide else monitor.rideBestSnapshot()
                val incomplete = !config.preview && monitor.incompleteRide
                val mainText = (ride[duration]?.toString() ?: "–") + if (incomplete) "*" else ""
                val view = RemoteViews(context.packageName, R.layout.stacked)
                view.setImageViewResource(R.id.field_background, background)
                view.setInt(R.id.field_background, "setColorFilter", context.getColor(R.color.background))
                view.setInt(R.id.main_row, "setGravity", gravity)
                view.setViewVisibility(R.id.icon, View.GONE)
                view.setViewVisibility(R.id.duration_icon, View.VISIBLE)
                view.setTextViewText(R.id.duration_icon, powerBestDurationIcon(duration))
                view.setContentDescription(R.id.duration_icon, durationText)
                view.setTextViewText(R.id.main, mainText)
                view.setContentDescription(R.id.main, if (incomplete)
                    context.getString(R.string.ride_since_restart, mainText) else mainText)
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
                    val value = if (config.preview) {
                        PreviewValues.previewBenchmark(period, duration)
                    } else records.reference(period.id, duration)
                    val label = context.getString(period.badge)
                    val fullLabel = context.getString(period.shortTitle)
                    val cell = RemoteViews(context.packageName, R.layout.power_best_reference)
                    cell.setTextViewText(R.id.reference_badge, label)
                    val stale = !config.preview && value != null && records.stale(period.id)
                    cell.setTextViewText(R.id.reference_value,
                        if (value == null) "–" else if (stale) "$value†" else value.toString())
                    cell.setTextColor(R.id.reference_badge, foreground)
                    cell.setTextColor(R.id.reference_value, foreground)
                    val reference = value?.let { context.getString(R.string.records_watts, it) } ?: "–"
                    cell.setContentDescription(R.id.reference_value, context.getString(
                        if (stale) R.string.comparison_accessible_stale_reference else R.string.comparison_accessible_reference,
                        fullLabel, reference))
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
