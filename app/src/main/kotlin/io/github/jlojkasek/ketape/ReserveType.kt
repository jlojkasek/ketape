package io.github.jlojkasek.ketape

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.RemoteViews
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.ceil

internal enum class ReserveField(val id: String) {
    BALANCE("reserve-balance"), AVAILABLE("reserve-available-power"), INSIGHTS("reserve-insights")
}

internal class ReserveType(private val field: ReserveField, private val monitor: ReserveMonitor) :
    DataTypeImpl("ketape", field.id) {
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        emitter.setCancellable { scope.cancel() }
        val settings = ReservePreferences(context)
        scope.launch {
            if (config.preview) {
                val parameters = ReserveParameters(300.0, 22_000.0, "Preview CP")
                val engine = ReserveEngine(parameters)
                var second = 0
                while (isActive) {
                    repeat(10) {
                        val power = when (second % 350) {
                            in 0..29 -> 260.0
                            in 30..149 -> 470.0
                            else -> 145.0
                        }
                        engine.sample(second++.toDouble(), power)
                    }
                    emitter.updateView(ReserveRenderer(context, config, settings).render(
                        field, engine.snapshot(second - 1.0, settings.average, false)))
                    delay(1100)
                }
            } else {
                // Settings and theme must also refresh while paused or idle. Never exceed SDK 1 Hz.
                var previous: Any? = null
                while (isActive) {
                    val snapshot = monitor.snapshots.value
                    val frame = Triple(snapshot, settings.displayKey(), context.resources.configuration.uiMode)
                    if (frame != previous) {
                        emitter.updateView(ReserveRenderer(context, config, settings).render(field, snapshot))
                        previous = frame
                    }
                    delay(1100)
                }
            }
        }
    }
}

internal class ReserveRenderer(private val context: Context, config: ViewConfig, private val settings: ReservePreferences) {
    private val packageName = context.packageName
    private val metrics = context.resources.displayMetrics
    private val density = metrics.density
    private val width = config.viewSize.first / density
    private val height = config.viewSize.second / density
    private val tier = when {
        height >= 129 && width >= 148 -> 3
        height >= 80 && width >= 205 -> 2
        height >= 71 && width >= 125 -> 1
        else -> 0
    }
    private val theme = settings.prefs.getString("theme", "auto")
    private val dark = theme == "dark" || theme == "auto" &&
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val foreground = if (dark) Color.WHITE else Color.rgb(17, 17, 17)
    private val track = if (dark) Color.DKGRAY else Color.LTGRAY
    private val gravity = when (config.alignment) {
        ViewConfig.Alignment.LEFT -> Gravity.START
        ViewConfig.Alignment.CENTER -> Gravity.CENTER_HORIZONTAL
        ViewConfig.Alignment.RIGHT -> Gravity.END
    }
    private val palette = listOf(0xff2779ad, 0xff4599af, 0xff777b8e, 0xffbd8126, 0xffb65a31).map(Long::toInt)
    private val view = RemoteViews(packageName, R.layout.reserve).apply {
        removeAllViews(R.id.reserve_rows)
        setImageViewResource(R.id.reserve_background,
            if (config.boundariesEnabled) R.drawable.field_background_rounded else android.R.color.white)
        setInt(R.id.reserve_background, "setColorFilter", if (dark) Color.BLACK else Color.WHITE)
    }

    fun render(field: ReserveField, snapshot: ReserveSnapshot): RemoteViews {
        val totals = snapshot.totals
        val parameters = snapshot.parameters
        if (totals == null || parameters == null) {
            row("Réserve", 14f, true)
            row(snapshot.status.displayLabel(context))
            return view
        }
        val percent = requireNotNull(snapshot.percent)
        val levelColor = when {
            !settings.colors -> foreground
            percent < settings.threshold -> palette.last()
            percent >= 80 -> palette[0]
            percent >= 50 -> palette[1]
            percent >= 25 -> palette[2]
            else -> palette[3]
        }
        val warning = when {
            snapshot.restored -> " ⚠"
            percent < settings.threshold -> " !"
            else -> ""
        }
        val interruption = snapshot.status == ReserveStatus.PAUSED || snapshot.status == ReserveStatus.MISSING
        when (field) {
            ReserveField.BALANCE -> {
                val amount = when {
                    tier >= 2 -> "  ${number(totals.balance / 1000)} / ${number(parameters.capacity / 1000)} kJ"
                    tier >= 1 -> "  ${kj(totals.balance)}"
                    else -> ""
                }
                row("${percent.roundToInt()}%$warning ${snapshot.status.symbol}$amount",
                    if (tier == 1) 17f else 20f, true)
                bar(percent, levelColor)
                if (tier >= 1) {
                    val delta = snapshot.delta?.roundToInt()?.let { if (it > 0) "+$it W" else "$it W" }
                    val power = snapshot.power?.roundToInt()?.let { "$it W" }
                    val forecast = snapshot.forecast?.let {
                        val label = if (snapshot.status == ReserveStatus.DEPLETING) context.getString(R.string.reserve_empty) else context.getString(R.string.reserve_to_ninety)
                        "$label ${duration(it, roundUp = true)}"
                    }
                    row(listOfNotNull(if (tier >= 2) power else null, delta,
                        forecast ?: snapshot.status.displayLabel(context)).joinToString("  "))
                }
                if (tier == 0 && interruption && height >= 40) row(snapshot.status.displayLabel(context), 10f)
                if (tier >= 3) {
                    val rate = snapshot.rate?.let { "  ${number(it)} kJ/min" }.orEmpty()
                    row("${snapshot.status.displayLabel(context)}$rate  ·  Min ${(totals.minimum * 100 / parameters.capacity).roundToInt()}%")
                    history(snapshot.history, levelColor)
                    row(reference(parameters))
                }
            }
            ReserveField.AVAILABLE -> {
                row(if (tier == 0) "${percent.roundToInt()}%$warning ${snapshot.status.symbol} · ${context.getString(R.string.reserve_power_short)}"
                    else "${percent.roundToInt()}%$warning · ${context.getString(R.string.reserve_power_long)}", if (tier == 0) 12f else 16f, true)
                bar(percent, levelColor)
                val minutes = when (tier) {
                    0 -> listOf(1, 5)
                    1 -> listOf(1, 3, 5)
                    2 -> listOf(1, 3, 5, 10)
                    else -> listOf(1, 2, 3, 5, 10, 20)
                }
                minutes.chunked(if (tier >= 2 && width < 300) 2 else 3).forEach { group ->
                    row(group.joinToString(if (tier <= 1) "  " else "   ") { minute ->
                        val value = snapshot.available(minute * 60)?.roundToInt()?.let { "$it W" } ?: "–"
                        "${minute}m $value"
                    }, when (tier) { 0 -> 10f; 1 -> 11f; else -> 14f }, true)
                }
                if (tier >= 2) row("${reference(parameters)}  ·  ${kj(totals.balance)}")
            }
            ReserveField.INSIGHTS -> {
                val min = (totals.minimum * 100 / parameters.capacity).roundToInt()
                row("Min $min%$warning ${snapshot.status.symbol}" + if (tier >= 1) "  ${kj(totals.minimum)}" else "", 16f, true)
                val expended = if (tier == 0) context.getString(R.string.reserve_spent_short) else context.getString(R.string.reserve_spent_long)
                row("$expended ${kj(totals.expended)}")
                if (tier >= 1) {
                    val average = if (totals.elapsed > 0)
                        "${(totals.integral * 100 / totals.elapsed / parameters.capacity).roundToInt()}%" else "–"
                    row("${context.getString(R.string.reserve_average_label)} $average")
                }
                if (tier >= 2) {
                    val share = if (totals.elapsed > 0) "  ${(totals.above / totals.elapsed * 100).roundToInt()}%" else ""
                    row("${context.getString(R.string.reserve_above_reference)} ${duration(totals.above)}$share")
                }
                if (tier >= 3) {
                    row("${context.getString(R.string.reserve_equivalents)} ${number(totals.expended / parameters.capacity)}×")
                    row(context.getString(R.string.reserve_levels), 10f)
                    chart(5, context.getString(R.string.reserve_level_description,
                        totals.levels.joinToString { duration(it) })) { canvas, paint ->
                        var x = 0f
                        paint.color = track
                        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
                        if (totals.elapsed > 0) totals.levels.forEachIndexed { index, seconds ->
                            val end = x + (seconds / totals.elapsed * canvas.width).toFloat()
                            paint.color = if (settings.colors) palette[index]
                                else Color.rgb(50 + index * 38, 50 + index * 38, 50 + index * 38)
                            canvas.drawRect(x, 0f, end, canvas.height.toFloat(), paint)
                            paint.color = if (dark) Color.BLACK else Color.WHITE
                            if (end - x >= 2f && x > 0) canvas.drawRect(x, 0f, x + 1, canvas.height.toFloat(), paint)
                            x = end
                        }
                    }
                }
            }
        }
        if (snapshot.restored && tier >= 1) row(context.getString(R.string.reserve_continuity), 10f)
        else if (interruption && field != ReserveField.BALANCE && tier >= 1) row(snapshot.status.displayLabel(context), 10f)
        return view
    }

    private fun row(text: String, size: Float = 12f, bold: Boolean = false) {
        val cell = RemoteViews(packageName, R.layout.reserve_text)
        cell.setTextViewText(R.id.reserve_text, text)
        cell.setTextColor(R.id.reserve_text, foreground)
        val measure = Paint().apply {
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, metrics)
        }
        val measured = measure.measureText(text)
        val available = (width - 14) * density
        val fitted = if (measured > available) (size * available / measured).coerceIn(9f, size) else size
        cell.setTextViewTextSize(R.id.reserve_text, TypedValue.COMPLEX_UNIT_SP, fitted)
        cell.setInt(R.id.reserve_text, "setGravity", gravity)
        if (bold) cell.setInt(R.id.reserve_text, "setPaintFlags", Paint.ANTI_ALIAS_FLAG or Paint.FAKE_BOLD_TEXT_FLAG)
        view.addView(R.id.reserve_rows, cell)
    }

    private fun bar(percent: Double, color: Int) = chart(5, context.getString(R.string.reserve_bar_description, percent.roundToInt())) { canvas, paint ->
        paint.color = track
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
        paint.color = color
        canvas.drawRect(0f, 0f, (percent.coerceIn(0.0, 100.0) / 100 * canvas.width).toFloat(),
            canvas.height.toFloat(), paint)
    }

    private fun history(points: List<ReservePoint>, color: Int) {
        val latest = points.lastOrNull() ?: return
        val span = settings.graph * 60.0
        chart(29, context.getString(R.string.reserve_history_description, settings.graph)) { canvas, paint ->
            val w = canvas.width - 1f
            val h = canvas.height - 3f
            paint.color = track
            canvas.drawLine(0f, 1f + h / 2, w, 1f + h / 2, paint)
            canvas.drawLine(0f, 1f + h, w, 1f + h, paint)
            paint.color = color
            paint.strokeWidth = 2f
            fun x(point: ReservePoint) = (w * (1 - (latest.at - point.at) / span)).toFloat()
            fun y(point: ReservePoint) = 1f + (h * (1 - point.percent.coerceIn(0.0, 100.0) / 100)).toFloat()
            points.zipWithNext().forEach { (a, b) ->
                if (a.segment == b.segment && x(b) >= 0) canvas.drawLine(x(a), y(a), x(b), y(b), paint)
            }
            canvas.drawCircle(w - 2, y(latest), 2f, paint)
        }
    }

    private fun chart(height: Int, description: String, draw: (Canvas, Paint) -> Unit) {
        val bitmap = Bitmap.createBitmap(((width - 12) * density).roundToInt().coerceIn(1, 480),
            (height * density).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        bitmap.density = (density * 160).roundToInt()
        draw(Canvas(bitmap), Paint(Paint.ANTI_ALIAS_FLAG))
        val cell = RemoteViews(packageName, R.layout.reserve_chart)
        cell.setImageViewBitmap(R.id.reserve_chart, bitmap)
        cell.setContentDescription(R.id.reserve_chart, description)
        view.addView(R.id.reserve_rows, cell)
    }

    private fun reference(p: ReserveParameters) = "${p.displaySource(context)} ${p.watts.roundToInt()} W"
    private fun number(value: Double) = String.format(Locale.getDefault(), "%.1f", value)
    private fun kj(joules: Double) = "${number(joules / 1000)} kJ"
    private fun duration(seconds: Double, roundUp: Boolean = false): String {
        val whole = if (roundUp) ceil(seconds).toInt() else seconds.toInt()
        return String.format(Locale.US, "%d:%02d", whole / 60, whole % 60)
    }
}
