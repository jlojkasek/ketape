package de.jannes.stacked

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.widget.RemoteViews
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.*
import kotlinx.coroutines.*
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

private data class Metric(val type: String, val format: Format = Format.INTEGER)
private enum class Format { INTEGER, SPEED, DISTANCE, TIME }
private data class Stack(val id: String, val title: String, val unit: String,
    val main: Metric, val left: Metric, val right: Metric?,
    val leftLabel: String = "Ø", val rightLabel: String = "MAX")

private val stacks = listOf(
    Stack("power-1s", "LEISTUNG · 1 s", "W", Metric(DataType.Type.POWER), Metric(DataType.Type.AVERAGE_POWER), Metric(DataType.Type.NORMALIZED_POWER), rightLabel = "NP"),
    Stack("power-3s", "LEISTUNG · 3 s", "W", Metric(DataType.Type.SMOOTHED_3S_AVERAGE_POWER), Metric(DataType.Type.AVERAGE_POWER), Metric(DataType.Type.NORMALIZED_POWER), rightLabel = "NP"),
    Stack("power-5s", "LEISTUNG · 5 s", "W", Metric(DataType.Type.SMOOTHED_5S_AVERAGE_POWER), Metric(DataType.Type.AVERAGE_POWER), Metric(DataType.Type.NORMALIZED_POWER), rightLabel = "NP"),
    Stack("heart", "HERZFREQUENZ", "bpm", Metric(DataType.Type.HEART_RATE), Metric(DataType.Type.AVERAGE_HR), Metric(DataType.Type.MAX_HR)),
    Stack("speed", "GESCHWINDIGKEIT", "km/h", Metric(DataType.Type.SPEED, Format.SPEED), Metric(DataType.Type.AVERAGE_SPEED, Format.SPEED), Metric(DataType.Type.MAX_SPEED, Format.SPEED)),
    Stack("distance", "STRECKE", "km", Metric(DataType.Type.DISTANCE, Format.DISTANCE), Metric(DataType.Type.ELEVATION_GAIN), Metric(DataType.Type.ELEVATION_LOSS), "↗", "↘"),
    Stack("cadence", "TRITTFREQUENZ", "rpm", Metric(DataType.Type.CADENCE), Metric(DataType.Type.AVERAGE_CADENCE), Metric(DataType.Type.MAX_CADENCE))
)

class StackedExtension : KarooExtension("stacked-fields", "0.1.0") {
    private val system by lazy { KarooSystemService(this) }
    override val types: List<DataTypeImpl> by lazy { stacks.map { StackedType(system, it) } }
    override fun onCreate() { super.onCreate(); system.connect() }
    override fun onDestroy() { system.disconnect(); super.onDestroy() }
}

private class StackedType(private val system: KarooSystemService, private val stack: Stack) :
    DataTypeImpl("stacked-fields", stack.id) {

    override fun startStream(emitter: Emitter<StreamState>) {
        val id = system.addConsumer<OnStreamState>(OnStreamState.StartStreaming(stack.main.type)) { event ->
            // Forward only the main numeric value under our own type ID.
            val state = event.state
            emitter.onNext(if (state is StreamState.Streaming) {
                val value = state.dataPoint.singleValue
                if (value != null && value.isFinite()) StreamState.Streaming(
                    DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to value))
                ) else StreamState.NotAvailable
            } else state)
        }
        emitter.setCancellable { system.removeConsumer(id) }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val values = ConcurrentHashMap<String, Double>()
        val timestamps = ConcurrentHashMap<String, Long>()
        val metrics = listOfNotNull(stack.main, stack.left, stack.right)
        val isPower = stack.id.startsWith("power-")
        val supportsZones = isPower || stack.id == "heart"
        val zoneRanges = AtomicReference<List<ZoneRange>>(emptyList())
        val profileId = if (supportsZones && !config.preview) {
            system.addConsumer<UserProfile> { profile ->
                zoneRanges.set((if (isPower) profile.powerZones else profile.heartRateZones)
                    .map { ZoneRange(it.min, it.max) })
            }
        } else null
        val ids = if (config.preview) emptyList() else metrics.map { it.type }.distinct().map { type ->
            system.addConsumer<OnStreamState>(OnStreamState.StartStreaming(type)) { event ->
                val value = (event.state as? StreamState.Streaming)?.dataPoint?.singleValue
                if (value != null && value.isFinite() && value >= 0.0) {
                    values[type] = value
                    timestamps[type] = SystemClock.elapsedRealtime()
                } else { values.remove(type); timestamps.remove(type) }
            }
        }
        // Balance is a multi-value stream: read its explicit LEFT field, never singleValue.
        val balanceType = DataType.Type.PEDAL_POWER_BALANCE
        val balanceId = if (stack.id == "cadence" && !config.preview) {
            system.addConsumer<OnStreamState>(OnStreamState.StartStreaming(balanceType)) { event ->
                val point = (event.state as? StreamState.Streaming)?.dataPoint
                val left = point?.values?.get(DataType.Field.PEDAL_POWER_BALANCE_LEFT)
                val power = point?.values?.get(DataType.Field.POWER)
                if (left != null && left.isFinite() && left in 0.0..100.0 && (power == null || power > 0.0)) {
                    values[balanceType] = left
                    timestamps[balanceType] = SystemClock.elapsedRealtime()
                } else {
                    values.remove(balanceType)
                    timestamps.remove(balanceType)
                }
            }
        } else null
        val prefs = context.getSharedPreferences("stacked", Context.MODE_PRIVATE)
        scope.launch {
            // Load once per view activation, not in the one-second render loop.
            val systemColors = KarooPalette.load(context)
            while (isActive) {
                val theme = prefs.getString("theme", "auto")
                val dark = theme == "dark" || (theme == "auto" &&
                    context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
                val fg = if (dark) Color.WHITE else Color.rgb(17,17,17)
                val bg = if (dark) Color.BLACK else Color.WHITE
                val view = RemoteViews(context.packageName, R.layout.stacked)
                view.setInt(R.id.root, "setBackgroundColor", bg)
                // RemoteViews may be reapplied to an existing view hierarchy.
                view.setInt(R.id.zone, "setBackgroundColor", Color.TRANSPARENT)
                view.setTextColor(R.id.zone, fg)
                val textIds = listOf(R.id.main, R.id.unit, R.id.left, R.id.right)
                textIds.forEach { view.setTextColor(it, fg) }
                val gravity = when (config.alignment) {
                    ViewConfig.Alignment.LEFT -> android.view.Gravity.LEFT
                    ViewConfig.Alignment.RIGHT -> android.view.Gravity.RIGHT
                    else -> android.view.Gravity.CENTER_HORIZONTAL
                } or android.view.Gravity.CENTER_VERTICAL
                textIds.forEach { view.setInt(it, "setGravity", gravity) }
                // Let Android fit the final text inside the measured cell, including
                // icon, unit and zone badge. Fixed sizes compete with native autosizing.
                fun rawValue(metric: Metric, index: Int): Double? =
                    if (config.preview) demo(metric, index) else {
                        val timestamp = timestamps[metric.type]
                        // Never leave a disconnected sensor's last current value on screen.
                        if (metric == stack.main && timestamp != null && SystemClock.elapsedRealtime() - timestamp > 5000) null
                        else values[metric.type]
                    }
                val mainValue = rawValue(stack.main, 0)
                val ranges = if (config.preview && supportsZones) {
                    if (isPower) listOf(ZoneRange(0, 165), ZoneRange(166, 225), ZoneRange(226, 270),
                        ZoneRange(271, 315), ZoneRange(316, 360), ZoneRange(361, 450), ZoneRange(451, 2000))
                    else listOf(ZoneRange(0, 110), ZoneRange(111, 135), ZoneRange(136, 155),
                        ZoneRange(156, 175), ZoneRange(176, 220))
                } else zoneRanges.get()
                val colorMode = prefs.getString("zone_colors", "background")
                val zone = if (supportsZones && colorMode != "off")
                    ZoneColors.resolve(mainValue, ranges, isPower,
                        if (isPower) systemColors.power else systemColors.heart) else null
                if (zone != null) {
                    if (colorMode == "badge") {
                        // Use the contrasting zone fill as a small badge; keep all text legible.
                        view.setInt(R.id.zone, "setBackgroundColor", zone.background)
                        view.setTextColor(R.id.zone, zone.foreground)
                    } else {
                        view.setInt(R.id.root, "setBackgroundColor", zone.background)
                        textIds.forEach { view.setTextColor(it, zone.foreground) }
                        view.setTextColor(R.id.zone, zone.foreground)
                    }
                }
                view.setViewVisibility(R.id.zone, if (zone != null) android.view.View.VISIBLE else android.view.View.GONE)
                view.setTextViewText(R.id.zone, zone?.let { "Z${it.number}" } ?: "")
                val iconColor = if (zone != null && colorMode == "background") zone.foreground else fg
                val iconResource = when {
                    isPower -> R.drawable.ic_power
                    stack.id == "heart" -> R.drawable.ic_heart
                    stack.id == "speed" -> R.drawable.ic_speed
                    stack.id == "distance" -> R.drawable.ic_distance
                    else -> R.drawable.ic_cadence
                }
                val icon = requireNotNull(context.getDrawable(iconResource)).mutate()
                icon.setTint(iconColor)
                val pixels = (22 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
                icon.setBounds(0, 0, pixels, pixels)
                icon.draw(Canvas(bitmap))
                view.setImageViewBitmap(R.id.icon, bitmap)
                view.setContentDescription(R.id.icon, stack.title)
                view.setTextViewText(R.id.unit, stack.unit)
                view.setTextViewText(R.id.main, format(mainValue, stack.main.format))
                val leftUnit = if (stack.id == "distance") " m" else ""
                view.setTextViewText(R.id.left, "${stack.leftLabel} ${format(rawValue(stack.left, 1), stack.left.format)}$leftUnit")
                val right = stack.right
                view.setViewVisibility(R.id.right, if (right == null) android.view.View.GONE else android.view.View.VISIBLE)
                view.setTextViewText(R.id.right, right?.let {
                    "${stack.rightLabel} ${format(rawValue(it, 2), it.format)}$leftUnit"
                } ?: "")
                if (stack.id == "cadence" && prefs.getBoolean("cadence_balance", false)) {
                    val timestamp = timestamps[balanceType]
                    val left = if (config.preview) 48.0 else if (timestamp != null &&
                        SystemClock.elapsedRealtime() - timestamp <= 5000) values[balanceType] else null
                    val balance = pedalBalance(left)
                    view.setTextViewText(R.id.left, "L ${balance?.first?.let { "$it%" } ?: "--"}")
                    view.setTextViewText(R.id.right, "R ${balance?.second?.let { "$it%" } ?: "--"}")
                    view.setViewVisibility(R.id.right, android.view.View.VISIBLE)
                }
                emitter.updateView(view)
                delay(1100) // Karoo permits at most one RemoteViews update per second.
            }
        }
        emitter.setCancellable {
            scope.cancel()
            ids.forEach(system::removeConsumer)
            profileId?.let(system::removeConsumer)
            balanceId?.let(system::removeConsumer)
        }
    }

    private fun demo(metric: Metric, index: Int): Double = when(metric.format) {
        Format.SPEED -> listOf(8.7, 7.8, 15.1)[index]
        Format.DISTANCE -> 82400.0
        Format.TIME -> 10800.0
        Format.INTEGER -> if (stack.id == "distance") (if (index == 1) 1260.0 else 1180.0)
            else if (stack.id == "heart") listOf(142.0, 136.0, 174.0)[index]
            else if (stack.id == "cadence") listOf(88.0, 83.0, 112.0)[index]
            else listOf(243.0, 218.0, 232.0)[index]
    }
}

private fun format(value: Double?, format: Format): String {
    if (value == null || !value.isFinite() || value < 0) return "--"
    return when(format) {
        Format.INTEGER -> String.format(Locale.getDefault(), "%.0f", value)
        Format.SPEED -> String.format(Locale.getDefault(), "%.1f", value * 3.6)
        Format.DISTANCE -> String.format(Locale.getDefault(), "%.1f", value / 1000)
        Format.TIME -> value.toLong().let { String.format(Locale.ROOT, "%d:%02d:%02d", it / 3600, it / 60 % 60, it % 60) }
    }
}
