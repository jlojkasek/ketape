package de.jannes.stacked

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.util.TypedValue
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
        val width = (config.viewSize.first - 6 * density).coerceAtLeast(1f)
        val height = (config.viewSize.second - 4 * density).coerceAtLeast(1f)
        val showHeader = height >= 48 * density && width >= 100 * density
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
                val durations = if (config.preview || prefs.getBoolean(RECORD_HEART, false))
                    powerDurations + HEART_RECORD else powerDurations
                val periods = monitor.comparisonPeriods()
                val page = (SystemClock.elapsedRealtime() - started) / if (config.preview) 2200L else 4000L
                val visible = comparisonPage(durations, rowCapacity, page)
                val ride = if (config.preview) previewRide else monitor.rideBestSnapshot()
                val view = RemoteViews(context.packageName, R.layout.best_comparison)
                view.setImageViewResource(R.id.field_background, if (config.boundariesEnabled)
                    R.drawable.field_background_rounded else android.R.color.white)
                view.setInt(R.id.field_background, "setColorFilter", background)
                view.removeAllViews(R.id.comparison_rows)
                val rowHeight = height / (minOf(rowCapacity, durations.size) + if (showHeader) 1 else 0)
                val referenceTitles = periods.map { period ->
                    context.getString(if (period == "42d") R.string.comparison_42_days else R.string.records_season_short)
                }

                fun addRow(metric: String, current: String, references: List<String>, header: Boolean = false) {
                    val row = RemoteViews(context.packageName, R.layout.best_comparison_row)
                    val ids = listOf(R.id.comparison_metric, R.id.comparison_ride, R.id.comparison_reference,
                        R.id.comparison_second_reference).take(2 + references.size)
                    row.setViewVisibility(R.id.comparison_second_reference,
                        if (references.size == 2) android.view.View.VISIBLE else android.view.View.GONE)
                    val texts = listOf(metric, current) + references
                    val totalWeight = 1f + (ids.size - 1) * 1.25f
                    val baseSize = minOf(rowHeight * if (header) 0.48f else 0.58f, 34 * density)
                    val size = ids.indices.fold(baseSize) { available, index ->
                        paint.typeface = if (index == 1) bold else normal
                        paint.textSize = baseSize
                        val required = paint.measureText(texts[index]).coerceAtLeast(1f)
                        minOf(available, baseSize * (width * (if (index == 0) 1f else 1.25f) / totalWeight - 2).coerceAtLeast(1f) / required)
                    }
                    for (index in ids.indices) {
                        row.setTextViewText(ids[index], texts[index])
                        row.setTextColor(ids[index], foreground)
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
                    view.addView(R.id.comparison_rows, row)
                }

                if (showHeader) addRow(context.getString(R.string.comparison_best),
                    context.getString(R.string.comparison_ride), referenceTitles, header = true)
                for (duration in visible) {
                    val metric = when (duration) {
                        HEART_RECORD -> context.getString(R.string.comparison_heart)
                        1, 5 -> context.getString(R.string.records_seconds, duration)
                        else -> context.getString(R.string.records_minutes, duration / 60)
                    }
                    val unit = if (duration == HEART_RECORD) R.string.records_bpm else R.string.records_watts
                    val current = ride[duration]?.let { context.getString(unit, it) } ?: "–"
                    val references = periods.map { period ->
                        val value = if (config.preview) {
                            (if (period == "42d") previewRecent else previewReference)[duration]
                        } else records.reference(period, duration)
                        value?.let { context.getString(unit, it) } ?: "–"
                    }
                    addRow(metric, current, references)
                }
                emitter.updateView(view)
                delay(1100)
            }
        }
    }

    private companion object {
        val previewRide = mapOf(1 to 961, 5 to 832, 60 to 435, 300 to 308, 1200 to 252, HEART_RECORD to 177)
        val previewRecent = mapOf(1 to 995, 5 to 860, 60 to 453, 300 to 317, 1200 to 265, HEART_RECORD to 185)
        val previewReference = mapOf(1 to 1058, 5 to 901, 60 to 477, 300 to 332, 1200 to 281, HEART_RECORD to 191)
    }
}
