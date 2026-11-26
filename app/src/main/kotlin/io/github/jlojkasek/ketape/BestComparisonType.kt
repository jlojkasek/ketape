package io.github.jlojkasek.ketape

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.*

internal fun comparisonPage(durations: List<Int>, rows: Int, page: Long): List<Int> {
    val capacity = rows.coerceIn(1, durations.size)
    val pages = (durations.size + capacity - 1) / capacity
    val start = (page % pages).toInt() * capacity
    return durations.drop(start).take(capacity)
}

internal class BestComparisonType(private val monitor: RideRecordMonitor) :
    DataTypeImpl("ketape", "palmares-table") {

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        emitter.setCancellable { scope.cancel() }
        val prefs = context.getSharedPreferences("stacked", Context.MODE_PRIVATE)
        val records = IntervalsRecords(context)
        val density = context.resources.displayMetrics.density
        val fullPage = config.gridSize.first == 60 && config.gridSize.second == 60
        val padding = if (fullPage) 12 else 3
        val width = (config.viewSize.first - 2 * padding * density).coerceAtLeast(1f)
        val height = (config.viewSize.second - 4 * density).coerceAtLeast(1f)
        val showHeader = fullPage || (height >= 48 * density && width >= 100 * density)
        val rowCapacity = ((height - if (showHeader) 18 * density else 0f) / (24 * density))
            .toInt().coerceAtLeast(1)
        val started = SystemClock.elapsedRealtime()
        val paint = Paint()
        val normal = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        val bold = Typeface.create(normal, Typeface.BOLD)

        scope.launch {
            while (isActive) {
                val foreground = context.getColor(R.color.foreground)
                val background = context.getColor(R.color.background)
                val durations = selectedPowerDurations(prefs.getStringSet(POWER_BEST_DURATIONS, null))
                val periods = records.comparisonPeriods
                val page = (SystemClock.elapsedRealtime() - started) / if (config.preview) 2200L else 4000L
                val bodyHeight = (config.viewSize.second - (28 + 2 * padding) * density).coerceAtLeast(1f)
                val capacity = if (fullPage) (bodyHeight / (24 * density)).toInt().coerceAtLeast(1) else rowCapacity
                val visible = comparisonPage(durations, capacity, page)
                val ride = if (config.preview) PreviewValues.previewRide else monitor.rideBestSnapshot()
                val marks = if (config.preview) PreviewValues.previewTableRecords else monitor.tableRecordSnapshot()
                val view = RemoteViews(context.packageName, R.layout.best_comparison)
                view.setImageViewResource(R.id.field_background, if (config.boundariesEnabled)
                    R.drawable.field_background_rounded else android.R.color.white)
                view.setInt(R.id.field_background, "setColorFilter", background)
                view.removeAllViews(R.id.comparison_rows)
                view.removeAllViews(R.id.comparison_header)
                if (fullPage) {
                    val inset = (padding * density).toInt()
                    view.setViewPadding(R.id.comparison_content, inset, inset, inset, inset)
                    view.setViewVisibility(R.id.comparison_header, View.VISIBLE)
                }
                val rowHeight = if (fullPage) {
                    bodyHeight / minOf(capacity, durations.size)
                } else height / (minOf(rowCapacity, durations.size) + if (showHeader) 1 else 0)
                val muted = context.getColor(R.color.comparison_muted)
                val stripe = context.getColor(R.color.comparison_stripe)
                val stale = periods.map { !config.preview && records.stale(it.id) }
                val referenceTitles = periods.map { period ->
                    context.getString(period.shortTitle)
                }

                fun addRow(metric: String, current: String, references: List<String>, header: Boolean = false,
                           shaded: Boolean = false, mark: PowerRecordMark? = null) {
                    val row = RemoteViews(context.packageName, R.layout.best_comparison_row)
                    val ids = listOf(R.id.comparison_metric, R.id.comparison_ride, R.id.comparison_reference,
                        R.id.comparison_second_reference).take(2 + references.size)
                    row.setViewVisibility(R.id.comparison_second_reference,
                        if (references.size == 2) android.view.View.VISIBLE else android.view.View.GONE)
                    val texts = listOf(metric, current) + references
                    val totalWeight = 1f + (ids.size - 1) * 1.25f
                    val baseSize = if (fullPage && header) 13 * density
                        else minOf(rowHeight * if (header) 0.48f else 0.58f, 34 * density)
                    val size = ids.indices.fold(baseSize) { available, index ->
                        paint.typeface = if (index == 1) bold else normal
                        paint.textSize = baseSize
                        val sample = if (fullPage && !header) {
                            if (index == 0) "20 min" else "5000"
                        } else texts[index]
                        val required = maxOf(paint.measureText(sample), paint.measureText(texts[index]), 1f)
                        minOf(available, baseSize * (width * (if (index == 0) 1f else 1.25f) / totalWeight - 2).coerceAtLeast(1f) / required)
                    }
                    if (fullPage && (header || shaded)) {
                        row.setInt(R.id.comparison_row, "setBackgroundColor", stripe)
                    }
                    for (index in ids.indices) {
                        row.setTextViewText(ids[index], texts[index])
                        row.setTextColor(ids[index], if (fullPage && (header || index != 1)) muted else foreground)
                        row.setTextViewTextSize(ids[index], TypedValue.COMPLEX_UNIT_PX, size)
                    }
                    if (!header) {
                        val description = context.getString(if (!config.preview && monitor.incompleteRide)
                            R.string.ride_since_restart else R.string.comparison_accessible_ride, current)
                        val markedDescription = when (mark) {
                            PowerRecordMark.RECENT -> {
                                row.setTextColor(R.id.comparison_ride, context.getColor(R.color.comparison_record_recent))
                                context.getString(R.string.comparison_accessible_recent_record, description)
                            }
                            PowerRecordMark.ALL_TIME -> {
                                row.setTextColor(R.id.comparison_ride, context.getColor(R.color.comparison_record_all_time))
                                context.getString(R.string.comparison_accessible_all_time_record, description)
                            }
                            null -> description
                        }
                        row.setContentDescription(R.id.comparison_ride, markedDescription)
                        references.forEachIndexed { index, reference ->
                            row.setContentDescription(ids[index + 2], context.getString(
                                if (stale[index]) R.string.comparison_accessible_stale_reference else R.string.comparison_accessible_reference,
                                referenceTitles[index], reference.removeSuffix("†")))
                        }
                    }
                    view.addView(if (fullPage && header) R.id.comparison_header else R.id.comparison_rows, row)
                }

                if (showHeader) addRow(context.getString(if (fullPage)
                    R.string.comparison_power_unit else R.string.comparison_best),
                    context.getString(R.string.comparison_ride), referenceTitles.mapIndexed { index, title ->
                        if (stale[index]) "$title†" else title
                    }, header = true)
                for ((index, duration) in visible.withIndex()) {
                    val metric = if (duration < 60) context.getString(R.string.records_seconds, duration)
                        else context.getString(R.string.records_minutes, duration / 60)
                    fun valueText(value: Int?) = when {
                        value == null -> "–"
                        fullPage -> value.toString()
                        else -> context.getString(R.string.records_watts, value)
                    }
                    val mark = marks[duration]
                    val currentValue = if (!config.preview) ride[duration] else when (mark) {
                        PowerRecordMark.RECENT -> PreviewValues.previewRecent[duration]?.plus(5)
                        PowerRecordMark.ALL_TIME -> PreviewValues.previewAllTime[duration]?.plus(2)
                        null -> ride[duration]
                    }
                    val current = valueText(currentValue) +
                        if (!config.preview && monitor.incompleteRide) "*" else ""
                    val references = periods.mapIndexed { referenceIndex, period ->
                        val value = if (config.preview) {
                            PreviewValues.previewBenchmark(period, duration)
                        } else records.reference(period.id, duration)
                        valueText(value) + if (!showHeader && value != null && stale[referenceIndex]) "†" else ""
                    }
                    addRow(metric, current, references, shaded = index % 2 == 1, mark = mark)
                }
                emitter.updateView(view)
                delay(1100)
            }
        }
    }

}
