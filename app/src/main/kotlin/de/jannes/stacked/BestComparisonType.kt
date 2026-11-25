package de.jannes.stacked

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
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
    DataTypeImpl("stacked-fields", "best-comparison") {

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
                val theme = prefs.getString("theme", "auto")
                val dark = theme == "dark" || (theme == "auto" &&
                    context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES)
                val foreground = if (dark) Color.WHITE else Color.rgb(17, 17, 17)
                val background = if (dark) Color.BLACK else Color.WHITE
                val durations = powerDurations
                val periods = monitor.comparisonPeriods()
                val page = (SystemClock.elapsedRealtime() - started) / if (config.preview) 2200L else 4000L
                val visible = if (fullPage) durations else comparisonPage(durations, rowCapacity, page)
                val ride = if (config.preview) PreviewValues.previewRide else monitor.rideBestSnapshot()
                val view = RemoteViews(context.packageName, R.layout.best_comparison)
                view.setImageViewResource(R.id.field_background, if (config.boundariesEnabled)
                    R.drawable.field_background_rounded else android.R.color.white)
                view.setInt(R.id.field_background, "setColorFilter", background)
                view.removeAllViews(R.id.comparison_rows)
                view.removeAllViews(R.id.comparison_header)
                if (fullPage) {
                    val inset = (padding * density).toInt()
                    view.setViewPadding(R.id.comparison_content, inset, inset, inset, inset)
                    view.setViewVisibility(R.id.comparison_title, View.VISIBLE)
                    view.setViewVisibility(R.id.comparison_header, View.VISIBLE)
                    view.setTextViewText(R.id.comparison_title, context.getString(R.string.power_best_table))
                    view.setTextColor(R.id.comparison_title, foreground)
                }
                val rowHeight = if (fullPage) {
                    (config.viewSize.second - (70 + 2 * padding) * density).coerceAtLeast(1f) / durations.size
                } else height / (minOf(rowCapacity, durations.size) + if (showHeader) 1 else 0)
                val muted = if (dark) Color.rgb(178, 189, 200) else Color.rgb(67, 80, 93)
                val stripe = if (dark) Color.rgb(24, 32, 40) else Color.rgb(235, 240, 245)
                val referenceTitles = periods.map { period ->
                    context.getString(if (period == "42d") R.string.comparison_42_days else R.string.records_season_short)
                }

                fun addRow(metric: String, current: String, references: List<String>, header: Boolean = false, shaded: Boolean = false) {
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
                        row.setContentDescription(R.id.comparison_ride,
                            context.getString(R.string.comparison_accessible_ride, current))
                        references.forEachIndexed { index, reference ->
                            row.setContentDescription(ids[index + 2], context.getString(
                                R.string.comparison_accessible_reference, referenceTitles[index], reference))
                        }
                    }
                    view.addView(if (fullPage && header) R.id.comparison_header else R.id.comparison_rows, row)
                }

                if (showHeader) addRow(context.getString(if (fullPage)
                    R.string.comparison_power_unit else R.string.comparison_best),
                    context.getString(R.string.comparison_ride), referenceTitles, header = true)
                for ((index, duration) in visible.withIndex()) {
                    val metric = when (duration) {
                        1, 5 -> context.getString(R.string.records_seconds, duration)
                        else -> context.getString(R.string.records_minutes, duration / 60)
                    }
                    fun valueText(value: Int?) = when {
                        value == null -> "–"
                        fullPage -> value.toString()
                        else -> context.getString(R.string.records_watts, value)
                    }
                    val current = valueText(ride[duration])
                    val references = periods.map { period ->
                        val value = if (config.preview) {
                            (if (period == "42d") PreviewValues.previewRecent else PreviewValues.previewReference)[duration]
                        } else records.reference(period, duration)
                        valueText(value)
                    }
                    addRow(metric, current, references, shaded = index % 2 == 1)
                }
                emitter.updateView(view)
                delay(1100)
            }
        }
    }

}
