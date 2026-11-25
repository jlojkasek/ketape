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
private data class Stack(val id: String, val titleRes: Int, val unit: String,
    val main: Metric, val left: Metric, val right: Metric?,
    val leftLabel: String = "Ø", val rightLabel: String = "MAX")

private val stacks = listOf(
    Stack("power-1s", R.string.field_0, "W", Metric(DataType.Type.POWER), Metric(DataType.Type.AVERAGE_POWER), Metric(DataType.Type.NORMALIZED_POWER), rightLabel = "NP"),
    Stack("power-3s", R.string.field_1, "W", Metric(DataType.Type.SMOOTHED_3S_AVERAGE_POWER), Metric(DataType.Type.AVERAGE_POWER), Metric(DataType.Type.NORMALIZED_POWER), rightLabel = "NP"),
    Stack("power-5s", R.string.field_2, "W", Metric(DataType.Type.SMOOTHED_5S_AVERAGE_POWER), Metric(DataType.Type.AVERAGE_POWER), Metric(DataType.Type.NORMALIZED_POWER), rightLabel = "NP"),
    Stack("heart", R.string.field_3, "bpm", Metric(DataType.Type.HEART_RATE), Metric(DataType.Type.AVERAGE_HR), Metric(DataType.Type.MAX_HR)),
    Stack("speed", R.string.field_4, "km/h", Metric(DataType.Type.SPEED, Format.SPEED), Metric(DataType.Type.AVERAGE_SPEED, Format.SPEED), Metric(DataType.Type.MAX_SPEED, Format.SPEED)),
    Stack("distance", R.string.field_5, "km", Metric(DataType.Type.DISTANCE, Format.DISTANCE), Metric(DataType.Type.ELEVATION_GAIN), Metric(DataType.Type.ELEVATION_LOSS), "↗", "↘"),
    Stack("cadence", R.string.field_6, "rpm", Metric(DataType.Type.CADENCE), Metric(DataType.Type.AVERAGE_CADENCE), Metric(DataType.Type.MAX_CADENCE))
)

class StackedExtension : KarooExtension("stacked-fields", BuildConfig.VERSION_NAME) {
    private val system by lazy { KarooSystemService(this) }
    private val recordMonitor by lazy { RideRecordMonitor(this, system) }
    override val types: List<DataTypeImpl> by lazy { stacks.map { StackedType(system, it, recordMonitor) } +
            BestComparisonType(recordMonitor) +
            (listOf(null) + powerDurations).map { PowerBestType(recordMonitor, it) } }

    override fun onCreate() {
        super.onCreate()
        system.connect()
        recordMonitor.start()
    }

    override fun onDestroy() {
        recordMonitor.stop()
        system.disconnect()
        super.onDestroy()
    }
}

private class StackedType(
    private val system: KarooSystemService,
    private val stack: Stack,
    private val recordMonitor: RideRecordMonitor
) :
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
        val backgroundDrawable = if (config.boundariesEnabled) {
            R.drawable.field_background_rounded
        } else {
            android.R.color.white
        }
        scope.launch {
            // Load once per view activation, not in the one-second render loop.
            val systemColors = KarooPalette.load(context)
            var previewTick = 0
            while (isActive) {
                val previewFrame = PreviewValues.frame(stack.id, previewTick)
                val theme = prefs.getString("theme", "auto")
                val dark = theme == "dark" || (theme == "auto" &&
                    context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
                val fg = if (dark) Color.WHITE else Color.rgb(17,17,17)
                val bg = if (dark) Color.BLACK else Color.WHITE
                val view = RemoteViews(context.packageName, R.layout.stacked)
                val gravity = when (config.alignment) {
                    ViewConfig.Alignment.LEFT -> android.view.Gravity.LEFT
                    ViewConfig.Alignment.RIGHT -> android.view.Gravity.RIGHT
                    else -> android.view.Gravity.CENTER_HORIZONTAL
                } or android.view.Gravity.CENTER_VERTICAL
                view.setInt(R.id.main_row, "setGravity", gravity)
                // Align the complete group, never the number independently from its unit.
                listOf(R.id.main, R.id.unit).forEach {
                    view.setInt(it, "setGravity", android.view.Gravity.CENTER)
                }
                // The compact main group is measured below; footer cells keep native autosizing.
                fun rawValue(metric: Metric, index: Int): Double? =
                    if (config.preview) when (index) {
                        0 -> previewFrame.main
                        1 -> previewFrame.average
                        else -> previewFrame.secondary
                    } else {
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
                val backgroundZone = zone?.takeIf { colorMode == "background" }
                val badgeZone = zone?.takeIf { colorMode == "badge" }
                val showBalance = stack.id == "cadence" && prefs.getBoolean("cadence_balance", false)
                val balance = if (showBalance) {
                    val timestamp = timestamps[balanceType]
                    val left = if (config.preview) previewFrame.balanceLeft else if (timestamp != null &&
                        SystemClock.elapsedRealtime() - timestamp <= 5000) values[balanceType] else null
                    pedalBalance(left)
                } else null
                val balanceWarning = exceedsBalanceThreshold(balance, prefs.getInt(BALANCE_WARNING_KEY, 0))
                val contentColor = if (balanceWarning) Color.WHITE else backgroundZone?.foreground ?: fg
                val fieldColor = if (balanceWarning) Color.rgb(185, 28, 28) else backgroundZone?.background ?: bg
                view.setImageViewResource(R.id.field_background, backgroundDrawable)
                view.setInt(R.id.field_background, "setColorFilter", fieldColor)
                listOf(R.id.main, R.id.unit, R.id.left, R.id.right).forEach {
                    view.setTextColor(it, contentColor)
                }
                view.setInt(R.id.zone, "setBackgroundColor", badgeZone?.background ?: Color.TRANSPARENT)
                view.setTextColor(R.id.zone, badgeZone?.foreground ?: fg)
                view.setViewVisibility(R.id.zone, if (badgeZone != null) android.view.View.VISIBLE else android.view.View.GONE)
                view.setTextViewText(R.id.zone, badgeZone?.let { "Z${it.number}" } ?: "")
                val iconResource = when {
                    isPower -> R.drawable.ic_power
                    stack.id == "heart" -> R.drawable.ic_heart
                    stack.id == "speed" -> R.drawable.ic_speed
                    stack.id == "distance" -> R.drawable.ic_distance
                    else -> R.drawable.ic_cadence
                }
                val icon = requireNotNull(context.getDrawable(iconResource)).mutate()
                icon.setTint(contentColor)
                val pixels = (22 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
                icon.setBounds(0, 0, pixels, pixels)
                icon.draw(Canvas(bitmap))
                view.setImageViewBitmap(R.id.icon, bitmap)
                view.setContentDescription(R.id.icon, context.getString(stack.titleRes))
                view.setTextViewText(R.id.unit, stack.unit)
                val mainText = format(mainValue, stack.main.format)
                view.setTextViewText(R.id.main, mainText)
                MainLineLayout.apply(context, view, config.viewSize.first, config.viewSize.second,
                    config.textSize, mainText, stack.unit)
                val leftUnit = if (stack.id == "distance") " m" else ""
                view.setTextViewText(R.id.left, "${stack.leftLabel} ${format(rawValue(stack.left, 1), stack.left.format)}$leftUnit")
                val right = stack.right
                view.setViewVisibility(R.id.right, if (right == null) android.view.View.GONE else android.view.View.VISIBLE)
                view.setTextViewText(R.id.right, right?.let {
                    "${stack.rightLabel} ${format(rawValue(it, 2), it.format)}$leftUnit"
                } ?: "")
                if (showBalance) {
                    view.setTextViewText(R.id.left, "L ${balance?.first?.let { "$it%" } ?: "--"}")
                    view.setTextViewText(R.id.right, "R ${balance?.second?.let { "$it%" } ?: "--"}")
                    view.setViewVisibility(R.id.right, android.view.View.VISIBLE)
                }
                val record = when {
                    config.preview -> PreviewValues.record(stack.id, previewTick)
                    supportsZones -> recordMonitor.current(isPower)
                    else -> null
                }
                if (record != null) {
                    val recordValue = record.value.toString()
                    view.setTextViewText(R.id.main, recordValue)
                    MainLineLayout.apply(context, view, config.viewSize.first, config.viewSize.second,
                        config.textSize, recordValue, stack.unit)
                    view.setViewVisibility(R.id.zone, android.view.View.GONE)
                    view.setViewVisibility(R.id.right, android.view.View.GONE)
                    val message = when (record.duration) {
                        HEART_RECORD -> context.getString(R.string.record_heart_notice)
                        1, 5 -> context.getString(R.string.record_seconds_notice, record.duration)
                        else -> context.getString(R.string.record_minutes_notice, record.duration / 60)
                    }
                    view.setTextViewText(R.id.left, message)
                }
                emitter.updateView(view)
                if (config.preview) previewTick++
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
